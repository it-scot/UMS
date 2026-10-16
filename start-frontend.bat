@echo off
cd /d "%~dp0"
echo Starting UMS React Frontend on port 5173...
npm run dev --prefix frontend
pause
