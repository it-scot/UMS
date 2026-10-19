@echo off
cd /d "%~dp0"
echo ========================================================
echo       Starting UMS (Backend + Frontend + Real Data)
echo ========================================================
echo.

echo 1. Freeing any busy ports (8081, 5173, 8085, 4000)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8081" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8085" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":4000" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

if not exist "emulator-data" mkdir "emulator-data"

echo 2. Launching Firebase Local Emulator (0 Quota, 96 Real Staff Preloaded)...
start "Firebase Emulator" cmd /k "cd /d ""%~dp0"" && firebase emulators:start --only firestore --import=./emulator-data --export-on-exit=./emulator-data"

echo 3. Waiting 6 seconds for emulator database to initialize...
ping 127.0.0.1 -n 7 >nul

echo 4. Starting Backend (Spring Boot on port 8081 connected to emulator)...
start "UMS Backend" cmd /k "cd /d ""%~dp0"" && if exist ""C:\tools\apache-maven-3.10.0\bin"" set ""PATH=C:\tools\apache-maven-3.10.0\bin;%%PATH%%"" && set UMS_DEMO_AUTH_ENABLED=true&& set SERVER_ADDRESS=127.0.0.1&& set FIRESTORE_EMULATOR_HOST=127.0.0.1:8085&& mvn -f backend\pom.xml spring-boot:run"

echo 5. Starting Frontend (React / Vite on port 5173)...
start "UMS Frontend" cmd /k "cd /d ""%~dp0"" && npm run dev --prefix frontend"

echo 6. Waiting for frontend to be ready...
ping 127.0.0.1 -n 5 >nul
start http://localhost:5173

echo.
echo ========================================================
echo All servers launched successfully!
echo - Web App:        http://localhost:5173
echo - Backend API:    http://localhost:8081
echo - Database UI:    http://localhost:4000
echo.
echo Login with: disitha@scot.lk (Disitha Ranasinghe)
echo Real staff loaded: 96 employees across 17 departments
echo ========================================================
