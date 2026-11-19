# R-Lead 중지 스크립트 (PowerShell)

Write-Host "============================================" -ForegroundColor Cyan
Write-Host "R-Lead 중지 스크립트" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "도커 컨테이너 중지 중..." -ForegroundColor Yellow
docker compose down

Write-Host ""
Write-Host "✅ 모든 도커 컨테이너가 중지되었습니다." -ForegroundColor Green
Write-Host ""
Write-Host "💡 로컬 프로세스(Backend/Frontend)는 수동으로 창을 닫아주세요." -ForegroundColor Yellow
Write-Host ""

Read-Host "계속하려면 Enter를 누르세요"
