# MMDP 本地开发环境与配置说明

> 记录日期：2026-07-31  
> 本地开发方式：Windows 11 + IntelliJ IDEA + PyCharm，本地运行代码，复用阿里云 CPU ECS 上的 MySQL 和阿里云 OSS。

## 1. 本地开发拓扑

```text
Windows 11 开发机
├── IntelliJ IDEA
│   └── MMDP Backend（Java 21 / Spring Boot，localhost:19021）
│       ├── MySQL：8.154.36.87:13306
│       └── Aliyun OSS：oss-cn-hangzhou.aliyuncs.com
├── PyCharm
│   └── MMDP Worker（pose_env / Python 3.10，CPU）
│       ├── Backend：http://localhost:19021
│       └── Aliyun OSS：oss-cn-hangzhou.aliyuncs.com
├── Vite Frontend（localhost:5173）
│   └── /api → http://localhost:19021
└── Collector（按需启动，localhost:19022）
```

对应的服务器信息见 [MMDP CPU 实例环境与容器运行信息](MMDP_CPU_Instance_Docker_Info.md)。

## 2. 本机基础环境

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

当前系统环境变量状态：

| 环境变量 | 当前状态 |
|---|---|
| `MAVEN_HOME` | `D:\software\apache-maven-3.9.12` |
| `JAVA_HOME` | 未设置 |

虽然 `JAVA_HOME` 未设置，但当前 `java` 命令和 Maven 均实际使用 `D:\software\jdk-21` 下的 Java 21。为了减少 IDE、终端和 Maven 使用不同 JDK 的风险，建议后续将 `JAVA_HOME` 统一设置为该路径。

## 3. IntelliJ IDEA 后端配置

### 3.1 项目设置

| IDEA 配置项 | 建议值 |
|---|---|
| Project SDK | Java 21，`D:\software\jdk-21` |
| Project language level | 21 |
| Maven Home | `D:\software\apache-maven-3.9.12` |
| Maven JDK | Project SDK / Java 21 |
| Maven 项目文件 | `mmdp-backend\pom.xml` |
| Working Directory | `D:\workspace\multimodal-data-platform\mmdp-backend` |
| 文件编码 | UTF-8 |

后端项目的 `pom.xml` 已声明：

```text
Java：21
Spring Boot：3.3.12
MyBatis-Plus：3.5.7
Aliyun OSS SDK：3.17.4
```

本地启动命令：

```powershell
cd D:\workspace\multimodal-data-platform\mmdp-backend
mvn spring-boot:run
```

后端监听地址：

```text
http://localhost:19021
```

### 3.2 后端本地配置文件

配置文件位置：

```text
mmdp-backend\.env
```

`application.yml` 通过以下配置自动读取该文件：

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

因此从 IDEA 启动时，应确保 Working Directory 为 `mmdp-backend`，否则相对路径 `.env` 可能无法被找到。

当前后端 `.env` 的非敏感配置摘要：

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

以下敏感项已在本地配置，但不得写入文档或提交 Git：

```text
MMDP_DB_PASSWORD
MMDP_OSS_ACCESS_KEY_ID
MMDP_OSS_ACCESS_KEY_SECRET
MMDP_OSS_STS_ROLE_ARN
```

## 4. PyCharm Worker 配置

### 4.1 Python Interpreter

PyCharm 应使用现有 Conda 虚拟环境：

```text
环境名称：pose_env
解释器：D:\software\Anaconda3\envs\pose_env\python.exe
Python：3.10.20
```

建议的 Run Configuration：

| PyCharm 配置项 | 建议值 |
|---|---|
| Script path | `D:\workspace\multimodal-data-platform\mmdp-worker\main.py` |
| Python Interpreter | `D:\software\Anaconda3\envs\pose_env\python.exe` |
| Working Directory | `D:\workspace\multimodal-data-platform\mmdp-worker` |
| Parameters | 无；查看 Pipeline 时使用 `--list-pipelines` |

本地启动命令：

```powershell
cd D:\workspace\multimodal-data-platform\mmdp-worker
D:\software\Anaconda3\envs\pose_env\python.exe main.py
```

### 4.2 pose_env 当前关键依赖

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

当前 PyTorch 状态：

```text
torch.cuda.is_available() = False
torch.version.cuda = None
```

这说明当前 `pose_env` 安装的是 CPU 版 PyTorch，与本机 CPU Worker 的运行方式一致。

项目 `requirements.txt` 声明的依赖范围为：

```text
requests>=2.28
oss2>=2.18
numpy>=1.24
h5py>=3.10
scipy>=1.12
opencv-python>=4.9
lerobot
```

### 4.3 Worker 本地配置文件

配置文件位置：

```text
mmdp-worker\.env
```

