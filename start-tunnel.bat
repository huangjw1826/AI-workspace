@echo off
taskkill /F /T /IM cloudflared.exe 2>NUL
powershell -Command "Start-Process -WindowStyle Hidden -FilePath 'C:\Program Files (x86)\cloudflared\cloudflared.exe' -ArgumentList 'tunnel run' -WorkingDirectory 'C:\Users\13318\.cloudflared'"
