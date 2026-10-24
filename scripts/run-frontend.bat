@echo off
cd /d "%~dp0\.."
title 3. UMS Frontend (Port 5173)
echo ========================================================
echo      Starting Frontend Dev Server on port 5173
echo ========================================================
call npm run dev --prefix frontend

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Frontend exited with error code %ERRORLEVEL%!
    pause
)
