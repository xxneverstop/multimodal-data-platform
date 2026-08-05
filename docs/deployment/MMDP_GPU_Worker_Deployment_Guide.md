# MMDP CPU/GPU Worker 开发、构建与部署指南

> 更新日期：2026-08-03  
> 适用环境：Windows 11 开发机、CPU ECS `172.31.133.185`、GPU ECS `172.31.133.186`  
> 设计文档：[MMDP_GPU_Worker_Design.md](MMDP_GPU_Worker_Design.md)  
> 环境说明：[MMDP_Development_Deployment_Hardware_Environment.md](MMDP_Development_Deployment_Hardware_Environment.md)

## 1. 部署原则

CPU ECS 和 GPU ECS 位于同一 VPC、同一交换机：

| 节点 | 私网 IP | 职责 |
|---|---|---|
| CPU ECS | `172.31.133.185` | Nginx、Backend、MySQL、CPU Worker |
| GPU ECS | `172.31.133.186` | GPU Worker |

必须遵守以下约定：

1. CPU ECS 现有正式部署根目录为 `/data/mmdp/deploy`；
2. GPU ECS 正式部署根目录固定为 `/data/mmdp-gpu`；
3. CPU ECS 保持现有目录不迁移，避免触碰已有 `.env` 和 `mysql-data/`；
4. CPU ECS 的 `.env` 和 `mysql-data/` 必须原地保留，部署包不得覆盖；
5. GPU Worker 通过 CPU ECS 私网 IP 访问 Nginx，不走公网；
6. CPU 和 GPU 使用同一个 OSS Bucket 和同一套 OSS 凭证；
7. Dockerfile 或依赖变化时先构建镜像，只有 Python 源码变化时才使用快速更新。

## 2. 三端目录结构

### 2.1 Windows 本地源码

```text
D:\workspace\multimodal-data-platform\
├── copy-deploy.bat
├── mmdp-backend/
├── mmdp-frontend/
├── mmdp-worker/
│   ├── main.py
│   ├── config.py
│   ├── requirements.txt             # CPU/本地依赖，包含 lerobot==0.4.4
│   ├── requirements-gpu.txt         # GPU 依赖，不包含 lerobot
│   └── pipelines/
├── deploy/
│   ├── docker-compose.yml           # CPU ECS
│   ├── docker-compose.gpu.yml       # GPU ECS
│   ├── .env                         # 本地敏感配置，不上传
│   ├── .env.gpu.example             # 可提交的 GPU 环境变量模板
│   ├── backend/
│   ├── frontend/
│   ├── initdb/
│   ├── nginx/
│   └── worker/
│       ├── Dockerfile
│       ├── .dockerignore
│       ├── Dockerfile.dockerignore
│       ├── Dockerfile.gpu
│       ├── Dockerfile.gpu.dockerignore
│       ├── ZED_SDK_Ubuntu22_cuda13.0_tensorrt10.13_v5.4.1.zstd.run
│       ├── src/                      # copy-deploy.bat 生成
│       └── smpl_models/
└── docs/
```

`copy-deploy.bat` 复制 Worker 源码时会排除：

```text
.env / .env.*
.idea/ / .agents/
__pycache__/
*.pyc / *.pyo / *.whl
test-*.py
pipeline-manifest.json
```

### 2.2 CPU ECS 正式目录

```text
/data/mmdp/deploy/
├── docker-compose.yml
├── .env                              # 服务器已有，禁止覆盖
├── backend/
│   ├── Dockerfile
│   └── mmdp-backend.jar
├── worker/
│   ├── Dockerfile
│   ├── .dockerignore                 # legacy builder 必需
│   ├── Dockerfile.dockerignore
│   └── src/
│       ├── main.py
│       ├── requirements.txt
│       ├── requirements-gpu.txt
│       └── pipelines/
├── frontend/dist/
├── initdb/
├── nginx/nginx.conf
└── mysql-data/                       # 生产数据库，禁止移动或覆盖
```

CPU 包不包含 ZED SDK 和 SMPL-H 模型，避免向 CPU ECS 传输约 1.8 GiB 无用文件。

### 2.3 GPU ECS 正式目录

