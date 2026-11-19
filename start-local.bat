@echo off
REM R-Lead local development mode
REM Usage: start-local.bat          (FastAPI backend, default)
REM        start-local.bat spring   (Spring Boot backend)
setlocal
set BACKEND=%1
if "%BACKEND%"=="" set BACKEND=fastapi

echo ============================================
echo R-Lead Local Development Mode (%BACKEND%)
echo ============================================
echo.

echo [1/4] Starting infrastructure with Docker...
if /I "%BACKEND%"=="spring" (
    docker compose up -d --wait minio
) else (
    docker compose up -d --wait postgres minio
)
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Docker startup failed! Is Docker Desktop running?
    pause
    exit /b 1
)

if /I "%BACKEND%"=="spring" goto spring

echo.
echo [2/4] Running database migrations...
pushd backend
venv\Scripts\python.exe -m alembic upgrade head
if %ERRORLEVEL% NEQ 0 echo WARNING: Migration failed. Check that backendenv exists (see QUICK_START.md).
popd

echo.
echo [3/4] Starting FastAPI server (new window)...
start "R-Lead Backend" cmd /k "cd /d %CD%ackend && venv\Scriptsctivate.bat && uvicorn app.main:app --reload --host 0.0.0.0 --port 8000"
set VITE_TYPE=fastapi
set API_DOCS=http://localhost:8000/api/docs
goto frontend

:spring
echo.
echo [2/4] (Spring Boot uses SQLite - no migration needed)
echo.
echo [3/4] Starting Spring Boot server (new window)...
if not exist backend-spring\.env copy backend-spring\.env.example backend-spring\.env
start "R-Lead Backend (Spring)" cmd /k "cd /d %CD%ackend-spring && gradlew.bat bootRun"
set VITE_TYPE=springboot
set API_DOCS=http://localhost:8080/swagger-ui.html

:frontend
timeout /t 3 > nul
echo.
echo [4/4] Starting Frontend server (new window)...
start "R-Lead Frontend" cmd /k "cd /d %CD%rontend && set VITE_BACKEND_TYPE=%VITE_TYPE%&& npm run dev"

echo.
echo ============================================
echo R-Lead Local Development Mode is running!
echo ============================================
echo.
echo Frontend:        http://localhost:5173
echo Backend docs:    %API_DOCS%
echo MinIO Console:   http://localhost:9001
echo.
echo Backend and Frontend run in separate windows (code changes reload automatically).
echo To stop: close those windows, then run: docker compose stop postgres minio
echo ============================================
echo.
pause
