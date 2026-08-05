# MMDP 开发、部署环境与硬件说明

> 整理日期：2026-07-31  
> 文档范围：本地开发机、阿里云 CPU ECS、阿里云 GPU ECS，以及三者之间的开发、部署和数据访问关系。

## 1. 文档说明

本文整合以下三份环境记录，原文继续保留：

- [MMDP 本地开发环境与配置说明](MMDP_Local_Development_Environment.md)
- [MMDP CPU 实例环境与容器运行信息](MMDP_CPU_Instance_Docker_Info.md)
- [MMDP GPU 实例环境信息](MMDP_GPU_Instance_CUDA_Docker_Info.md)

本文中的信息分为四类：

| 类型 | 含义 |
|---|---|
| 固定配置 | 实例规格、软件版本、路径、端口和配置方式 |
| 当前部署 | 已经实际运行的服务和容器 |
| 运行快照 | 某个记录时刻的 CPU、内存、I/O 和 GPU 状态 |
| 推荐方案 | 尚需部署或验证的 GPU Worker、CUDA 容器方案 |

运行快照会随负载变化，不应视为资源限制或长期容量指标。

## 2. 环境总览

### 2.1 节点职责

| 节点 | 操作系统 | 核心配置 | 主要职责 |
|---|---|---|---|
| 本地开发机 | Windows 11 64 位 | Java 21、Maven 3.9.12、`pose_env`、Node.js | 在 IDEA、PyCharm 中运行 Backend、Worker 和 Frontend，进行开发测试 |
| CPU ECS | Ubuntu 22.04.5 LTS | 4 vCPU、8 GiB RAM、100 GiB 系统盘 | 生产部署 Nginx、Backend、CPU Worker、MySQL 和 RustDesk |
| GPU ECS | Ubuntu 22.04 64 位 | 4 vCPU、15 GiB RAM、1 × Tesla T4 | 承载需要 CUDA、ZED SDK 或 GPU 加速的 Worker |
| 阿里云 OSS | 托管对象存储 | `cn-hangzhou`、Bucket `mmdp-test` | 保存采集数据、处理输入和 Pipeline 产物 |

### 2.2 整体拓扑

```text
Windows 11 开发机
├── Frontend：localhost:5173
│   ├── /api → localhost:19021
│   └── Collector → localhost:19022（按需）
├── Backend：localhost:19021
│   ├── MySQL → 8.154.36.87:13306
│   └── OSS → oss-cn-hangzhou.aliyuncs.com
└── CPU Worker：pose_env
    ├── Backend → localhost:19021
    └── OSS → oss-cn-hangzhou.aliyuncs.com

阿里云 VPC（华东 1 / 杭州可用区 K）
├── CPU ECS：172.31.133.185
│   ├── Nginx
│   ├── MMDP Backend
│   ├── MMDP CPU Worker
│   ├── MySQL 8.4.2
│   └── RustDesk hbbs / hbbr
├── GPU ECS：172.31.133.186
│   └── MMDP GPU Worker（推荐部署目标）
└── Aliyun OSS：cn-hangzhou / mmdp-test
```

### 2.3 核心端口与地址

| 服务 | 地址或端口 | 使用场景 |
|---|---|---|
| 本地 Frontend | `http://localhost:5173` | Vite 开发服务器 |
| 本地 Backend | `http://localhost:19021` | 本地后端 API 和本地 Worker 注册 |
| 本地 Collector | `http://localhost:19022` | 采集、实时状态和视频流，按需启动 |
| 生产 HTTP | CPU ECS `80/tcp` | Nginx HTTP 入口 |
| 生产 HTTPS | CPU ECS `443/tcp` | Nginx HTTPS 入口 |
| 远程 MySQL | `8.154.36.87:13306` | 本地 Backend 访问云端 MySQL |
| MySQL 容器端口 | `3306/tcp` | CPU ECS Docker 网络内部使用 |
| OSS Endpoint | `https://oss-cn-hangzhou.aliyuncs.com` | Backend 和 Worker 访问对象存储 |

