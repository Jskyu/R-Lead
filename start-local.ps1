# R-Lead 로컬 개발 모드 실행 스크립트 (PowerShell)
# 사용법:  .\start-local.ps1              # FastAPI 백엔드 (기본)
#          .\start-local.ps1 -Backend spring   # Spring Boot 백엔드
param(
    [ValidateSet("fastapi", "spring")]
    [string]$Backend = "fastapi"
)

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "R-Lead 로컬 개발 모드 ($Backend)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

$services = if ($Backend -eq "fastapi") { "postgres minio" } else { "minio" }
Write-Host "[1/4] 인프라($services)를 도커로 실행..." -ForegroundColor Yellow
docker compose up -d --wait $services.Split(" ")

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ 도커 실행 실패! Docker Desktop이 켜져 있는지 확인하세요." -ForegroundColor Red
    Read-Host "계속하려면 Enter를 누르세요"
    exit 1
}

$root = $PSScriptRoot

if ($Backend -eq "fastapi") {
    Write-Host ""
    Write-Host "[2/4] 데이터베이스 마이그레이션 실행..." -ForegroundColor Yellow
    Push-Location (Join-Path $root "backend")
    & .env\Scripts\python.exe -m alembic upgrade head
    if ($LASTEXITCODE -ne 0) {
        Write-Host "마이그레이션 실패. backendenv 가 만들어져 있는지 확인하세요. (QUICK_START.md)" -ForegroundColor Red
    }
    Pop-Location

    Write-Host ""
    Write-Host "[3/4] FastAPI 서버 시작 (새 창)..." -ForegroundColor Yellow
    $backendPath = Join-Path $root "backend"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendPath'; .env\Scripts\Activate.ps1; uvicorn app.main:app --reload --host 0.0.0.0 --port 8000"
    $apiDocs = "http://localhost:8000/api/docs"
    $viteType = "fastapi"
} else {
    Write-Host ""
    Write-Host "[2/4] (Spring Boot 는 SQLite 를 사용하므로 마이그레이션이 필요 없습니다)" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "[3/4] Spring Boot 서버 시작 (새 창)..." -ForegroundColor Yellow
    $backendPath = Join-Path $root "backend-spring"
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$backendPath'; if (-not (Test-Path .env)) { Copy-Item .env.example .env }; .\gradlew.bat bootRun"
    $apiDocs = "http://localhost:8080/swagger-ui.html"
    $viteType = "springboot"
}

Start-Sleep -Seconds 3

Write-Host ""
Write-Host "[4/4] 프론트엔드 서버 시작 (새 창)..." -ForegroundColor Yellow
$frontendPath = Join-Path $root "frontend"
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$frontendPath'; `$env:VITE_BACKEND_TYPE='$viteType'; npm run dev"

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "✅ R-Lead 로컬 개발 모드 실행 완료!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "📱 Frontend:        http://localhost:5173" -ForegroundColor Cyan
Write-Host "🔧 Backend 문서:    $apiDocs" -ForegroundColor Cyan
Write-Host "📦 MinIO Console:   http://localhost:9001" -ForegroundColor Cyan
Write-Host ""
Write-Host "💡 Backend와 Frontend는 별도 창에서 실행되며 코드 수정이 자동 반영됩니다." -ForegroundColor Yellow
Write-Host ""
Write-Host "중지하기: 각 창에서 Ctrl+C 후  docker compose stop postgres minio" -ForegroundColor Gray
Write-Host "============================================" -ForegroundColor Green
Write-Host ""

Read-Host "계속하려면 Enter를 누르세요"
