# MMDP CPU/GPU 生产部署及后续更新运维记录

> 记录日期：2026-08-04  
> 适用范围：MMDP CPU ECS、按量付费 GPU ECS、Docker Compose V2  
> 本文依据本次生产环境的实际部署输出整理，不记录任何真实密码、AccessKey 或证书私钥。

## 1. 最终部署状态

### 1.1 节点分工

| 节点 | 私网 IP | 部署目录 | 常驻服务 |
|---|---|---|---|
| CPU ECS | `172.31.133.185` | `/data/mmdp/deploy` | Nginx、Backend、MySQL、CPU Worker |
| GPU ECS | `172.31.133.186` | `/data/mmdp-gpu` | GPU Worker，实例按量付费、按需开机 |

CPU 与 GPU ECS 位于同一 VPC。GPU Worker 通过 CPU ECS 私网地址访问 Backend，处理文件通过同一个 OSS Bucket 中转，不使用 GPU ECS 公网 IP 进行业务通信。

### 1.2 已验证的服务状态

CPU ECS 正常状态：

```text
mmdp-mysql    Up ... (healthy)
mmdp-backend  Up ...
mmdp-worker   Up ...
mmdp-nginx    Up ...
```

GPU ECS 正常状态：

```text
mmdp-worker-gpu   mmdp-worker-gpu:latest   "python3 main.py"   Up
```

GPU Worker 正常启动日志：

```text
Worker 类型: GPU
后端: http://172.31.133.185
已注册 2 个 Pipeline:
  - G1_MERGE_CAMERA_ROBOT
  - MOTION_PHYSICS_METRICS_V2
[register] 已向 Backend 注册 2 个 Pipeline
[claim-diag] HTTP=200 ... No pending job for workerType=GPU
```

CPU 与 GPU Job 已按 `worker_type` 隔离：CPU Worker 不领取 GPU Job，GPU Worker 能领取并执行 `G1_MERGE_CAMERA_ROBOT`。

## 2. 部署前必须遵守的约定

1. 后续所有 Compose 命令统一使用 `docker compose`，不要再使用旧命令 `docker-compose`。
2. CPU ECS 的 `.env`、`mysql-data/` 是生产配置和生产数据，部署包不得包含或覆盖。
3. GPU ECS 的 `.env` 包含 OSS 凭证，部署包不得覆盖。
4. 不执行 `docker compose down -v`；不要删除 `/data/mmdp/deploy/mysql-data`。
5. 不随意执行 `docker system prune -a`、`docker builder prune` 或构建时添加 `--no-cache`，否则会丢失耗时很长的 CUDA、ZED SDK、PyTorch 构建缓存。
6. 更新前为当前镜像增加回滚标签，更新后先验收再清理旧镜像。
7. MySQL 的初始化环境变量只在空数据目录首次启动时生效。已有 `mysql-data/` 时，修改 `.env` 中密码不会自动修改数据库里的 root 密码。

## 3. 本地生成部署产物

开发机项目目录：

```text
D:\workspace\multimodal-data-platform
```

执行：

```powershell
cd D:\workspace\multimodal-data-platform
.\copy-deploy.bat
```

该脚本会：

1. 构建 Backend JAR；
2. 构建前端 `dist`；
3. 将 JAR、前端产物和 Worker 源码同步到 `deploy/`；
4. 排除 `.env`、IDE 文件、Windows wheel、缓存和测试临时文件；
5. 检查 `requirements-gpu.txt` 是否存在。

完成后检查：

```powershell
Test-Path deploy\backend\mmdp-backend.jar
Test-Path deploy\frontend\dist\index.html
Test-Path deploy\worker\src\requirements.txt
Test-Path deploy\worker\src\requirements-gpu.txt
Test-Path deploy\worker\src\.env
```

预期：前四项为 `True`，最后一项为 `False`。

### 3.1 必须先回收 GPU 服务器现场修复

本次首次部署中，GPU ECS 上的最终有效文件经过了现场修正。下一次从本地上传部署包前，必须保证仓库版本包含以下配置，否则覆盖服务器后问题会复发：

1. `Dockerfile.gpu` 使用阿里云 apt/PyPI 镜像；
2. ZED SDK 安装后通过 `/usr/local/zed/get_python_api.py` 安装 pyzed；
3. PyTorch 固定为 `torch==2.10.0`，索引为 `https://download.pytorch.org/whl/cu130`；
4. GPU requirements 使用 `requirements-gpu.txt`，不安装 `lerobot/evdev`；
5. `docker-compose.gpu.yml` 包含：

```yaml
environment:
  NVIDIA_DRIVER_CAPABILITIES: "all"
```

服务器最终文件可以先回传留档：