## 3. 本地开发环境

### 3.1 本机工具链

| 项目 | 当前配置 |
|---|---|
| 操作系统 | Windows 11 64 位 |
| 项目路径 | `D:\workspace\multimodal-data-platform` |
| IntelliJ IDEA | IntelliJ IDEA 2025.1.3 |
| IntelliJ IDEA 路径 | `D:\application\IntelliJ IDEA 2025.1.3` |
| PyCharm | PyCharm 2023.2.5 |
| PyCharm 路径 | `D:\application\PyCharm 2023.2.5` |
| JDK | Oracle JDK 21.0.8 LTS |
| JDK 路径 | `D:\software\jdk-21` |
| Maven | Apache Maven 3.9.12 |
| Maven Home | `D:\software\apache-maven-3.9.12` |
| Conda | 25.5.1 |
| Python 虚拟环境 | `pose_env` |
| Python | 3.10.20 |
| Python 解释器 | `D:\software\Anaconda3\envs\pose_env\python.exe` |
| pip | 26.0.1 |
| Node.js | 24.16.0 |
| npm | 11.13.0 |
| 默认字符编码 | UTF-8 |

当前环境变量：

| 环境变量 | 当前状态 |
|---|---|
| `MAVEN_HOME` | `D:\software\apache-maven-3.9.12` |
| `JAVA_HOME` | 未设置 |

当前 `java` 命令和 Maven 实际使用 `D:\software\jdk-21`。建议将 `JAVA_HOME` 也设置为该路径，避免终端、IDEA 和 Maven 使用不同 JDK。

项目未提交 `.idea` 配置，因此 Project SDK、Maven Home、Python Interpreter 和 Working Directory 需要在每台开发机上单独设置。

### 3.2 Backend：IntelliJ IDEA

IDEA 推荐配置：

| 配置项 | 建议值 |
|---|---|
| Project SDK | Java 21，`D:\software\jdk-21` |
| Project language level | 21 |
| Maven Home | `D:\software\apache-maven-3.9.12` |
| Maven JDK | Project SDK / Java 21 |
| Maven 项目文件 | `mmdp-backend\pom.xml` |
| Working Directory | `D:\workspace\multimodal-data-platform\mmdp-backend` |
| 文件编码 | UTF-8 |

后端主要版本：

```text
Java：21
Spring Boot：3.3.12
MyBatis-Plus：3.5.7
Aliyun OSS SDK：3.17.4
```

启动命令：

```powershell
cd D:\workspace\multimodal-data-platform\mmdp-backend
mvn spring-boot:run
```

服务地址：

```text
http://localhost:19021
```

后端本地配置位于：

```text
mmdp-backend\.env
```

`application.yml` 使用以下配置读取 `.env`：

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

因此 IDEA Run Configuration 的 Working Directory 应指向 `mmdp-backend`，否则相对路径 `.env` 可能无法被找到。

后端 `.env` 的非敏感配置：

| 配置项 | 当前值 |
|---|---|
| `MMDP_DB_URL` | `jdbc:mysql://8.154.36.87:13306/mmdp_db?...` |
| `MMDP_DB_USERNAME` | `root` |
| `MMDP_STORAGE_DEFAULT_PROVIDER` | `OSS` |
| `MMDP_OSS_ENDPOINT` | `https://oss-cn-hangzhou.aliyuncs.com` |
| `MMDP_OSS_BUCKET` | `mmdp-test` |
| `MMDP_OSS_REGION` | `cn-hangzhou` |
| `MMDP_OSS_STS_ROLE_SESSION_NAME` | `mmdp-direct-upload` |
| `MMDP_OSS_STS_DURATION_SECONDS` | `900` 秒 |

### 3.3 CPU Worker：PyCharm

PyCharm 推荐配置：

| 配置项 | 建议值 |
|---|---|
| Script path | `D:\workspace\multimodal-data-platform\mmdp-worker\main.py` |
| Python Interpreter | `D:\software\Anaconda3\envs\pose_env\python.exe` |
| Working Directory | `D:\workspace\multimodal-data-platform\mmdp-worker` |
| Parameters | 默认无参数；查看 Pipeline 时使用 `--list-pipelines` |

