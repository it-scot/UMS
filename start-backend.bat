@echo off
cd /d "%~dp0"
echo Starting UMS Spring Boot Backend on port 8081...
set UMS_DEMO_AUTH_ENABLED=true
set SERVER_ADDRESS=127.0.0.1
mvn -f backend\pom.xml spring-boot:run
pause
