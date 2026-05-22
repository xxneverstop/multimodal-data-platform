@echo off
cls

:: MinIO Docker Startup Script for Windows
:: Port 9000: API port for file upload/download
:: Port 9001: Web console for management
:: Volume C:/minio/data: Local storage for all files

docker run -d ^
-p 29010:9000 ^
-p 29011:9001 ^
-v D:/data/minio:/data ^
-e MINIO_ROOT_USER=admin ^
-e MINIO_ROOT_PASSWORD=123123123 ^
--name mmdp-minio ^
minio/minio server /data --console-address ":9001"

echo.
echo ==============================================
echo MinIO container started successfully!
echo Web Console: http://localhost:29011
echo API Address: http://localhost:29010
echo USER: admin
echo PWD: 123123123
echo ==============================================
echo.
pause
