@echo off
setlocal enabledelayedexpansion

echo ============================================
echo  MMDP Deployment Package Builder
echo ============================================
echo.

set "SCRIPT_DIR=%~dp0"

REM ---- Backend Build ----
echo [1/5] Building backend ^(mvn clean package -DskipTests^)...
cd /d "%SCRIPT_DIR%mmdp-backend"
call mvn clean package -DskipTests
if %errorlevel% neq 0 (
    echo [ERROR] Backend build failed!
    pause
    exit /b 1
)
echo        Backend build OK.

REM ---- Frontend Build ----
echo [2/5] Building frontend ^(npm run build^)...
cd /d "%SCRIPT_DIR%mmdp-frontend"
call npm run build
if %errorlevel% neq 0 (
    echo [ERROR] Frontend build failed!
    pause
    exit /b 1
)
echo        Frontend build OK.

REM ---- Prepare deploy directories ----
echo [3/5] Preparing deploy directories...
cd /d "%SCRIPT_DIR%"
if not exist "deploy\backend"   mkdir "deploy\backend"
if not exist "deploy\frontend"  mkdir "deploy\frontend"
if not exist "deploy\initdb"    mkdir "deploy\initdb"
if not exist "deploy\nginx"     mkdir "deploy\nginx"

REM ---- Copy artifacts ----
echo [4/5] Copying artifacts...

REM Backend JAR
REM Find the built JAR (handles versioned filenames like mmdp-backend-1.0.0-SNAPSHOT.jar)
for %%f in ("mmdp-backend\target\mmdp-backend-*.jar") do set "JAR_FILE=%%f"
if not defined JAR_FILE (
    echo [ERROR] No backend JAR found in mmdp-backend\target\! Expected mmdp-backend-*.jar
    pause
    exit /b 1
)
copy /y "!JAR_FILE!" "deploy\backend\mmdp-backend.jar" >nul
if %errorlevel% neq 0 (
    echo [ERROR] Failed to copy backend JAR!
    pause
    exit /b 1
)

REM Frontend dist (clear old first)
if exist "deploy\frontend\dist" rmdir /s /q "deploy\frontend\dist"
xcopy /e /q /i "mmdp-frontend\dist" "deploy\frontend\dist" >nul
if %errorlevel% neq 0 (
    echo [ERROR] Failed to copy frontend dist!
    pause
    exit /b 1
)

REM Schema SQL for MySQL auto-init
copy /y "mmdp-backend\src\main\resources\schema.sql" "deploy\initdb\schema.sql" >nul

echo [5/5] Done.
echo.
echo ============================================
echo  Deploy package ready at: %SCRIPT_DIR%deploy\
echo ============================================
echo.
echo Next steps:
echo   1. Edit deploy\.env and fill in real secrets.
echo   2. Upload the entire deploy\ folder to /data/mmdp on the server.
echo   3. On server: cd /data/mmdp ^&^& docker compose up -d --build
echo.
pause
