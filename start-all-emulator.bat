@echo off
cd /d "%~dp0"
echo ========================================================
echo   Starting UMS with Firebase Local Emulator (0 Quota)
echo ========================================================
echo.
if not exist "emulator-data" mkdir "emulator-data"

echo 1. Launching Firebase Local Emulator...
start "Firebase Emulator" cmd /k "cd /d ""%~dp0"" && firebase emulators:start --only firestore --import=./emulator-data --export-on-exit=./emulator-data"

echo 2. Waiting 5 seconds for emulator to initialize...
timeout /t 5 /nobreak >nul

echo 3. Launching Spring Boot Backend (Connected to Emulator)...
start "UMS Backend" cmd /k "cd /d ""%~dp0"" && set UMS_DEMO_AUTH_ENABLED=true&& set SERVER_ADDRESS=127.0.0.1&& set FIRESTORE_EMULATOR_HOST=127.0.0.1:8085&& mvn -f backend\pom.xml spring-boot:run"

echo 4. Launching Vite Frontend...
start "UMS Frontend" cmd /k "cd /d ""%~dp0"" && npm run dev --prefix frontend"

echo.
echo ========================================================
echo All services started in separate windows:
echo - Frontend:        http://localhost:5173
echo - Backend API:     http://localhost:8081
echo - Emulator UI:     http://localhost:4000
echo - Cloud Firebase:  0 reads/writes (Unlimited local usage)
echo ========================================================
