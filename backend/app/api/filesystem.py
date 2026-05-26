"""Native Windows folder picker support.

Uses the Shell.Application COM object to show a native folder picker
dialog. This approach works even when the calling process has no
visible window (e.g. started with -WindowStyle Hidden).
"""

import os
import subprocess
import tempfile
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request

from app.api.auth import is_local_request

router = APIRouter(tags=["filesystem"])

# PowerShell script that uses Shell.Application COM to show a folder
# browser dialog. Unlike FolderBrowserDialog, Shell.Application
# does NOT require a parent form/window, so it works from hidden processes.
PICK_FOLDER_PS = r"""
$shell = New-Object -ComObject Shell.Application
$folder = $shell.BrowseForFolder(0, '选择文件夹', 0x00000010, 0)
if ($folder) {
    $item = $folder.Self
    Set-Content -Path $env:RESULT_FILE -Value $item.Path -Encoding UTF8
} else {
    Set-Content -Path $env:RESULT_FILE -Value '' -Encoding UTF8
}
"""


@router.post("/api/pick-folder")
async def pick_folder(request: Request) -> dict[str, object]:
    """Show a native Windows folder picker dialog and return the selected path."""
    if not is_local_request(request):
        raise HTTPException(status_code=403, detail="Folder picker is only available on the local PC.")

    fd, result_path = tempfile.mkstemp(suffix=".txt", prefix="ai_recorder_pick_")
    os.close(fd)

    try:
        env = os.environ.copy()
        env["RESULT_FILE"] = result_path

        proc = subprocess.run(
            [
                "powershell",
                "-NoProfile",
                "-Command",
                PICK_FOLDER_PS,
            ],
            capture_output=True,
            text=True,
            timeout=120,  # 2 min for user interaction
            env=env,
        )

        # Read the result from the temp file
        # PowerShell Set-Content -Encoding UTF8 writes a BOM, so strip it
        path = ""
        try:
            with open(result_path, "r", encoding="utf-8-sig") as f:
                path = f.read().strip()
        except Exception:
            pass

        if path and Path(path).is_dir():
            return {"path": path}
        return {"path": ""}
    except subprocess.TimeoutExpired:
        return {"path": ""}
    except Exception:
        return {"path": ""}
    finally:
        try:
            os.unlink(result_path)
        except Exception:
            pass