```powershell
scp root@<GPU公网IP>:/data/mmdp-gpu/worker/Dockerfile.gpu deploy/worker/Dockerfile.gpu.server-final
scp root@<GPU公网IP>:/data/mmdp-gpu/docker-compose.gpu.yml deploy/docker-compose.gpu.yml.server-final
```

对比确认后再合入正式文件。不要未经比较直接覆盖仓库中的用户改动。

### 3.2 生成并上传 CPU 部署包

Windows PowerShell：

```powershell
cd D:\workspace\multimodal-data-platform

tar -czf deploy\mmdp-cpu-deploy.tar.gz `
  -C deploy `
  docker-compose.yml `
  python3-10-slim.tar `
  backend frontend initdb nginx `
  worker/Dockerfile `
  worker/.dockerignore `
  worker/Dockerfile.dockerignore `
  worker/src

scp deploy\mmdp-cpu-deploy.tar.gz root@8.154.36.87:/tmp/
```

该包不包含 `.env`、`mysql-data/`、ZED SDK 和 GPU 模型。

CPU ECS 解压到既有正式目录：

```bash
tar -xzf /tmp/mmdp-cpu-deploy.tar.gz -C /data/mmdp/deploy
cd /data/mmdp/deploy
test -f .env
test -d mysql-data
docker compose config >/dev/null
```

### 3.3 生成并上传 GPU 首次部署包

Windows PowerShell：

```powershell
cd D:\workspace\multimodal-data-platform

tar -czf deploy\mmdp-gpu-deploy.tar.gz `
  -C deploy `
  docker-compose.gpu.yml `
  .env.gpu.example `
  worker/Dockerfile.gpu `
  worker/Dockerfile.gpu.dockerignore `
  worker/ZED_SDK_Ubuntu22_cuda13.0_tensorrt10.13_v5.4.1.zstd.run `
  worker/src `
  worker/smpl_models

scp deploy\mmdp-gpu-deploy.tar.gz root@<GPU当前公网IP>:/tmp/
```

GPU ECS：

```bash
mkdir -p /data/mmdp-gpu
tar -xzf /tmp/mmdp-gpu-deploy.tar.gz -C /data/mmdp-gpu
cd /data/mmdp-gpu

# 只在首次部署时创建，后续部署不得覆盖已有 .env
test -f .env || cp .env.gpu.example .env

test -f worker/Dockerfile.gpu
test -f worker/src/requirements-gpu.txt
test -f worker/smpl_models/neutral/model.npz
docker compose -f docker-compose.gpu.yml config >/dev/null
```

## 4. CPU ECS 首次部署和本次有效步骤

### 4.1 进入固定目录

```bash
cd /data/mmdp/deploy
pwd
test -f .env
test -d mysql-data
```

预期：

```text
/data/mmdp/deploy
```

### 4.2 升级到 Docker Compose V2

旧版 `docker-compose 1.29.2` 在新 Docker Engine 上重建容器时出现：

```text
KeyError: 'ContainerConfig'
```

有效处理步骤：

```bash
apt update
apt install -y docker-compose-v2
docker compose version
```

正常结果：

```text
Docker Compose version 2.40.3+ds1-0ubuntu1~22.04.1
```

检查配置：

```bash
cd /data/mmdp/deploy
docker compose config >/dev/null
```

无输出且退出码为 0 表示 Compose 配置有效。

### 4.3 处理旧 Compose 遗留的重命名容器

旧 Compose 重建失败后，可能残留以下形式的容器名：

```text
4bce80cf2a47_mmdp-mysql
a9bdd23c8670_mmdp-backend
```

先检查，不要直接删除：

```bash
docker ps -a --format 'table {{.ID}}\t{{.Names}}\t{{.Status}}\t{{.Image}}' | grep mmdp
```

MySQL 容器删除前必须确认状态和挂载：

```bash
docker inspect -f '{{.State.Status}}{{println}}{{range .Mounts}}{{println .Source "->" .Destination}}{{end}}' \
  <旧MySQL容器名>
```

本次确认的关键挂载为：

```text
/data/mmdp/deploy/mysql-data -> /var/lib/mysql
/data/mmdp/deploy/initdb -> /docker-entrypoint-initdb.d
```

只有在旧容器为 `exited` 且数据挂载确认正确后，才删除旧容器并由 Compose 重建：

```bash
docker rm <旧MySQL容器名>
docker compose up -d mmdp-mysql
docker compose ps
```

正常结果：

```text
mmdp-mysql ... Up ... (healthy)
```

Backend、Worker 如存在同类的已退出重命名容器，也按同样原则检查后删除。不要使用 `docker rm -v`。

### 4.4 MySQL 数据与密码校验

重建 MySQL 容器不会删除 bind mount 中的生产数据，但 `.env` 必须使用数据库历史上真实生效的密码。

