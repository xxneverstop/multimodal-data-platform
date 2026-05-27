@echo off

docker run -d ^
--name mmdp-mysql ^
-p 13306:3306 ^
-e MYSQL_ROOT_PASSWORD=123123 ^
-e MYSQL_ROOT_HOST=%% ^
-v mmdp-mysql-data:/var/lib/mysql ^
mysql:8.4.2

pause