```text
/data/mmdp-gpu/
├── docker-compose.gpu.yml
├── .env                              # 服务器实际凭证，禁止覆盖
├── .env.gpu.example                  # 模板
└── worker/
    ├── Dockerfile.gpu
    ├── Dockerfile.gpu.dockerignore
    ├── ZED_SDK_Ubuntu22_cuda13.0_tensorrt10.13_v5.4.1.zstd.run
    ├── src/
    │   ├── main.py
    │   ├── requirements-gpu.txt
    │   └── pipelines/
    └── smpl_models/
        ├── male/model.npz
        ├── female/model.npz
        └── neutral/model.npz
```

Compose 构建关系：

| Compose | Build context | Dockerfile |
|---|---|---|
| CPU | `/data/mmdp/deploy/worker` | `Dockerfile` |
| GPU | `/data/mmdp-gpu/worker` | `Dockerfile.gpu` |

## 3. Dockerfile 依赖划分

### 3.1 CPU Worker

CPU Worker 使用：

```text
python:3.10-slim-bookworm
PyTorch 2.10.0 CPU
torchvision 0.25.0 CPU
lerobot 0.4.4
evdev 编译工具链
```

`lerobot` 在 Linux 上会经由 `pynput` 安装并编译 `evdev`。CPU Dockerfile 必须在安装 requirements 前保留 `build-essential`，否则会出现：

```text
fatal error: limits.h: No such file or directory
ERROR: Failed building wheel for evdev
```

基础镜像自带的旧 pip 不能正确处理 PyTorch 2.10 wheel 的部分包名元数据，因此 Dockerfile 会先升级 pip、setuptools 和 wheel。

Dockerfile 在依赖安装完成后会执行：

```python
import cv2, evdev, lerobot, torch
assert torch.version.cuda is None
```

只有自检通过，镜像才会构建成功。

### 3.2 GPU Worker

GPU Worker 使用：

```text
nvidia/cuda:13.0.0-runtime-ubuntu22.04
ZED SDK 5.4.1
pyzed
PyTorch 2.10.0 + CUDA 13.0
SMPL-H models
```

GPU Pipeline 只有：

- `G1_MERGE_CAMERA_ROBOT`：依赖 `pyzed`；
- `MOTION_PHYSICS_METRICS_V2`：依赖 CUDA PyTorch 和 SMPL-H。

GPU Worker 不安装 `lerobot`，因此使用独立的 `requirements-gpu.txt`。Dockerfile 会先升级 pip，但不使用 `--break-system-packages`。

依赖安装完成后会执行：

```python
import cv2, pyzed.sl, torch
assert torch.version.cuda.startswith("13.")
```

## 4. 本地开发验证

### 4.1 启动 Backend

```powershell
cd D:\workspace\multimodal-data-platform\mmdp-backend
mvn spring-boot:run
```

确认 `http://localhost:19021` 可访问。

### 4.2 验证 Worker Pipeline 分组

```powershell
cd D:\workspace\multimodal-data-platform\mmdp-worker

# CPU：只列出 CPU Pipeline
$env:MMDP_WORKER_TYPE="CPU"
D:\software\Anaconda3\envs\pose_env\python.exe main.py --list-pipelines

# GPU：只列出 GPU Pipeline
$env:MMDP_WORKER_TYPE="GPU"
D:\software\Anaconda3\envs\pose_env\python.exe main.py --list-pipelines

# 恢复默认 ALL
Remove-Item Env:MMDP_WORKER_TYPE
```

预期：CPU 模式不包含两个 GPU Pipeline；GPU 模式只包含两个 GPU Pipeline。

### 4.3 启动本地 Worker

```powershell
$env:MMDP_WORKER_TYPE="ALL"
D:\software\Anaconda3\envs\pose_env\python.exe main.py
```

本地 `pose_env` 必须安装 `requirements.txt`，并具有可用的 Windows `pyzed` 环境，才能在 ALL 模式执行所有 Pipeline。

## 5. 生成部署源码

```powershell
cd D:\workspace\multimodal-data-platform
.\copy-deploy.bat
```

脚本会：

1. 构建 Backend JAR；
2. 构建 Frontend；
3. 更新 `deploy/backend` 和 `deploy/frontend`；
4. 镜像复制 Worker 源码到 `deploy/worker/src`；
5. 排除本地 `.env`、IDE 文件、Windows wheel 和缓存文件；
6. 检查 `requirements-gpu.txt` 是否存在。

构建后检查：