交互式验证，避免把密码写进命令历史：

```bash
docker exec -it mmdp-mysql mysql -uroot -p mmdp_db
```

进入 MySQL 后执行：

```sql
SHOW COLUMNS FROM pipeline_definition LIKE 'worker_type';
```

本次正常结果：

```text
Field        Type         Null  Default
worker_type  varchar(16)  NO    CPU
```

如果 Backend 日志出现：

```text
Access denied for user 'root'@'172.18.0.x' (using password: YES)
```

说明 Backend 使用的 `.env` 密码与已有 MySQL 数据中的密码不一致。修正 `/data/mmdp/deploy/.env` 后只重建 Backend：

```bash
docker compose up -d --no-deps --force-recreate mmdp-backend
docker logs --tail 100 mmdp-backend
```

正常日志应包含：

```text
HikariPool-1 - Added connection
HikariPool-1 - Start completed
Tomcat started on port 19021
Started MmdpBackendApplication
```

### 4.5 数据库迁移

`deploy/initdb/` 只会在 MySQL 空数据目录首次初始化时自动执行。已有生产数据时，必须手动执行新增迁移。

执行前备份：

```bash
cd /data/mmdp/deploy
mkdir -p backup
docker exec mmdp-mysql sh -c \
  'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers mmdp_db' \
  > "backup/mmdp_db-$(date +%Y%m%d-%H%M%S).sql"
ls -lh backup/
```

执行 `worker_type` 迁移：

```bash
docker exec -it mmdp-mysql mysql -uroot -p mmdp_db
```

MySQL 客户端中执行：

```sql
source /docker-entrypoint-initdb.d/migration_add_worker_type.sql;
SELECT pipeline_id, worker_type, enabled
FROM pipeline_definition
ORDER BY worker_type, pipeline_id;
```

预期 `G1_MERGE_CAMERA_ROBOT`、`MOTION_PHYSICS_METRICS_V2` 为 `GPU`，其余为 `CPU`。

其他表结构或 Pipeline 定义变化，应使用对应的增量 SQL，不能用新版 `schema.sql` 覆盖现有数据库。

### 4.6 构建并启动 CPU 服务

如果服务器访问 Docker Hub 不稳定，先加载离线 Python 基础镜像：

```bash
cd /data/mmdp/deploy
docker load -i python3-10-slim.tar
docker tag python:3.10-slim python:3.10-slim-bookworm
docker image inspect python:3.10-slim-bookworm >/dev/null
```

首次部署或需要全量对齐时：

```bash
docker compose build mmdp-backend mmdp-worker
docker compose up -d --force-recreate
docker compose ps
```

MySQL 使用 `/data/mmdp/deploy/mysql-data:/var/lib/mysql`，因此容器重建不会删除数据。仍然禁止使用 `down -v` 或删除宿主机 `mysql-data/`。

### 4.7 CPU 服务验收

```bash
docker compose ps
docker logs --tail 100 mmdp-backend
docker logs --tail 100 mmdp-worker

curl -i http://127.0.0.1/api/auth/me
curl -I http://127.0.0.1/login
```

正常结果：

- `/api/auth/me` 返回 HTTP `401` 和“当前未登录”，表示 Nginx → Backend 链路正常；
- `/login` 返回 HTTP `200` 和 `text/html`，表示前端静态资源与 SPA 路由正常；
- CPU Worker 日志显示 Pipeline 注册成功，并持续轮询；
- Backend 日志显示收到 CPU Worker 注册。

公网 HTTP 验证：

```bash
curl -I http://8.154.36.87/login
```

## 5. HTTPS 状态与配置边界

当前仓库中的 Nginx 配置只包含 `listen 80`。Compose 中存在 `443:443` 端口映射并不等于已经启用 HTTPS。

检查生产容器的真实配置：

```bash
docker exec mmdp-nginx nginx -T 2>&1 | grep -nE 'listen .*443|ssl_certificate'
```

有输出且证书路径有效，才表示 HTTPS 已配置。正式启用 HTTPS 建议使用域名证书，并完成以下三项：

1. 将证书和私钥放在服务器受限目录，例如 `/data/mmdp/deploy/nginx/certs/`；
2. Compose 将该目录只读挂载到 `/etc/nginx/certs`；
3. Nginx 增加 `listen 443 ssl`、`ssl_certificate`、`ssl_certificate_key`，并将 80 重定向到 HTTPS。

修改后验证：

```bash
docker exec mmdp-nginx nginx -t
docker compose up -d --no-deps --force-recreate mmdp-nginx
curl -Ik https://<域名>/login
```

不要提交证书私钥。直接使用公网 IP 访问 HTTPS 时，证书必须明确包含该 IP，否则浏览器仍会报告证书名称不匹配。

## 6. GPU ECS 首次部署和本次有效步骤

