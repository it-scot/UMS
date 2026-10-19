@echo off
cd /d "%~dp0"
echo ========================================================
echo   Starting UMS Backend ^& Frontend (Direct Cloud Mode)
echo ========================================================
echo.
echo NOTE: This mode connects directly to Google Cloud Firestore.
echo If your Google Cloud project has exceeded its daily free quota,
echo reads will fail with RESOURCE_EXHAUSTED until quota resets.
echo.

echo Freeing ports 8081 and 5173...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8081" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

echo Starting Backend (Spring Boot on port 8081)...
start "UMS Backend (Cloud)" cmd /k "cd /d ""%~dp0"" && if exist ""C:\tools\apache-maven-3.10.0\bin"" set ""PATH=C:\tools\apache-maven-3.10.0\bin;%%PATH%%"" && set UMS_DEMO_AUTH_ENABLED=true&& set SERVER_ADDRESS=127.0.0.1&& mvn -f backend\pom.xml spring-boot:run"

echo Starting Frontend (React / Vite on port 5173)...
start "UMS Frontend" cmd /k "cd /d ""%~dp0"" && npm run dev --prefix frontend"

echo Waiting 5 seconds...
ping 127.0.0.1 -n 6 >nul
start http://localhost:5173

echo.
echo ========================================================
echo Services started in Cloud Mode:
echo - Frontend: http://localhost:5173
echo - Backend:  http://localhost:8081
echo ========================================================