```powershell
Test-Path deploy\worker\src\requirements.txt
Test-Path deploy\worker\src\requirements-gpu.txt
Test-Path deploy\worker\src\.env
```

预期结果为：

```text
True
True
False
```

## 6. CPU ECS 部署

### 6.1 生成 CPU 部署包

现有 CPU ECS 已经使用 `/data/mmdp/deploy`，部署包必须解压到该目录，不迁移现有 MySQL 数据。

在 Windows 项目根目录执行：

```powershell
tar -czf deploy\mmdp-cpu-deploy.tar.gz `
  -C deploy `
  docker-compose.yml `
  python3-10-slim.tar `
  backend frontend initdb nginx `
  worker/Dockerfile `
  worker/.dockerignore `
  worker/Dockerfile.dockerignore `
  worker/src
```

该压缩包不包含：

- `deploy/.env`；
- `mysql-data/`；
- ZED SDK；
- SMPL-H 模型；
- GPU Dockerfile。

### 6.2 上传并解压到正式根目录

```powershell
scp deploy\mmdp-cpu-deploy.tar.gz root@8.154.36.87:/tmp/
ssh root@8.154.36.87
```

CPU ECS 执行：

```bash
tar -xzf /tmp/mmdp-cpu-deploy.tar.gz -C /data/mmdp/deploy
cd /data/mmdp/deploy

test -f .env
test -d mysql-data
test -f worker/Dockerfile
test -f worker/.dockerignore
test -f worker/src/requirements-gpu.txt
docker-compose config >/dev/null
```

所有命令都应成功。此时 `pwd` 必须是：

```text
/data/mmdp/deploy
```

### 6.3 MySQL 保护

本次只重建 Backend、CPU Worker 和 Nginx，不执行数据库迁移，不停止、重建或删除 `mmdp-mysql`，不修改 `mysql-data/`。

### 6.4 构建和启动

服务器无法稳定访问 Docker Hub，先加载离线 Python 基础镜像并补充 Dockerfile 要求的标签。旧版 legacy builder 只识别 `worker/.dockerignore`：

```bash
cd /data/mmdp/deploy
docker load -i python3-10-slim.tar
docker tag python:3.10-slim python:3.10-slim-bookworm
docker image inspect python:3.10-slim-bookworm >/dev/null

docker-compose build mmdp-worker
docker-compose build mmdp-backend
```

成功后只更新业务容器，不触碰 MySQL：

```bash
docker-compose up -d --no-deps mmdp-worker mmdp-backend
docker-compose up -d --no-deps --force-recreate mmdp-nginx
docker-compose ps
```

### 6.5 验证 CPU Worker

```bash
docker logs --tail=50 mmdp-worker

docker run --rm --entrypoint python mmdp-worker:latest \
  -c "import cv2, evdev, importlib.metadata as m, torch; assert torch.version.cuda is None; assert m.version('lerobot') == '0.4.4'; print('CPU image OK')"
```

日志应显示：

```text
Worker 类型: CPU
已向 Backend 注册 CPU Pipeline
```

## 7. GPU ECS 部署

### 7.1 前置检查

GPU ECS 启动后执行：

```bash
nvidia-smi
nvidia-ctk --version
docker run --rm --gpus all \
  nvidia/cuda:13.0.0-runtime-ubuntu22.04 nvidia-smi
```

如果 NVIDIA Container Toolkit 尚未配置：

```bash
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker
```

### 7.2 本地文件检查

ZED SDK 当前实际大小约 1.52 GiB，并非 300 MiB：

```powershell
Get-Item deploy\worker\ZED_SDK_Ubuntu22_cuda13.0_tensorrt10.13_v5.4.1.zstd.run
Get-ChildItem deploy\worker\smpl_models -Recurse -Filter model.npz
```

必须存在：

```text
ZED_SDK_Ubuntu22_cuda13.0_tensorrt10.13_v5.4.1.zstd.run
smpl_models/male/model.npz
smpl_models/female/model.npz
smpl_models/neutral/model.npz
```

### 7.3 生成 GPU 部署包

```powershell
tar -czf deploy\mmdp-gpu-deploy.tar.gz `
  -C deploy `
  docker-compose.gpu.yml .env.gpu.example `
  worker/Dockerfile.gpu `
  worker/Dockerfile.gpu.dockerignore `
  worker/ZED_SDK_Ubuntu22_cuda13.0_tensorrt10.13_v5.4.1.zstd.run `
  worker/src `
  worker/smpl_models