### 6.1 目录与基础文件

最终目录：

```text
/data/mmdp-gpu/
├── docker-compose.gpu.yml
├── .env
├── cuda13-runtime.tar
└── worker/
    ├── Dockerfile.gpu
    ├── ZED_SDK_Ubuntu22_cuda13.0_tensorrt10.13_v5.4.1.zstd.run
    ├── src/
    └── smpl_models/
        ├── female/model.npz
        ├── male/model.npz
        └── neutral/model.npz
```

因为 Compose 文件名不是默认的 `compose.yml` 或 `docker-compose.yml`，所有命令都必须带：

```bash
-f docker-compose.gpu.yml
```

否则会出现：

```text
no configuration file provided: not found
```

### 6.2 GPU 和容器运行时检查

```bash
nvidia-smi
nvidia-ctk --version
docker info | grep -i runtime
```

加载已有 CUDA 13 基础镜像：

```bash
cd /data/mmdp-gpu
docker load -i cuda13-runtime.tar
docker image inspect nvidia/cuda:13.0.0-runtime-ubuntu22.04 >/dev/null
```

验证容器访问 GPU：

```bash
docker run --rm --gpus all \
  nvidia/cuda:13.0.0-runtime-ubuntu22.04 nvidia-smi
```

如 NVIDIA runtime 未配置：

```bash
nvidia-ctk runtime configure --runtime=docker
systemctl restart docker
```

### 6.3 GPU 环境变量

`/data/mmdp-gpu/.env` 至少包含：

```ini
MMDP_BACKEND_URL=http://172.31.133.185
MMDP_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
MMDP_OSS_ACCESS_KEY_ID=<实际值>
MMDP_OSS_ACCESS_KEY_SECRET=<实际值>
MMDP_OSS_BUCKET=<实际Bucket>
MMDP_OSS_REGION=cn-hangzhou
```

不得保留占位符，不要提交 `.env`。

验证 GPU ECS 到 CPU ECS：

```bash
curl -i http://172.31.133.185/api/auth/me
```

预期 HTTP `401 当前未登录`，这表示私网链路正常。

### 6.4 GPU Dockerfile 的最终有效构建顺序

最终有效顺序如下，顺序本身决定后续能否命中缓存：

1. CUDA 13 Runtime 基础镜像；
2. apt 切换阿里云镜像并安装 Python、ffmpeg、zstd、ZED 运行库及临时编译工具；
3. pip/setuptools/wheel 升级，并将 pip 默认索引设置为阿里云镜像；
4. 预装 ZED 安装过程所需的 NumPy；
5. COPY 并静默安装 ZED SDK，使用 `skip_cuda=true`；
6. 执行 `/usr/local/zed/get_python_api.py` 安装匹配当前 Python 的 pyzed；
7. 从 PyTorch 官方 CUDA 13 索引安装 `torch==2.10.0`；
8. 安装 `requirements-gpu.txt`；
9. 删除临时编译工具；
10. 最后 COPY `src/` 和 `smpl_models/`。

PyTorch CUDA 13 必须使用：

```text
https://download.pytorch.org/whl/cu130
```

普通 PyPI 国内镜像不能替代该 CUDA 专用索引。国内镜像用于普通 Python 依赖即可。

### 6.5 构建 GPU 镜像

```bash
cd /data/mmdp-gpu
docker compose -f docker-compose.gpu.yml config >/dev/null
docker compose -f docker-compose.gpu.yml build mmdp-worker-gpu
```

不要使用 `--no-cache`。

本次首次构建的实际正常结果：

```text
[+] Building 5421.5s (21/21) FINISHED
Image mmdp-worker-gpu:latest Built
```

其中主要耗时：

```text
安装 pyzed：约 118 秒
安装 torch + CUDA 依赖：约 4737 秒
安装 requirements-gpu：约 182 秒
导出和解包镜像：约 371 秒
```

最终镜像：

```text
mmdp-worker-gpu:latest
DISK USAGE: 约 22.1 GB
CONTENT SIZE: 约 8.59 GB
```

下载 PyTorch 时 BuildKit 可能长时间停留在某一个 `.whl`。只要宿主机网卡接收字节持续增加，就不是卡死。首次下载成功后，对应 Docker 层会被复用。

### 6.6 启用 ZED 所需的 NVIDIA 驱动能力

仅设置 Compose GPU reservation 时，容器默认通常只有 `compute,utility`，ZED 解码 SVO2 还需要 NVIDIA 视频驱动库。`docker-compose.gpu.yml` 必须包含：

```yaml
services:
  mmdp-worker-gpu:
    environment:
      NVIDIA_DRIVER_CAPABILITIES: "all"
```

这是本次以下错误的根因修复：

```text
ImportError: libnvcuvid.so.1: cannot open shared object file
```

