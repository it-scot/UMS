@echo off
cd /d "%~dp0"
echo ========================================================
echo   Starting UMS with Firebase Local Emulator (0 Quota)
echo ========================================================
echo.

echo 1. Freeing any busy ports (8081, 5173, 8085, 4000)...
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8081" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":5173" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":8085" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":4000" ^| findstr "LISTENING"') do taskkill /F /PID %%a >nul 2>&1

if not exist "emulator-data" mkdir "emulator-data"

echo 2. Launching Firebase Local Emulator...
start "Firebase Emulator" cmd /k "cd /d ""%~dp0"" && firebase emulators:start --only firestore --import=./emulator-data --export-on-exit=./emulator-data"

echo 3. Waiting 6 seconds for emulator to initialize...
ping 127.0.0.1 -n 7 >nul

echo 4. Launching Spring Boot Backend (Connected to Emulator)...
start "UMS Backend" cmd /k "cd /d ""%~dp0"" && if exist ""C:\tools\apache-maven-3.10.0\bin"" set ""PATH=C:\tools\apache-maven-3.10.0\bin;%%PATH%%"" && set UMS_DEMO_AUTH_ENABLED=true&& set SERVER_ADDRESS=127.0.0.1&& set FIRESTORE_EMULATOR_HOST=127.0.0.1:8085&& mvn -f backend\pom.xml spring-boot:run"

echo 5. Launching Vite Frontend...
start "UMS Frontend" cmd /k "cd /d ""%~dp0"" && npm run dev --prefix frontend"

echo 6. Waiting for frontend to start...
ping 127.0.0.1 -n 5 >nul
start http://localhost:5173

echo.
echo ========================================================
echo All services started in separate windows:
echo - Frontend:        http://localhost:5173
echo - Backend API:     http://localhost:8081
echo - Emulator UI:     http://localhost:4000
echo - Cloud Firebase:  0 reads/writes (Unlimited local usage)
echo ========================================================