启动命令：

```powershell
cd D:\workspace\multimodal-data-platform\mmdp-worker
D:\software\Anaconda3\envs\pose_env\python.exe main.py
```

`pose_env` 当前关键依赖：

| Python 包 | 本地已安装版本 |
|---|---:|
| `torch` | `2.10.0+cpu` |
| `numpy` | `2.2.6` |
| `scipy` | `1.15.3` |
| `h5py` | `3.16.0` |
| `opencv-python` | `4.13.0.92` |
| `requests` | `2.34.2` |
| `oss2` | `2.19.1` |
| `lerobot` | `0.4.4` |

PyTorch 当前状态：

```text
torch.cuda.is_available() = False
torch.version.cuda = None
```

当前 `pose_env` 使用 CPU 版 PyTorch，适合本地 CPU Worker。GPU Worker 应在 GPU ECS 的独立 CUDA 容器环境中运行，不应直接复用本地 `pose_env`。

项目 `requirements.txt` 声明：

```text
requests>=2.28
oss2>=2.18
numpy>=1.24
h5py>=3.10
scipy>=1.12
opencv-python>=4.9
lerobot
```

Worker 本地配置位于：

```text
mmdp-worker\.env
```

`config.py` 会主动读取与自身同目录的 `.env`。如果操作系统中已有同名环境变量，则系统环境变量优先。

Worker `.env` 的非敏感配置：

| 配置项 | 当前值 |
|---|---|
| `MMDP_BACKEND_URL` | `http://localhost:19021` |
| `MMDP_OSS_ENDPOINT` | `https://oss-cn-hangzhou.aliyuncs.com` |
| `MMDP_OSS_BUCKET` | `mmdp-test` |
| `MMDP_OSS_REGION` | `cn-hangzhou` |
| `MMDP_WORKER_WORK_DIR` | `D:\lab\tmp\mmdp-worker` |
| `MMDP_SMPL_MODEL_DIR` | `D:\lab\models` |
| `MMDP_PHYSICS_DEVICE` | `cpu` |
| `MMDP_WORKER_POLL_INTERVAL` | 未显式设置，使用默认值 `5` 秒 |

### 3.4 Frontend：Vite

前端环境：

```text
Node.js 24.16.0
npm 11.13.0
Vue 3
TypeScript
Vite 6
Tailwind CSS 4
```

启动命令：

```powershell
cd D:\workspace\multimodal-data-platform\mmdp-frontend
npm install
npm run dev
```

访问地址：

```text
http://localhost:5173
```

Vite 开发代理：

```text
/api → http://localhost:19021
```

Axios 的 API Base URL 读取 `VITE_API_BASE_URL`；未配置时使用 `/`，由 Vite 将 `/api` 请求代理到本地 Backend。

当前关键包实际版本：

| 包 | 本地版本 |
|---|---:|
| `vue` | `3.5.34` |
| `vue-router` | `4.6.4` |
| `vite` | `6.4.3` |
| `typescript` | `5.9.3` |
| `axios` | `1.16.1` |
| `tailwindcss` | `4.3.0` |
| `three` | `0.185.0` |
| `ali-oss` | `6.23.0` |

当前前端目录未发现 npm、pnpm 或 Yarn 锁文件。重新执行 `npm install` 时，带 `^` 的依赖可能更新到新的兼容版本。

### 3.5 本地 Collector

前端采集原型使用独立的本地 Collector：

| 功能 | 地址 |
|---|---|
| HTTP API | `http://localhost:19022` |
| 实时 WebSocket | `ws://localhost:19022/ws/realtime` |
| 视频流 | `http://localhost:19022/stream/{source}` |

普通平台页面不依赖 Collector。开发采集、实时状态或视频预览功能时，需要额外启动 `19022` 端口的 Collector。

### 3.6 本地启动顺序

