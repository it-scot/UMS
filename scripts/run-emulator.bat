@echo off
cd /d "%~dp0\.."
title 1. Firebase Local Database (Port 8085)
echo ========================================================
echo   Starting Firebase Emulator (Port 8085, UI 4000)
echo ========================================================
if not exist "emulator-data" mkdir "emulator-data"
call firebase emulators:start --only firestore --import=./emulator-data --export-on-exit=./emulator-data

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Firebase emulator exited with error code %ERRORLEVEL%!
    pause
)
