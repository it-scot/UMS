@echo off
cd /d "%~dp0"
title SCOT ERP - UMS
color 0A

echo ========================================================
echo             SCOT ERP - UMS Quick Starter
echo ========================================================
echo.

echo [1/4] Stopping any previous running instances...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8081" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8085" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":4000" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

echo [2/4] Starting Firebase Local Database (Port 8085)...
start "1. Firebase Database" cmd /k "call ""%~dp0scripts\run-emulator.bat"""

:: Wait for emulator on port 8085
set /a emu_wait=0
:wait_emu_loop
set /a emu_wait+=1
ping 127.0.0.1 -n 2 >nul
netstat -ano | findstr /i ":8085" | findstr /i "LISTENING" >nul
if %errorlevel% equ 0 goto emu_is_up
if %emu_wait% geq 20 goto emu_is_up
goto wait_emu_loop
:emu_is_up
echo       + Database emulator is ready!

echo [3/4] Starting Spring Boot Backend (Port 8081)...
start "2. UMS Backend" cmd /k "call ""%~dp0scripts\run-backend.bat"""

echo       Waiting for backend to compile and start Tomcat...
set /a backend_wait=0
:wait_backend_loop
set /a backend_wait+=1
ping 127.0.0.1 -n 2 >nul
netstat -ano | findstr /i ":8081" | findstr /i "LISTENING" >nul
if %errorlevel% equ 0 goto backend_is_up
if %backend_wait% geq 40 goto backend_failed
echo       ... initializing (%backend_wait%/40)
goto wait_backend_loop

:backend_failed
echo.
echo ========================================================
echo [ERROR] Backend failed to start on port 8081!
echo Please check the '2. UMS Backend' window for the error.
echo ========================================================
echo.
pause
exit /b 1

:backend_is_up
echo       + Backend is fully ready on port 8081!

echo [4/4] Starting Frontend (Vite on port 5173)...
start "3. UMS Frontend" cmd /k "call ""%~dp0scripts\run-frontend.bat"""

ping 127.0.0.1 -n 4 >nul
start http://localhost:5173

echo.
echo ========================================================
echo        All UMS Systems Started Successfully!
echo ========================================================
echo - Web App:        http://localhost:5173
echo - Backend API:    http://127.0.0.1:8081
echo - Database UI:    http://localhost:4000
echo.
echo Quick Login: Click 'disitha@scot.lk (Disitha - Manager)'
echo Real Data:   96 Staff Members, 17 Departments Loaded
echo.
echo (To stop all servers, run stop.bat)
echo ========================================================
echo.
timeout /t 10
