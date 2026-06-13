@echo off
cd /d "%~dp0"

echo Starting Backend (Spring Boot)...
start "" cmd /k "powershell -ExecutionPolicy Bypass -File run_backend_with_env.ps1"

echo Starting Frontend (Vite)...
start "" cmd /k "cd ..\frontend && npm run dev"

echo.
echo ======================================================
echo Auto-Setup is running in the background windows.
echo It will download Maven automatically and start the backend.
echo ======================================================
pause