1. 在 IntelliJ IDEA 中启动 Backend，确认 `http://localhost:19021` 可访问；
2. 在 PyCharm 中使用 `pose_env` 启动 Worker；
3. 在前端目录执行 `npm run dev`，访问 `http://localhost:5173`；
4. 仅在开发采集功能时启动本地 Collector。

Worker 启动时会向 Backend 注册 Pipeline。若先启动 Worker、后启动 Backend，Worker 需要等待后续心跳重试。

## 4. CPU ECS：主服务节点

> 实例与运行状态记录日期：2026-07-31

### 4.1 实例配置

| 项目 | 当前配置 |
|---|---|
| 实例名称 | `launch-advisor-20260601` |
| 实例 ID | `i-bp1hq2o77hv60nmidyd5` |
| 运行状态 | 运行中 |
| 付费类型 | 包年包月 |
| 到期时间 | `2027-06-01 23:59:59` |
| 创建时间 | `2026-06-01 13:40:00` |
| 地域 | 华东 1（杭州） |
| 可用区 | 杭州可用区 K |
| 实例规格 | `ecs.u2a-c1m2.xlarge` |
| CPU | 4 vCPU |
| 内存 | 8 GiB |
| 私网 IP | `172.31.133.185` |
| 公网 IP | `8.154.36.87` |
| 公网带宽 | 峰值 5 Mbps，按固定带宽计费 |
| 操作系统 | Ubuntu 22.04 64 位 |
| 系统盘 | ESSD Entry 云盘 100 GiB |
| 自动续费 | 手动续费 |

### 4.2 宿主机状态

```text
Ubuntu 22.04.5 LTS
Linux 5.15.0-179-generic x86_64
Hostname：iZbp1hq2o77hv60nmidyd5Z
```

内存快照：

| 指标 | 数值 |
|---|---:|
| 物理内存总量 | 7.1 GiB |
| 已使用 | 1.5 GiB |
| 空闲 | 423 MiB |
| 共享内存 | 3.0 MiB |
| Buffer/Cache | 5.1 GiB |
| 可用内存 | 5.3 GiB |
| Swap 总量 | 0 B |
| Swap 已使用 | 0 B |

当前宿主机没有配置 Swap。操作系统可见的 7.1 GiB 与实例标称 8 GiB 属于容量统计口径差异。

### 4.3 Docker 容器

| 容器名称 | 镜像 | 状态 | 对外端口 | 主要职责 |
|---|---|---|---|---|
| `mmdp-nginx` | `nginx:1.27-alpine` | Up 4 weeks | `80:80`、`443:443`（IPv4/IPv6） | Web 入口、HTTPS 和反向代理 |
| `mmdp-worker` | `mmdp-worker:latest` | Up 4 weeks | 无 | CPU 数据处理 Worker |
| `mmdp-backend` | `mmdp-backend:latest` | Up 4 weeks | 容器内 `19021/tcp` | Spring Boot 后端 |
| `mmdp-mysql` | `mysql:8.4.2` | Up 4 weeks（healthy） | `13306:3306`（IPv4/IPv6）、容器内 `33060/tcp` | MMDP 数据库 |
| `hbbs` | `rustdesk/rustdesk-server:latest` | Up 6 weeks | 无 | RustDesk ID/信令服务 |
| `hbbr` | `rustdesk/rustdesk-server:latest` | Up 6 weeks | 无 | RustDesk 中继服务 |

容器关系：

```text
CPU ECS
├── mmdp-nginx
│   ├── 80/tcp
│   └── 443/tcp
├── mmdp-backend
│   └── 19021/tcp（仅容器网络）
├── mmdp-worker
├── mmdp-mysql
│   └── 13306/tcp → 3306/tcp
├── hbbs
└── hbbr
```

### 4.4 容器资源快照

以下数据来自记录时的 `docker stats`：

