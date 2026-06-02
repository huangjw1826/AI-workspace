"""
文件系统 API 模块 - Windows 原生文件夹选择器

使用 Shell.Application COM 对象弹出原生文件夹选择对话框。
此方式相比传统的 FolderBrowserDialog 的优点是：
即使调用进程没有可见窗口（如 -WindowStyle Hidden 启动），
Shell.Application 也能正常工作。

工作原理：
1. 前端请求 /api/pick-folder
2. 后端在临时文件中写入 PowerShell 脚本路径
3. PowerShell 使用 COM 对象弹出原生对话框
4. 用户选择后，路径写入临时结果文件
5. 后端读取结果返回给前端
"""

import asyncio
import logging
import os
import subprocess
import tempfile
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request

from app.api.auth import is_local_request

logger = logging.getLogger(__name__)

router = APIRouter(tags=["filesystem"])

# PowerShell 脚本：使用 Shell.Application COM 对象弹出原生文件夹浏览器对话框。
# 与 FolderBrowserDialog 不同，Shell.Application 不需要父窗口/表单，
# 因此即使从隐藏进程也能正常工作。
# 注意：使用 ASCII-safe 注释避免 PowerShell 5 解析多字节字符时的潜在问题
PICK_FOLDER_PS = r"""
$shell = New-Object -ComObject Shell.Application
$folder = $shell.BrowseForFolder(0, 'Select Folder', 0x00000010, 0)
if ($folder) {
    $item = $folder.Self
    Set-Content -Path $env:RESULT_FILE -Value $item.Path -Encoding UTF8
} else {
    Set-Content -Path $env:RESULT_FILE -Value '' -Encoding UTF8
}
"""


async def _run_powershell_dialog(result_path: str, timeout: float = 120.0) -> None:
    """在后台线程中运行 PowerShell COM 对话框。

    使用 asyncio.to_thread 将同步的 subprocess.run 放到线程池中执行，
    这样 FastAPI 事件循环不会阻塞，可以继续处理其他请求。

    Args:
        result_path: PowerShell 写入用户选择结果的临时文件路径
        timeout: 超时时间（秒），默认 120 秒
    """
    env = os.environ.copy()
    env["RESULT_FILE"] = result_path

    def _run() -> None:
        try:
            result = subprocess.run(
                [
                    "powershell",
                    "-NoProfile",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-Command",
                    PICK_FOLDER_PS,
                ],
                capture_output=True,
                text=True,
                timeout=timeout,
                env=env,
            )
            if result.returncode != 0:
                logger.warning(
                    "PowerShell folder picker exited with code %d: stderr=%s",
                    result.returncode,
                    result.stderr.strip(),
                )
        except subprocess.TimeoutExpired:
            logger.error("PowerShell folder picker timed out after %.1fs", timeout)
            raise
        except FileNotFoundError:
            logger.error("PowerShell executable not found in PATH")
            raise
        except Exception:
            logger.exception("Unexpected error running PowerShell folder picker")
            raise

    await asyncio.to_thread(_run)


@router.post("/api/pick-folder")
async def pick_folder(request: Request) -> dict[str, object]:
    """弹出 Windows 原生文件夹选择对话框并返回用户选择的路径。

    安全限制：仅限本地请求（is_local_request），防止远程用户触发
    本地对话框。用户取消选择时返回 cancelled=True。

    Args:
        request: FastAPI 请求对象（用于判断本地/远程）

    Returns:
        dict: {
            "path": str - 选择的文件夹路径，取消或出错时为空字符串
            "cancelled": bool - 用户是否取消选择
            "error": bool - 是否发生错误
        }
    """
    if not is_local_request(request):
        raise HTTPException(
            status_code=403,
            detail="文件夹选择器仅限本地使用",
        )

    fd, result_path = tempfile.mkstemp(suffix=".txt", prefix="ai_recorder_pick_")
    os.close(fd)

    timeout_seconds = 120.0
    try:
        # 异步运行 PowerShell 对话框，不阻塞事件循环
        try:
            await asyncio.wait_for(
                _run_powershell_dialog(result_path, timeout=timeout_seconds),
                timeout=timeout_seconds,
            )
        except asyncio.TimeoutError:
            return {"path": "", "cancelled": True}
        except Exception:
            return {"path": "", "error": True}

        # 读取 PowerShell 写入的结果文件
        path = ""
        try:
            with open(result_path, "r", encoding="utf-8-sig") as f:
                path = f.read().strip()
        except Exception:
            pass

        if path and Path(path).is_dir():
            return {"path": path}
        return {"path": "", "cancelled": not path}
    except Exception:
        return {"path": "", "error": True}
    finally:
        try:
            os.unlink(result_path)
        except Exception:
            pass
