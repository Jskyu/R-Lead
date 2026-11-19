@echo off
echo ============================================
echo R-Lead Docker Startup Script
echo ============================================
echo.

echo [1/4] Stopping existing containers...
docker compose down

echo.
echo [2/4] Building and starting containers...
docker compose up -d --build

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Docker startup failed!
    pause
    exit /b 1
)

echo.
echo [3/4] Checking container status...
docker compose ps

echo.
echo [4/4] Waiting for services to be ready... (30 seconds)
timeout /t 30 /nobreak > nul

echo.
echo ============================================
echo R-Lead is now running!
echo ============================================
echo.
echo Frontend:        http://localhost:3000
echo Backend API:     http://localhost:8000/api/docs
echo MinIO Console:   http://localhost:9001
echo    (ID: minioadmin / PW: minioadmin)
echo.
echo View logs: docker compose logs -f
echo Stop:      docker compose down
echo ============================================
echo.

echo View logs now? (Y/N)
choice /c YN /n /m "Select: "
if %ERRORLEVEL% EQU 1 (
    docker compose logs -f
)

pause
