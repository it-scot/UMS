@echo off
cd /d "%~dp0"
echo ========================================================
echo       Seed Sample Data to Firebase Local Emulator
echo ========================================================
echo.
set FIRESTORE_EMULATOR_HOST=127.0.0.1:8085
node scripts\seed-emulator.mjs
echo.
pause
