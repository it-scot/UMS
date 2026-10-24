@echo off
cd /d "%~dp0"
title Stop UMS Services
color 0C

echo ========================================================
echo             Stopping All UMS Services
echo ========================================================
echo.

echo Freeing Port 8081 (Backend)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8081" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

echo Freeing Port 5173 (Frontend)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

echo Freeing Port 8085 (Database Emulator)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8085" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

echo Freeing Port 4000 (Emulator UI)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":4000" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

echo.
echo ========================================================
echo         All UMS services have been stopped!
echo ========================================================
echo.
pause
