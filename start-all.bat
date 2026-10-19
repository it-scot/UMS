@echo off
cd /d "%~dp0"
echo ========================================================
echo       Starting UMS Backend ^& Frontend Servers
echo ========================================================
echo.
echo Starting Backend (Spring Boot on port 8081)...
start "UMS Backend" cmd /k "cd /d ""%~dp0"" && if exist ""C:\tools\apache-maven-3.10.0\bin"" set ""PATH=C:\tools\apache-maven-3.10.0\bin;%%PATH%%"" && set UMS_DEMO_AUTH_ENABLED=true&& set SERVER_ADDRESS=127.0.0.1&& mvn -f backend\pom.xml spring-boot:run"

echo Starting Frontend (React / Vite on port 5173)...
start "UMS Frontend" cmd /k "cd /d ""%~dp0"" && npm run dev --prefix frontend"

echo.
echo Both servers have been launched in separate windows!
echo - Frontend: http://localhost:5173
echo - Backend:  http://localhost:8081
echo ========================================================
