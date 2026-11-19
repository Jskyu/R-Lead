# R-Lead 도커 실행 스크립트 (PowerShell)

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "R-Lead 도커 실행 스크립트" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/4] 기존 컨테이너 중지 및 삭제..." -ForegroundColor Yellow
docker compose down

Write-Host ""
Write-Host "[2/4] 도커 이미지 빌드 및 컨테이너 시작..." -ForegroundColor Yellow
docker compose up -d --build

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ 도커 실행 실패!" -ForegroundColor Red
    Read-Host "계속하려면 Enter를 누르세요"
    exit 1
}

Write-Host ""
Write-Host "[3/4] 컨테이너 상태 확인..." -ForegroundColor Yellow
docker compose ps

Write-Host ""
Write-Host "[4/4] 서비스 준비 중... (약 30초 소요)" -ForegroundColor Yellow
Start-Sleep -Seconds 30

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "✅ R-Lead 실행 완료!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "📱 Frontend:        http://localhost:3000" -ForegroundColor Cyan
Write-Host "🔧 Backend API:     http://localhost:8000/api/docs" -ForegroundColor Cyan
Write-Host "📦 MinIO Console:   http://localhost:9001" -ForegroundColor Cyan
Write-Host "   (ID: minioadmin / PW: minioadmin)" -ForegroundColor Gray
Write-Host ""
Write-Host "로그 확인: docker compose logs -f" -ForegroundColor Gray
Write-Host "중지하기: docker compose down" -ForegroundColor Gray
Write-Host "============================================" -ForegroundColor Green
Write-Host ""

$choice = Read-Host "로그를 확인하시겠습니까? (Y/N)"
if ($choice -eq "Y" -or $choice -eq "y") {
    docker compose logs -f
}

Read-Host "계속하려면 Enter를 누르세요"
