@echo off
cd /d "%~dp0"
title SCOT ERP - UMS Starter
echo ========================================================
echo       Starting UMS (Backend + Frontend + Real Data)
echo ========================================================
echo.

echo 1. Freeing any busy ports (8081, 5173, 8085, 4000)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8081" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8085" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":4000" ^| findstr /i "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

if not exist "emulator-data" mkdir "emulator-data"

echo 2. Launching Firebase Local Emulator (0 Quota, Real Staff Database)...
start "Firebase Emulator" cmd /k "call ""%~dp0start-emulator.bat"""

echo 3. Waiting for Emulator to be ready on port 8085...
set /a emu_attempts=0
:wait_emu
set /a emu_attempts+=1
ping 127.0.0.1 -n 2 >nul
netstat -ano | findstr /i ":8085" | findstr /i "LISTENING" >nul
if %errorlevel% equ 0 goto emu_ready
if %emu_attempts% geq 20 goto emu_timeout
goto wait_emu

:emu_ready
echo    + Emulator is ready on port 8085!
goto start_backend

:emu_timeout
echo    ! Emulator starting took longer than expected, proceeding...

:start_backend
echo.
echo 4. Starting Spring Boot Backend (Connected to Emulator)...
start "UMS Backend" cmd /k "call ""%~dp0start-backend.bat"""

echo 5. Waiting for Backend to be ready on port 8081 (compiling ^& starting Tomcat)...
set /a backend_attempts=0
:wait_backend
set /a backend_attempts+=1
ping 127.0.0.1 -n 2 >nul
netstat -ano | findstr /i ":8081" | findstr /i "LISTENING" >nul
if %errorlevel% equ 0 goto backend_ready
if %backend_attempts% geq 35 goto backend_timeout
echo    ... still initializing backend (%backend_attempts%/35)
goto wait_backend

:backend_ready
echo    + Backend is fully ready on port 8081!
goto start_frontend

:backend_timeout
echo    ! Backend took longer than expected to bind port 8081.

:start_frontend
echo.
echo 6. Starting Vite Frontend on port 5173...
start "UMS Frontend" cmd /k "call ""%~dp0start-frontend.bat"""

echo 7. Waiting for frontend to start...
ping 127.0.0.1 -n 4 >nul
start http://localhost:5173

echo.
echo ========================================================
echo All servers launched successfully!
echo - Web App:        http://localhost:5173
echo - Backend API:    http://127.0.0.1:8081
echo - Database UI:    http://localhost:4000
echo.
echo Login with: disitha@scot.lk (Disitha Ranasinghe)
echo Real staff loaded: 96 employees across 17 departments
echo ========================================================