| 容器名称 | CPU | 内存使用 / 宿主机可见上限 | 内存占比 | 网络 I/O | 块设备 I/O | PIDs |
|---|---:|---:|---:|---:|---:|---:|
| `mmdp-nginx` | 0.00% | 2.859 MiB / 7.096 GiB | 0.04% | 26.7 MB / 29.3 MB | 221 kB / 12.3 kB | 2 |
| `mmdp-worker` | 0.00% | 30.67 MiB / 7.096 GiB | 0.42% | 604 MB / 337 MB | 20.5 kB / 247 MB | 1 |
| `mmdp-backend` | 0.03% | 542.4 MiB / 7.096 GiB | 7.46% | 1.4 GB / 906 MB | 12.3 kB / 1.14 GB | 42 |
| `mmdp-mysql` | 0.13% | 532.5 MiB / 7.096 GiB | 7.33% | 1.35 GB / 3.64 GB | 43 MB / 241 MB | 56 |
| `hbbs` | 0.02% | 2.449 MiB / 7.096 GiB | 0.03% | 0 B / 0 B | 0 B / 2.86 MB | 13 |
| `hbbr` | 0.00% | 1.324 MiB / 7.096 GiB | 0.02% | 0 B / 0 B | 0 B / 0 B | 7 |

快照结论：

- 6 个容器均在运行，MySQL 健康检查为 `healthy`；
- 容器合计使用内存约 1.09 GiB；
- 宿主机可用内存约 5.3 GiB；
- CPU 负载整体较低；
- 内存主要由 Backend 和 MySQL 使用；
- 快照未显示容器单独设置的内存限制。

## 5. GPU ECS：计算节点

> 实例与 GPU 状态记录日期：2026-07-23

### 5.1 实例配置

| 项目 | 当前配置 |
|---|---|
| 实例名称 | `launch-advisor-20260723` |
| 实例 ID | `i-bp1fanxpewrb1b806gsw` |
| 付费类型 | 按量付费 |
| 地域 | 华东 1（杭州） |
| 可用区 | 杭州可用区 K |
| 实例规格 | `ecs.gn6i-c4g1.xlarge` |
| CPU | 4 vCPU |
| 内存 | 15 GiB |
| GPU | 1 × NVIDIA Tesla T4 |
| GPU 显存 | 15360 MiB，约 15 GiB |
| 私网 IP | `172.31.133.186` |
| 当前公网 IP | `8.154.24.218` |
| 公网带宽 | 按使用流量，峰值 5 Mbps |
| 系统盘 | ESSD 云盘 100 GiB，PL0 |
| 操作系统 | Ubuntu 22.04 64 位 |
| 镜像 | Ubuntu 22.04，预装 NVIDIA GPU 驱动和 CUDA |
| 网络 | 与 CPU ECS 位于同一 VPC/交换网络 |

GPU ECS 的公网 IP 在节省停机后可能变化。CPU ECS 与 GPU ECS 之间的业务通信应优先使用私网 IP。

### 5.2 NVIDIA 驱动与 GPU 快照

`nvidia-smi` 的已验证结果：

```text
GPU Model:           Tesla T4
Driver Version:      580.126.09
CUDA Version:        13.0
GPU Memory:          15360 MiB
Persistence Mode:    On
Compute Mode:        Default
```

空闲状态快照：

```text
GPU Utilization:     0%
GPU Memory Usage:    0 MiB / 15360 MiB
Temperature:         37°C
Power Usage:         10 W / 70 W
Running Processes:   None
```

`nvidia-smi` 中的 `CUDA Version: 13.0` 表示当前驱动最高支持 CUDA 13.0，并不表示宿主机 CUDA Toolkit 的实际版本也是 13.0。

### 5.3 CUDA Toolkit

`nvcc --version` 的已验证结果：

```text
CUDA Toolkit:        12.8
NVCC Version:        V12.8.93
Build:               cuda_12.8.r12.8/compiler.35583870_0
```

当前宿主机组合：

```text
Ubuntu 22.04
NVIDIA Driver 580.126.09
CUDA Driver Capability 13.0
CUDA Toolkit 12.8
```

该组合正常：