```

首次包较大。后续仅修改 Python 代码时不要重复上传 ZED SDK，见“快速更新”。

### 7.4 上传并解压到正式根目录

```powershell
scp deploy\mmdp-gpu-deploy.tar.gz root@<GPU公网IP>:/tmp/
ssh root@<GPU公网IP>
```

GPU ECS 执行：

```bash
mkdir -p /data/mmdp-gpu
tar -xzf /tmp/mmdp-gpu-deploy.tar.gz -C /data/mmdp-gpu
cd /data/mmdp-gpu

# 首次部署才从模板创建，已有 .env 不覆盖
test -f .env || cp .env.gpu.example .env

test -f worker/Dockerfile.gpu
test -f worker/src/requirements-gpu.txt
test -f worker/smpl_models/neutral/model.npz
! grep -q -- '--break-system-packages' worker/Dockerfile.gpu
docker compose -f docker-compose.gpu.yml config >/dev/null
```

此时 `pwd` 必须是：

```text
/data/mmdp-gpu
```

### 7.5 配置 GPU 环境变量

编辑：

```bash
vim /data/mmdp-gpu/.env
```

至少配置：

```ini
MMDP_BACKEND_URL=http://172.31.133.185
MMDP_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
MMDP_OSS_ACCESS_KEY_ID=<实际值>
MMDP_OSS_ACCESS_KEY_SECRET=<实际值>
MMDP_OSS_BUCKET=mmdp-test
MMDP_OSS_REGION=cn-hangzhou
```

不得保留 `<实际值>` 占位符。

验证 GPU ECS 到 CPU ECS 的私网访问：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' http://172.31.133.185/
```

### 7.6 构建和启动

```bash
cd /data/mmdp-gpu
docker compose -f docker-compose.gpu.yml build mmdp-worker-gpu
docker compose -f docker-compose.gpu.yml up -d mmdp-worker-gpu
```

不要加 `--no-cache`。之前成功的 CUDA、apt 和 ZED SDK 层会被复用。

### 7.7 验证 GPU Worker

```bash
docker run --rm --gpus all \
  --entrypoint python3 mmdp-worker-gpu:latest \
  -c "import pyzed.sl, torch; assert torch.cuda.is_available(); print(torch.__version__, torch.version.cuda, torch.cuda.get_device_name(0))"

docker logs --tail=50 mmdp-worker-gpu
```

预期：

```text
Worker 类型: GPU
已注册 2 个 GPU Pipeline
torch.cuda.is_available() = True
```

## 8. 全链路验证

### 8.1 CPU Job

提交 `BUILD_PLAYBACK`，然后在 CPU ECS 查看：

```bash
docker logs -f mmdp-worker
```

预期：CPU Worker 完成领取、下载、执行、上传和上报。

### 8.2 GPU Job

提交 `MOTION_PHYSICS_METRICS_V2`，然后在 GPU ECS 查看：

```bash
docker logs -f mmdp-worker-gpu
```

预期：GPU Worker 完成领取、下载、CUDA 执行、上传和上报。

### 8.3 Job 隔离

- CPU Worker 只领取 `worker_type=CPU` 的 Job；
- GPU Worker 只领取 `worker_type=GPU` 的 Job；
- 两端通过同一 Backend 和 OSS 协作。

## 9. 快速更新

仅当 Dockerfile 和 requirements 均未变化时使用。

### 9.1 CPU Worker

本地上传源码：

```powershell
tar -czf deploy\mmdp-worker-src.tar.gz -C deploy\worker\src .
scp deploy\mmdp-worker-src.tar.gz root@8.154.36.87:/tmp/
```

CPU ECS：

```bash
rm -rf /tmp/mmdp-worker-src
mkdir -p /tmp/mmdp-worker-src
tar -xzf /tmp/mmdp-worker-src.tar.gz -C /tmp/mmdp-worker-src
docker cp /tmp/mmdp-worker-src/. mmdp-worker:/app/
docker restart mmdp-worker
```

### 9.2 GPU Worker

将同一个源码包上传到 GPU ECS，然后执行：

