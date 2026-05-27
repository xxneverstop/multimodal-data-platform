@echo off
cls

echo Starting MinIO...

docker run -d ^
--name mmdp-minio ^
--restart unless-stopped ^
-p 29010:9000 ^
-p 29011:9001 ^
-v mmdp-minio-data:/data ^
-e MINIO_ROOT_USER=admin ^
-e MINIO_ROOT_PASSWORD=123123123 ^
minio/minio server /data --console-address ":9001"

echo.
echo ==============================================
echo MinIO container started successfully!
echo.
echo Web Console:
echo http://localhost:29011
echo.
echo API Address:
echo http://localhost:29010
echo.
echo USER: admin
echo PWD : 123123123
echo ==============================================
echo.

pause