- NVIDIA 驱动支持最高 CUDA 13.0；
- 宿主机默认 CUDA Toolkit 为 12.8；
- Docker 容器可以使用 CUDA 13 Runtime；
- 无需为了运行 CUDA 13 容器而卸载宿主机 CUDA Toolkit 12.8。

### 5.4 Docker 与待验证项

已确认的 Docker 版本：

```text
Docker Version: 29.5.1
Docker Build:   2518b52
```

NVIDIA Container Toolkit 仍需确认：

```bash
nvidia-ctk --version
docker info | grep -i runtime
```

### 5.5 推荐的 GPU Worker 容器

推荐保持宿主机环境不变：

```text
GPU ECS 宿主机
├── Ubuntu 22.04
├── NVIDIA Driver 580.126.09
├── CUDA Toolkit 12.8
├── Docker 29.5.1
└── NVIDIA Container Toolkit
    └── MMDP GPU Worker
        ├── Ubuntu 22.04 Runtime
        ├── CUDA 13 Runtime
        ├── Linux ZED SDK
        ├── pyzed
        └── Python Worker
```

该结构是推荐部署目标，不代表 GPU Worker 容器已经完成部署。

### 5.6 CUDA 容器验证

验证命令：

```bash
docker run --rm --gpus all \
  nvidia/cuda:13.0.0-base-ubuntu22.04 \
  nvidia-smi
```

正常情况下应识别：

```text
GPU:             Tesla T4
Driver Version:  580.126.09
CUDA Version:    13.0
```

如果出现 GPU Runtime 错误，可执行：

```bash
nvidia-ctk --version

sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker
```

然后重新运行 CUDA 容器验证。

## 6. 开发与部署的数据链路

### 6.1 本地开发模式

本地开发不运行本地 MySQL 或 OSS 模拟服务，直接复用云端资源：

```text
本地 Backend ──公网──> CPU ECS MySQL（8.154.36.87:13306）
本地 Backend ──HTTPS──> Aliyun OSS
本地 CPU Worker ──localhost──> 本地 Backend
本地 CPU Worker ──HTTPS──> Aliyun OSS
```

网络前提：

- CPU ECS 安全组和 MySQL 访问策略允许开发机访问 `13306/tcp`；
- 开发机能够访问阿里云 OSS HTTPS Endpoint；
- Backend 和 Worker 使用相同的 OSS Bucket、Region 和凭证；
- Worker 启动前，本地 Backend 已监听 `19021`。

### 6.2 服务器部署模式

```text
用户请求
   │
   ▼
CPU ECS Nginx（80/443）
   │
   ▼
CPU ECS Backend（容器内 19021）
   ├── MySQL 容器（3306）
   ├── CPU Worker
   ├── Aliyun OSS
   └── GPU Worker（通过 VPC 私网协作，推荐）
```

CPU ECS 与 GPU ECS 位于同一 VPC/交换网络。服务器之间应优先使用：

```text
CPU ECS 私网 IP：172.31.133.185
GPU ECS 私网 IP：172.31.133.186
```

### 6.3 Worker 与 Backend

Worker 采用“轮询、领取、执行、上报”模式：

1. Worker 向 Backend 注册 Pipeline；
2. Worker 轮询并领取待处理 Job；
3. Worker 从 OSS 下载输入文件；
4. Worker 在 CPU 或 GPU 节点执行 Pipeline；
5. Worker 将产物上传 OSS；
6. Worker 向 Backend 上报成功或失败结果。

CPU Worker 与 GPU Worker 应共享一致的 Backend 地址、OSS Bucket、Region 和访问凭证，但可以使用不同的：

- Worker 镜像；
- Python 依赖；
- 工作目录；
- `MMDP_PHYSICS_DEVICE`；
- Pipeline 能力；
- CPU、CUDA 和 ZED SDK 运行环境。

## 7. 配置与安全约定

### 7.1 本地敏感配置

本地配置文件：

```text
mmdp-backend\.env
mmdp-worker\.env
```

敏感变量包括：

