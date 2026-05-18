@echo off
cls

:: MinIO Docker Startup Script for Windows
:: Port 9000: API port for file upload/download
:: Port 9001: Web console for management
:: Volume C:/minio/data: Local storage for all files
:: Override credentials before running if needed:
::   set MMDP_MINIO_ROOT_USER=your-user
::   set MMDP_MINIO_ROOT_PASSWORD=your-password

if "%MMDP_MINIO_ROOT_USER%"=="" set MMDP_MINIO_ROOT_USER=minioadmin
if "%MMDP_MINIO_ROOT_PASSWORD%"=="" set MMDP_MINIO_ROOT_PASSWORD=change_me

docker run -d ^
-p 29010:9000 ^
-p 29011:9001 ^
-v D:/data/minio:/data ^
-e MINIO_ROOT_USER=%MMDP_MINIO_ROOT_USER% ^
-e MINIO_ROOT_PASSWORD=%MMDP_MINIO_ROOT_PASSWORD% ^
--name mmdp-minio ^
minio/minio server /data --console-address ":9001"

echo.
echo ==============================================
echo MinIO container started successfully!
echo Web Console: http://localhost:29011
echo API Address: http://localhost:29010
echo USER: %MMDP_MINIO_ROOT_USER%
echo PWD: %MMDP_MINIO_ROOT_PASSWORD%
echo ==============================================
echo.
pause
