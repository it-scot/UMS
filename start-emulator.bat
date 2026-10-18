@echo off
cd /d "%~dp0"
echo ========================================================
echo       Starting Firebase Local Firestore Emulator
echo ========================================================
echo.
echo Firestore Endpoint: 127.0.0.1:8080
echo Emulator UI:        http://localhost:4000
echo.
echo [INFO] Zero cloud quota used - 100%% free unlimited local reads/writes.
echo [INFO] Data is automatically preserved in ./emulator-data on exit.
echo.
if not exist "emulator-data" mkdir "emulator-data"
firebase emulators:start --only firestore --import=./emulator-data --export-on-exit=./emulator-data
pause
