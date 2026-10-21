@echo off
title FinFlow Dev Launcher

echo [1/2] Starting backend (Spring Boot, port 8080) ...
if not exist "%~dp0finflow-backend\mvnw.cmd" (
    echo ERROR: finflow-backend\mvnw.cmd not found
    pause
    exit /b 1
)
rem Project requires JDK 25 (pom java.version=25). Global JAVA_HOME may point
rem to JDK 24, so pin JAVA_HOME to the JDK 25 downloaded by the IDE.
set "JAVA_HOME=%USERPROFILE%\.jdks\openjdk-25"
if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo ERROR: JDK 25 not found at "%JAVA_HOME%"
    pause
    exit /b 1
)
cd /d "%~dp0finflow-backend"
start "FinFlow-Backend (8080)" cmd /k mvnw.cmd spring-boot:run

echo [2/2] Starting frontend (Vite, port 5173) ...
if not exist "%~dp0finflow-frontend\package.json" (
    echo ERROR: finflow-frontend\package.json not found
    pause
    exit /b 1
)
cd /d "%~dp0finflow-frontend"
start "FinFlow-Frontend (5173)" cmd /k npm run dev

echo.
echo Done. Backend and frontend now run in the two new windows:
echo   - Backend : http://localhost:8080   (window: FinFlow-Backend)
echo   - Frontend: http://localhost:5173   (window: FinFlow-Frontend)
echo Close a service window to stop that service.
echo.
echo This launcher window can be closed now.
pause