```text
MMDP_DB_PASSWORD
MMDP_OSS_ACCESS_KEY_ID
MMDP_OSS_ACCESS_KEY_SECRET
MMDP_OSS_STS_ROLE_ARN
```

文档和 Git 中不得记录这些变量的真实值。

### 7.2 Git 忽略

项目 `.gitignore` 已覆盖：

```text
.idea/
.env
.env.local
.env.*
node_modules/
dist/
target/
```

禁止提交：

- 数据库密码和云服务凭证；
- 实际环境配置文件；
- IDE 用户配置；
- Python 虚拟环境；
- `node_modules`；
- Maven、Vite 等编译产物。

### 7.3 网络安全

- MySQL `13306/tcp` 当前用于本地开发远程访问，应通过 ECS 安全组限制来源 IP；
- 生产 Backend 的 `19021/tcp` 仅在容器网络中暴露，由 Nginx 对外提供 `80/443`；
- CPU ECS 与 GPU ECS 的内部通信优先走 VPC 私网；
- GPU ECS 公网 IP 可能变化，不应作为服务器间固定依赖；
- OSS AccessKey 应仅存放于受控环境变量或 `.env` 中，不写入代码和文档。

## 8. 运维与验证清单

### 8.1 本地开发

- [ ] IDEA Project SDK 和 Maven JDK 均为 Java 21；
- [ ] Maven Home 为 `D:\software\apache-maven-3.9.12`；
- [ ] PyCharm Interpreter 为 `pose_env`；
- [ ] Backend Working Directory 为 `mmdp-backend`；
- [ ] Backend `.env` 和 Worker `.env` 已配置但未提交；
- [ ] 本地 Backend 能访问远程 MySQL 和 OSS；
- [ ] Backend 启动后再启动 Worker；
- [ ] Frontend `/api` 正确代理到 `localhost:19021`。

### 8.2 CPU ECS

- [ ] Nginx、Backend、Worker 和 MySQL 容器处于运行状态；
- [ ] MySQL 健康检查为 `healthy`；
- [ ] `80/443` 对外访问正常；
- [ ] MySQL `13306` 仅允许必要来源访问；
- [ ] 定期检查内存、磁盘和容器日志；
- [ ] 评估是否需要为 8 GiB 主机增加 Swap 或容器资源限制。

### 8.3 GPU ECS

- [ ] `nvidia-smi` 正常识别 Tesla T4；
- [ ] 确认 `nvidia-ctk --version`；
- [ ] 确认 Docker NVIDIA Runtime；
- [ ] 运行 CUDA 13 基础镜像验证；
- [ ] 构建包含 CUDA 13、ZED SDK 和 pyzed 的 GPU Worker 镜像；
- [ ] 使用 CPU ECS 私网地址连接 Backend；
- [ ] 验证 GPU Worker 的 Pipeline 注册、Job 领取、OSS 下载、执行和上报链路。

## 9. 环境摘要

```text
本地开发机：
Windows 11
IntelliJ IDEA 2025.1.3
PyCharm 2023.2.5
Oracle JDK 21.0.8 LTS
Maven 3.9.12
pose_env / Python 3.10.20 / PyTorch 2.10.0+cpu
Node.js 24.16.0 / npm 11.13.0

CPU ECS：
ecs.u2a-c1m2.xlarge
4 vCPU / 8 GiB RAM / ESSD Entry 100 GiB
Ubuntu 22.04.5 LTS
Nginx + Backend + CPU Worker + MySQL 8.4.2
RustDesk hbbs / hbbr

GPU ECS：
ecs.gn6i-c4g1.xlarge
4 vCPU / 15 GiB RAM / 1 × NVIDIA Tesla T4
Ubuntu 22.04
NVIDIA Driver 580.126.09
驱动支持 CUDA 13.0
宿主机 CUDA Toolkit 12.8
Docker 29.5.1

共享资源：
MySQL：8.154.36.87:13306 / mmdp_db
OSS：cn-hangzhou / mmdp-test
```
