@echo off
echo ============================================
echo R-Lead Stop Script
echo ============================================
echo.

echo Stopping Docker containers...
docker compose down

echo.
echo All Docker containers stopped.
echo.
echo Note: Close Backend/Frontend windows manually if running in local mode.
echo.
pause