错误消息虽然显示“pyzed 未安装”，但实际是 pyzed 已安装、导入时缺少宿主机驱动库挂载。

不启动正式 Worker即可验证：

```bash
docker run --rm --gpus all \
  -e NVIDIA_DRIVER_CAPABILITIES=all \
  --entrypoint bash \
  mmdp-worker-gpu:latest -lc \
  'ldconfig -p | grep libnvcuvid.so.1 && python3 -c "import pyzed.sl as sl; print(\"pyzed OK\")"'
```

预期：

```text
pyzed OK
```

### 6.7 启动并验收 GPU Worker

```bash
cd /data/mmdp-gpu
docker compose -f docker-compose.gpu.yml \
  up -d --no-build --force-recreate mmdp-worker-gpu

docker compose -f docker-compose.gpu.yml ps
docker logs --tail 100 mmdp-worker-gpu
```

容器内验收：

```bash
docker exec mmdp-worker-gpu nvidia-smi

docker exec mmdp-worker-gpu python3 -c \
  'import pyzed.sl as sl, torch; print("pyzed OK"); print(torch.__version__, torch.version.cuda, torch.cuda.is_available(), torch.cuda.get_device_name(0))'
```

预期：

```text
pyzed OK
torch.cuda.is_available() = True
GPU = Tesla T4
```

CPU ECS 的 Backend 日志应出现：

```text
GPU Worker 注册表已更新: 2 个 Pipeline
GPU Worker 注册 2 个 Pipeline
```

### 6.8 第一次执行 ZED Job

第一次执行 `G1_MERGE_CAMERA_ROBOT` 时可能看到：

```text
No calibration file was found ... Downloading the file
Calibration file successfully downloaded
Please wait while the AI model is being optimized for your graphics card
This operation will be run only once and may take a few minutes
```

这是正常的首次标定文件下载和 GPU 模型优化。此时不要重启容器，可另开 SSH 窗口观察：

```bash
watch -n 2 nvidia-smi
docker logs -f mmdp-worker-gpu
```

最终应看到 Job 下载、执行、上传并成功上报。日志跟踪时按 `Ctrl+C` 只会退出日志查看，不会停止容器。

## 7. 后续更新部署决策表

| 变更内容 | CPU ECS | GPU ECS | 是否重装依赖 |
|---|---|---|---|
| 仅前端代码 | 更新 `frontend/dist` | 无 | 否 |
| 仅 Backend Java 代码 | 重建并重建 Backend 容器 | 无 | 否 |
| 仅 CPU Pipeline/Python 源码 | 缓存构建 CPU Worker | 无 | 否 |
| 仅 GPU Pipeline/Python 源码 | 无 | 缓存构建 GPU Worker | 否 |
| Worker 公共源码 | 分别缓存构建两个 Worker | 分别更新 | 否 |
| `requirements.txt` | 重建 CPU Worker 依赖层 | 通常无 | 是 |
| `requirements-gpu.txt` | 通常无 | 重建 GPU 依赖层 | 是 |
| Dockerfile、CUDA、ZED SDK 版本 | 按影响节点重建 | 按影响节点重建 | 是 |
| `.env` 或 Compose | 重建对应容器 | 重建对应容器 | 否 |
| 数据库结构/Pipeline 元数据 | 备份并执行增量 SQL，更新 Backend | Worker 类型变化时同步更新 | 视代码而定 |

### 7.1 更新前统一备份镜像标签

CPU ECS：

```bash
cd /data/mmdp/deploy
release=$(date +%Y%m%d-%H%M%S)
docker tag mmdp-backend:latest "mmdp-backend:rollback-$release"
docker tag mmdp-worker:latest "mmdp-worker:rollback-$release"
```

GPU ECS：

```bash
cd /data/mmdp-gpu
release=$(date +%Y%m%d-%H%M%S)
docker tag mmdp-worker-gpu:latest "mmdp-worker-gpu:rollback-$release"
```

### 7.2 仅更新 Backend

本地构建并上传新的 `deploy/backend/mmdp-backend.jar`，服务器执行：

```bash
cd /data/mmdp/deploy
docker compose build mmdp-backend
docker compose up -d --no-deps --force-recreate mmdp-backend
docker logs --tail 100 mmdp-backend
curl -i http://127.0.0.1/api/auth/me
```

JRE 基础层不变时构建很快。无需重建 MySQL、Nginx 或 Worker。

如果本次同时修改了 Worker/Backend 通信协议，应先部署兼容新旧 Worker 的 Backend，再更新 CPU Worker，最后启动并更新 GPU Worker。

### 7.3 仅更新前端

本地执行 `npm run build` 并上传 `deploy/frontend/dist` 内容到服务器原目录：

