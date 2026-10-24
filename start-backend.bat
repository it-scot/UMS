@echo off
cd /d "%~dp0"
title UMS Spring Boot Backend
echo ========================================================
echo        Starting UMS Backend on port 8081
echo ========================================================
if exist "C:\tools\apache-maven-3.10.0\bin" set "PATH=C:\tools\apache-maven-3.10.0\bin;%PATH%"
set UMS_DEMO_AUTH_ENABLED=true
set SERVER_ADDRESS=127.0.0.1
if "%FIRESTORE_EMULATOR_HOST%"=="" set FIRESTORE_EMULATOR_HOST=127.0.0.1:8085

echo [INFO] Database: Firestore Emulator at %FIRESTORE_EMULATOR_HOST%
echo [INFO] Starting Spring Boot (port 8081)...
mvn -f backend\pom.xml spring-boot:run
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Backend exited with error code %ERRORLEVEL%.
    pause
)
