@echo off
cd /d "%~dp0"
echo ========================================================
echo       Import Data to Firebase Local Emulator
echo ========================================================
echo.
echo Make sure start-emulator.bat is running before running this!
echo.
set FIRESTORE_EMULATOR_HOST=127.0.0.1:8080
node scripts\import-firestore.mjs
echo.
echo Done! Data imported into the local emulator.
pause