```text
/data/mmdp/deploy/frontend/dist
```

该目录以 bind mount 方式挂载到 Nginx，文件更新后立即生效，通常无需重建镜像或容器。验证：

```bash
curl -I http://127.0.0.1/login
```

如修改的是 `nginx.conf`，执行：

```bash
docker exec mmdp-nginx nginx -t
docker compose up -d --no-deps --force-recreate mmdp-nginx
```

### 7.4 仅更新 CPU Pipeline/Python 源码

本地创建不含依赖大文件的源码包：

```powershell
cd D:\workspace\multimodal-data-platform
.\copy-deploy.bat
tar -czf deploy\mmdp-worker-src.tar.gz -C deploy\worker\src .
scp deploy\mmdp-worker-src.tar.gz root@8.154.36.87:/tmp/
```

服务器必须把新源码同步到永久目录 `/data/mmdp/deploy/worker/src`。为正确删除已经移除的旧 Pipeline 文件，推荐使用 `rsync --delete`：

```bash
rm -rf /tmp/mmdp-worker-src-new
mkdir -p /tmp/mmdp-worker-src-new
tar -xzf /tmp/mmdp-worker-src.tar.gz -C /tmp/mmdp-worker-src-new

# 首次缺少 rsync 时执行：apt install -y rsync
rsync -a --delete /tmp/mmdp-worker-src-new/ /data/mmdp/deploy/worker/src/
```

然后使用 Docker 层缓存构建：

```bash
cd /data/mmdp/deploy
docker compose build mmdp-worker
docker compose up -d --no-deps --force-recreate mmdp-worker
docker logs --tail 100 mmdp-worker
```

当前 Dockerfile 将依赖安装放在 `COPY src/` 之前。因此只要 Dockerfile 和 requirements 未变化，torch、lerobot、evdev 等依赖层会显示 `CACHED`，只重新复制源码并导出新镜像。

不建议把 `docker cp ... /app/` 作为正式更新方式：它虽然快，但修改只存在于当前容器，容器重建或服务器恢复后会丢失。

### 7.5 仅更新 GPU Pipeline/Python 源码

将 7.4 生成的同一个源码包上传到 GPU ECS：

```powershell
scp deploy\mmdp-worker-src.tar.gz root@<GPU当前公网IP>:/tmp/
```

GPU ECS 同步到永久目录：

```bash
rm -rf /tmp/mmdp-worker-src-new
mkdir -p /tmp/mmdp-worker-src-new
tar -xzf /tmp/mmdp-worker-src.tar.gz -C /tmp/mmdp-worker-src-new

# 首次缺少 rsync 时执行：apt install -y rsync
rsync -a --delete /tmp/mmdp-worker-src-new/ /data/mmdp-gpu/worker/src/
```

再执行缓存构建和容器替换：

```bash
cd /data/mmdp-gpu
docker compose -f docker-compose.gpu.yml build mmdp-worker-gpu
docker compose -f docker-compose.gpu.yml \
  up -d --no-deps --no-build --force-recreate mmdp-worker-gpu
docker logs --tail 100 mmdp-worker-gpu
```

正常情况下，CUDA、ZED SDK、pyzed、PyTorch 和 requirements 层均显示 `CACHED`，不会再次下载 641.6 MB 的 torch wheel 或重新安装 ZED SDK。

### 7.6 修改依赖或基础环境

以下变化必须重建相应依赖层：

- `requirements.txt` / `requirements-gpu.txt`；
- Python、CUDA、ZED SDK、PyTorch 版本；
- apt 系统依赖；
- Dockerfile 中依赖层之前的任何指令。

构建时仍不要使用 `--no-cache`。BuildKit 会复用变化位置之前的层。

未来如果经常修改 Python 依赖，可将 pip 安装层改为 BuildKit cache mount：

```dockerfile
# syntax=docker/dockerfile:1
RUN --mount=type=cache,target=/root/.cache/pip \
    python3 -m pip install --default-timeout 600 \
      torch==2.10.0 \
      --index-url https://download.pytorch.org/whl/cu130
```

使用 cache mount 时不要在同一条命令中使用 `--no-cache-dir`。它主要减少失败重试时重复下载已完成依赖的成本；源码日常更新依靠 Docker 层缓存已经足够。

### 7.7 Pipeline 新增或元数据变化

新增 Pipeline 时至少检查：

1. Python Pipeline 类的 `pipeline_id`、`worker_type`、输入输出资产类型；
2. `pipeline_definition` 的 `pipeline_id`、`worker_type`、`enabled`、输入输出资产类型；
3. CPU/GPU Worker 是否安装运行依赖；
4. Worker 重启后是否在启动日志中自动发现并注册；
5. Backend 是否收到对应 Worker 类型的注册；
6. 创建一个最小 Job 验证领取、下载、执行、上传、上报全链路。

