@echo off
cd /d "%~dp0\.."
title 2. UMS Backend (Port 8081)
if exist "C:\tools\apache-maven-3.10.0\bin" set "PATH=C:\tools\apache-maven-3.10.0\bin;%PATH%"
set UMS_DEMO_AUTH_ENABLED=true
set SERVER_ADDRESS=127.0.0.1
set FIRESTORE_EMULATOR_HOST=127.0.0.1:8085

echo ========================================================
echo        Starting UMS Backend on port 8081
echo ========================================================
if exist "C:\tools\apache-maven-3.10.0\bin\mvn.cmd" (
    call "C:\tools\apache-maven-3.10.0\bin\mvn.cmd" -f backend\pom.xml spring-boot:run
) else (
    call mvn -f backend\pom.xml spring-boot:run
)

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Backend exited with error code %ERRORLEVEL%!
    pause
)
