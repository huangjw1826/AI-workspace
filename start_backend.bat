@echo off
powershell -Command "Start-Process -WindowStyle Hidden -FilePath '%~dp0.venv\Scripts\python.exe' -ArgumentList '-m uvicorn app.main:app --host 0.0.0.0 --port 8000' -WorkingDirectory '%~dp0backend'"