只改 Python 文件也必须重启或重建对应 Worker，否则运行中的 `PIPELINES` 注册表仍是旧版本。

## 8. 按量 GPU ECS 的开机、关机和自动启动

### 8.1 当前配置是否会自动启动 Worker

`docker-compose.gpu.yml` 中已经配置：

```yaml
restart: unless-stopped
```

满足以下条件时，从阿里云控制台“停止实例”后再次“启动实例”，GPU Worker 应随 Docker 自动启动：

1. Docker 服务已设置开机启动；
2. `mmdp-worker-gpu` 容器仍然存在；
3. 停机前没有手工执行 `docker stop mmdp-worker-gpu`；
4. 没有执行 `docker compose down` 删除容器；
5. 阿里云操作是“停止实例”，不是“释放实例”。

一次性检查：

```bash
systemctl enable docker
systemctl is-enabled docker
docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' mmdp-worker-gpu
```

预期：

```text
enabled
unless-stopped
```

### 8.2 推荐关机流程

1. 在平台确认没有 GPU Job 处于 `CREATED` 或 `RUNNING`；
2. 日志确认当前没有 `[START]`、`[DOWNLOAD]`、`[EXEC]`、`[UPLOAD]` 中的任务；
3. 直接在阿里云控制台停止 GPU ECS；
4. 不执行 `docker compose down`，也不要先执行 `docker stop mmdp-worker-gpu`。

这样容器及其 restart policy 会保留，下一次 ECS 开机后可自动恢复。

### 8.3 每次 GPU ECS 开机后的检查

开机并 SSH 登录后执行：

```bash
systemctl is-active docker
nvidia-smi
docker ps --filter name=mmdp-worker-gpu
docker logs --since 5m mmdp-worker-gpu
```

正常结果：

```text
Docker: active
mmdp-worker-gpu: Up
[register] 已向 Backend 注册 2 个 Pipeline
```

只有看到 Worker 已重新向 Backend 注册后，再从平台提交 GPU Job。

如果容器没有自动启动，但仍然存在：

```bash
docker start mmdp-worker-gpu
```

如果容器已被删除：

```bash
cd /data/mmdp-gpu
docker compose -f docker-compose.gpu.yml up -d --no-build mmdp-worker-gpu
```

如果 Docker 没有启动：

```bash
systemctl start docker
cd /data/mmdp-gpu
docker compose -f docker-compose.gpu.yml up -d --no-build mmdp-worker-gpu
```

### 8.4 按量实例注意事项

- 停止实例前必须等待 GPU Job 完成，否则进程会被中断，Job 可能停留在运行中或失败；
- 不要释放实例，释放可能导致系统盘、容器和本地构建缓存丢失；
- GPU ECS 公网 IP 可能变化，运维登录时以控制台当前地址为准；
- CPU/GPU 业务通信始终使用 CPU ECS 私网 IP `172.31.133.185`；
- 停机期间 GPU Worker 离线，应先开机并等待注册成功，再创建 GPU Job。

## 9. 构建缓存和时间成本控制

### 9.1 日常必须做到

1. Dockerfile 中稳定依赖放前面，频繁变化的 `COPY src/` 放最后；
2. requirements 单独 COPY，再执行 pip install；
3. 源码更新不修改 Dockerfile 和 requirements；
4. 构建不加 `--no-cache`；
5. 不清理 BuildKit 缓存和当前基础镜像；
6. GPU ECS 不释放，保留系统盘和 `/var/lib/docker`；
7. 使用 `.dockerignore` 排除 `.env`、IDE 文件、缓存、Windows wheel 和无关大文件；
8. 更新后保留至少一个可用 rollback 镜像标签。

查看空间和缓存：

```bash
df -h /data /var/lib/docker
docker system df
docker images
```

只有磁盘空间确实不足时才清理明确不再需要的旧镜像。不要在日常更新前运行全量 prune。

### 9.2 为什么源码更新不会再次耗时 90 分钟

首次 GPU 构建慢的核心是 ZED SDK、CUDA 版 PyTorch 和 NVIDIA wheel 下载。当前 Dockerfile 已将它们放在源码 COPY 之前。

只要以下文件没有变化：

```text
worker/Dockerfile.gpu
worker/ZED_SDK_*.run
worker/src/requirements-gpu.txt
基础镜像标签
```

后续只修改 `worker/src/main.py` 或 `worker/src/pipelines/*.py` 时，依赖层会命中缓存，只生成末尾源码层。

### 9.3 不推荐的“快速方案”

