"""Native Windows folder picker support.

Uses the Shell.Application COM object to show a native folder picker
dialog. This approach works even when the calling process has no
visible window (e.g. started with -WindowStyle Hidden).
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

# PowerShell script that uses Shell.Application COM to show a folder
# browser dialog. Unlike FolderBrowserDialog, Shell.Application
# does NOT require a parent form/window, so it works from hidden processes.
# 注释使用英文避免 PowerShell 5 解析多字节字符时的潜在问题
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
    """在后台线程中运行 PowerShell COM 对话框，避免阻塞事件循环。

    使用 asyncio.to_thread 将同步的 subprocess.run 放到线程池中执行，
    这样 FastAPI 事件循环可以继续处理其他请求。
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
    """Show a native Windows folder picker dialog and return the selected path."""
    if not is_local_request(request):
        raise HTTPException(
            status_code=403,
            detail="Folder picker is only available on the local PC.",
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
