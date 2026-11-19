#!/usr/bin/env pwsh

Write-Host "Starting R-Lead Spring Boot Backend..." -ForegroundColor Green
Write-Host ""

# Check if Gradle wrapper exists
if (-not (Test-Path gradlew.bat)) {
    Write-Host "Gradle wrapper not found. Please run: gradle wrapper" -ForegroundColor Red
    exit 1
}

# Check if .env file exists
if (-not (Test-Path .env)) {
    Write-Host ".env file not found. Creating from .env.example..." -ForegroundColor Yellow
    Copy-Item .env.example .env
    Write-Host ""
    Write-Host "Please edit .env file with your configuration." -ForegroundColor Yellow
    Read-Host "Press Enter to continue"
}

Write-Host "Building and starting Spring Boot application..." -ForegroundColor Cyan
.\gradlew.bat bootRun