- `docker cp` 直接修改运行容器：容器重建后丢失；
- 将整个 `/app` bind mount：会遮住镜像内的依赖或 SMPL 模型；
- 每次打包并上传完整 8.59 GB GPU 镜像：在 5 Mbps 公网带宽下通常比缓存构建更慢；
- 为了释放空间执行 `docker system prune -a`：会删除最昂贵的依赖缓存；
- 不固定 torch/CUDA 版本：可能重新解析依赖或装成 CPU 版。

## 10. 回滚

### 10.1 Backend 回滚

```bash
docker images 'mmdp-backend' --format 'table {{.Repository}}:{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}'
docker tag mmdp-backend:rollback-<时间戳> mmdp-backend:latest
cd /data/mmdp/deploy
docker compose up -d --no-deps --no-build --force-recreate mmdp-backend
```

### 10.2 CPU Worker 回滚

```bash
docker tag mmdp-worker:rollback-<时间戳> mmdp-worker:latest
cd /data/mmdp/deploy
docker compose up -d --no-deps --no-build --force-recreate mmdp-worker
```

### 10.3 GPU Worker 回滚

```bash
docker tag mmdp-worker-gpu:rollback-<时间戳> mmdp-worker-gpu:latest
cd /data/mmdp-gpu
docker compose -f docker-compose.gpu.yml \
  up -d --no-deps --no-build --force-recreate mmdp-worker-gpu
```

镜像回滚不等于数据库回滚。涉及数据库结构或数据变更时，必须事先准备并验证独立回滚 SQL。

## 11. 本次问题与有效处理对照表

| 现象 | 根因 | 有效处理 |
|---|---|---|
| `KeyError: 'ContainerConfig'` | Compose V1 与新 Docker Engine 不兼容 | 安装 Compose V2，统一使用 `docker compose` |
| 容器名 `xxx_mmdp-mysql` 冲突 | Compose V1 重建失败留下重命名容器 | 检查状态和挂载，删除已退出旧容器，再由 V2 创建 |
| Backend 不断重启、MySQL `Access denied` | `.env` 密码与已有数据目录中的真实密码不一致 | 修正 `.env`，只重建 Backend |
| `/api/auth/me` 返回 401 | 未登录 | 属于正常健康检查结果，不是服务故障 |
| `docker compose ps` 提示找不到配置 | GPU Compose 文件名非默认名称 | 始终加 `-f docker-compose.gpu.yml` |
| GPU apt/pip 很慢 | 公网带宽小、海外源慢 | apt/PyPI 使用国内镜像，CUDA torch 保留官方 cu130 索引 |
| 清华镜像找不到 `numpy==2.2.6` | 镜像同步或兼容问题 | 使用阿里云 PyPI，并在 ZED 安装前预装 NumPy |
| `/usr/local/zed/pyzed/ does not exist` | ZED 5.4.1 安装布局与旧方案不同 | 使用 `/usr/local/zed/get_python_api.py` |
| 下载 `torch` 长时间无进度 | 641.6 MB CUDA wheel 且 BuildKit 输出不连续 | 网卡接收字节增长则继续等待，不中断 |
| `libnvcuvid.so.1` 缺失 | 容器没有 NVIDIA video 驱动能力 | `NVIDIA_DRIVER_CAPABILITIES=all` 后重建容器，无需重建镜像 |
| ZED 首次显示模型优化 | 首次针对当前 GPU 生成优化模型 | 保持容器运行并等待，只发生首次或缓存丢失后 |

## 12. 日常运维命令速查

CPU ECS：

```bash
cd /data/mmdp/deploy
docker compose ps
docker logs --tail 100 mmdp-backend
docker logs --tail 100 mmdp-worker
docker logs --tail 100 mmdp-nginx
curl -i http://127.0.0.1/api/auth/me
curl -I http://127.0.0.1/login
```

GPU ECS：

```bash
cd /data/mmdp-gpu
docker compose -f docker-compose.gpu.yml ps
docker logs --tail 100 mmdp-worker-gpu
nvidia-smi
docker exec mmdp-worker-gpu python3 -c \
  'import pyzed.sl as sl, torch; print(torch.cuda.is_available(), torch.cuda.get_device_name(0))'
```

全链路 Job 验收标准：

```text
创建 Job
→ 对应 worker_type 的 Worker 领取
→ 按 input_asset_types 下载匹配文件
→ Pipeline 执行
→ 产物上传 OSS
→ Worker 上报成功
→ Backend Job 状态变为成功
```

## 13. 相关文档

- [CPU/GPU Worker 开发、构建与部署指南](MMDP_GPU_Worker_Deployment_Guide.md)
- [GPU Worker 架构设计](MMDP_GPU_Worker_Design.md)
- [CPU 实例环境信息](MMDP_CPU_Instance_Docker_Info.md)
- [GPU 实例 CUDA/Docker 信息](MMDP_GPU_Instance_CUDA_Docker_Info.md)
- [项目部署文档](deploy.md)