Worker 的 `config.py` 会主动读取与自身同目录的 `.env`。如果操作系统中已存在同名环境变量，则系统环境变量优先，不会被 `.env` 覆盖。

当前 Worker `.env` 的非敏感配置摘要：

| 配置项 | 当前值 |
|---|---|
| `MMDP_BACKEND_URL` | `http://localhost:19021` |
| `MMDP_OSS_ENDPOINT` | `https://oss-cn-hangzhou.aliyuncs.com` |
| `MMDP_OSS_BUCKET` | `mmdp-test` |
| `MMDP_OSS_REGION` | `cn-hangzhou` |
| `MMDP_WORKER_WORK_DIR` | `D:\lab\tmp\mmdp-worker` |
| `MMDP_SMPL_MODEL_DIR` | `D:\lab\models` |
| `MMDP_PHYSICS_DEVICE` | `cpu` |
| `MMDP_WORKER_POLL_INTERVAL` | 未在 `.env` 中设置，使用默认值 `5` 秒 |

以下敏感项已在本地配置，但不得写入文档或提交 Git：

```text
MMDP_OSS_ACCESS_KEY_ID
MMDP_OSS_ACCESS_KEY_SECRET
```

## 5. 前端本地开发配置

前端技术环境：

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

本地访问地址：

```text
http://localhost:5173
```

`vite.config.ts` 中的开发代理：

```text
/api → http://localhost:19021
```

前端 Axios 的 API Base URL 读取 `VITE_API_BASE_URL`；未配置时使用 `/`，由 Vite 将 `/api` 请求代理到本地后端。

当前 `node_modules` 已安装，关键包的实际版本包括：

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

当前前端目录未发现 npm、pnpm 或 Yarn 锁文件，因此重新执行 `npm install` 时，带 `^` 的依赖可能更新到新的兼容版本。

## 6. 本地 Collector 端口

前端采集原型还包含一个独立的本地 Collector 连接：

| 功能 | 地址 |
|---|---|
| Collector HTTP API | `http://localhost:19022` |
| 实时 WebSocket | `ws://localhost:19022/ws/realtime` |
| 视频流 | `http://localhost:19022/stream/{source}` |

该服务与 MMDP Backend 的 `19021` 端口不同。只开发普通平台页面时可不启动；开发采集、实时状态或视频预览功能时，需要额外启动本地 Collector。

## 7. 远程 MySQL 与 OSS

本地开发不运行本地 MySQL 和 OSS 模拟服务，而是复用云端资源：

| 资源 | 本地连接方式 |
|---|---|
| MySQL | CPU ECS 公网地址 `8.154.36.87:13306` |
| 数据库 | `mmdp_db` |
| OSS Endpoint | `https://oss-cn-hangzhou.aliyuncs.com` |
| OSS Region | `cn-hangzhou` |
| OSS Bucket | `mmdp-test` |

数据链路：

```text
本地 Backend ──公网──> CPU ECS MySQL
本地 Backend ──HTTPS──> Aliyun OSS
本地 Worker ──localhost──> 本地 Backend
本地 Worker ──HTTPS──> Aliyun OSS
```

本地联调的网络前提：

- CPU ECS 安全组和 MySQL 访问策略允许开发机访问公网端口 `13306`；
- 开发机能够访问阿里云 OSS 的 HTTPS Endpoint；
- 后端和 Worker 使用相同的 OSS Bucket、Region 和凭证；
- Worker 启动前，本地 Backend 应已监听 `19021`。

## 8. 推荐启动顺序

1. 在 IntelliJ IDEA 中启动 Backend，确认 `http://localhost:19021` 可访问；
2. 在 PyCharm 中使用 `pose_env` 启动 Worker；
3. 在前端目录执行 `npm run dev`，访问 `http://localhost:5173`；
4. 仅在开发采集功能时，额外启动 `19022` 端口的 Collector。

Worker 启动时会向 Backend 注册 Pipeline。若先启动 Worker、后启动 Backend，Worker 需要等待后续心跳重试注册。

## 9. 配置文件与安全约定

本地配置文件：

```text
mmdp-backend\.env
mmdp-worker\.env
```

项目根目录 `.gitignore` 已忽略：

```text
.idea/
.env
.env.local
.env.*
node_modules/
dist/
target/
```

当前仓库未提交 `.idea` 项目设置，这意味着 IntelliJ IDEA 和 PyCharm 的 SDK、Interpreter、Maven Home、Working Directory 等配置需要在每台开发机上单独设置。

禁止提交以下内容：

- MySQL 密码；
- OSS AccessKey ID 和 AccessKey Secret；
- STS Role ARN 等云账号配置；
- 本地 `.env`；
- IDE 用户配置；
- Python 虚拟环境、`node_modules`、`target` 和 `dist` 等生成目录。