```bash
rm -rf /tmp/mmdp-worker-src
mkdir -p /tmp/mmdp-worker-src
tar -xzf /tmp/mmdp-worker-src.tar.gz -C /tmp/mmdp-worker-src
docker cp /tmp/mmdp-worker-src/. mmdp-worker-gpu:/app/
docker restart mmdp-worker-gpu
```

## 10. 常见问题

### 10.1 CPU：`limits.h: No such file or directory`

服务器使用了旧 CPU Dockerfile。检查：

```bash
cd /data/mmdp/deploy
grep -n 'build-essential' worker/Dockerfile
```

必须能看到 `build-essential`。同步新版 Dockerfile 后重新执行：

```bash
docker-compose build mmdp-worker
```

### 10.2 GPU：`no such option: --break-system-packages`

服务器使用了旧 GPU Dockerfile。检查：

```bash
cd /data/mmdp-gpu
grep -n -- '--break-system-packages' worker/Dockerfile.gpu
```

新版文件应无输出。不要通过升级 pip 掩盖旧文件问题，直接同步当前 Dockerfile。

### 10.3 GPU 又开始编译 `evdev`

说明 GPU Dockerfile 错用了 `requirements.txt`。必须是：

```dockerfile
COPY src/requirements-gpu.txt /app/requirements.txt
```

### 10.4 `torch.cuda.is_available() = False`

检查：

```bash
nvidia-ctk --version
docker compose -f docker-compose.gpu.yml config
docker run --rm --gpus all --entrypoint nvidia-smi mmdp-worker-gpu:latest
```

同时确认镜像内是 CUDA 版 PyTorch：

```bash
docker run --rm --entrypoint python3 mmdp-worker-gpu:latest \
  -c "import torch; print(torch.__version__, torch.version.cuda)"
```

### 10.5 GPU Worker 领取不到 Job

本指南不操作生产 MySQL。先确认 GPU Worker 已注册并持续心跳：

```bash
docker logs mmdp-worker-gpu 2>&1 | grep -i 'register\|error\|traceback'
```

### 10.6 CPU 部署目录

当前生产 CPU Compose 必须从以下目录执行：

```text
/data/mmdp/deploy
```

其中的 `.env` 和 `mysql-data/` 是原生产配置与数据，部署包不得包含或覆盖它们。

### 10.7 构建上下文过大

CPU ECS 使用 Docker legacy builder，必须在构建上下文根目录放置 `.dockerignore`：

```text
.dockerignore                 # legacy builder 实际生效
Dockerfile.dockerignore       # BuildKit 构建 CPU Dockerfile 时生效
Dockerfile.gpu.dockerignore   # GPU：保留 ZED SDK、SMPL models，排除本地垃圾
```

CPU 构建发送的 context 应为数 MB，不应包含 1.52 GiB ZED SDK 或 `smpl_models/`。

### 10.8 CPU 基础镜像访问 Docker Hub 超时

```bash
cd /data/mmdp/deploy
docker load -i python3-10-slim.tar
docker tag python:3.10-slim python:3.10-slim-bookworm
docker-compose build mmdp-worker
```

### 10.9 GPU 构建很慢

- ZED SDK 文件约 1.52 GiB；
- CUDA 13 PyTorch wheel 较大；
- GPU ECS 公网带宽只有 5 Mbps；
- 首次构建慢是正常现象；
- 不要随意使用 `--no-cache` 或 `docker builder prune`；
- 后续只修改源码时使用快速更新。

## 11. 最终检查清单

### CPU ECS

- [ ] 当前目录为 `/data/mmdp/deploy`；
- [ ] `.env` 和 `mysql-data/` 未被覆盖；
- [ ] CPU Dockerfile 包含 `build-essential`；
- [ ] CPU 镜像通过 `evdev/lerobot/torch CPU` 自检；
- [ ] CPU Worker 只注册 CPU Pipeline。

### GPU ECS

- [ ] 当前目录为 `/data/mmdp-gpu`；
- [ ] GPU Dockerfile 不含 `--break-system-packages`；
- [ ] 使用 `requirements-gpu.txt`；
- [ ] ZED SDK 和三个 SMPL-H 模型存在；
- [ ] CUDA 13 PyTorch 自检通过；
- [ ] GPU Worker 只注册两个 GPU Pipeline；
- [ ] Backend 地址使用 CPU ECS 私网 IP `172.31.133.185`。
