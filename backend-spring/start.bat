@echo off
echo Starting R-Lead Spring Boot Backend...
echo.

REM Check if Gradle wrapper exists
if not exist gradlew.bat (
    echo Gradle wrapper not found. Please run: gradle wrapper
    pause
    exit /b 1
)

REM Check if .env file exists
if not exist .env (
    echo .env file not found. Creating from .env.example...
    copy .env.example .env
    echo.
    echo Please edit .env file with your configuration.
    pause
)

echo Building and starting Spring Boot application...
gradlew.bat bootRun

pause
