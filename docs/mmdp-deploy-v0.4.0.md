# MMDP 项目部署文档 v0.4.0

> **版本变更说明**（相对于 v0.3.0）：
> - 新增 **Python Worker 容器**，负责领取并执行 Pipeline 处理任务（图像转码、IMU 对齐、运动指标计算等）
> - 新增 `deploy/worker/` 目录，包含 Worker Dockerfile 与源码
> - `.env` 新增 Worker 相关环境变量
> - `docker-compose.yml` 新增 `mmdp-worker` 服务

---

## 一、部署架构

```
Internet → [服务器防火墙 :80/:443 放行] → [mmdp-nginx :80/:443]
                                                   |
              /api/* proxy_pass ──────────→ [mmdp-backend :19021]
                                                   |
              SPA 静态文件 ← /usr/share/nginx/html  |
                                                   ↓
                                           [mmdp-mysql :3306]
                                                   ↑
              /api/worker/jobs/* ←──────── [mmdp-worker] ──→ [OSS 对象存储]
```

四个 Docker 容器通过内部 bridge 网络 `mmdp-net` 通信，仅 Nginx 暴露宿主机端口。Backend、Worker 不直接暴露到公网。

**容器职责**：

| 容器 | 镜像 | 职责 |
|------|------|------|
| `mmdp-mysql` | `mysql:8.4.2` | 数据库，存储所有业务数据 |
| `mmdp-backend` | `mmdp-backend:latest`（自构建） | Spring Boot 3 后端，提供 REST API |
| `mmdp-nginx` | `nginx:1.27-alpine` | 反向代理 + SPA 静态文件服务 + 安全拦截 |
| `mmdp-worker` | `mmdp-worker:latest`（自构建） | Python Worker，轮询领取处理任务，调用 Pipeline 执行 |

---

## 二、项目目录结构

```
项目根目录/
├── mmdp-frontend/          # Vue3 前端源码
├── mmdp-backend/           # Spring Boot 3 后端源码
├── mmdp-worker/            # Python Worker 源码
├── docs/                   # 项目文档
├── copy-deploy.bat         # 一键构建脚本（Windows）
└── deploy/                 # 部署物料目录（上传至服务器 /data/mmdp）
    ├── .env                # 🔒 真实环境变量（不提交 Git）
    ├── .env.example        # 📋 环境变量模板（提交 Git）
    ├── docker-compose.yml  # 容器编排配置
    ├── backend/
    │   ├── Dockerfile      # Java 21 JRE 镜像
    │   └── mmdp-backend.jar
    ├── frontend/
    │   └── dist/           # 前端构建产物
    ├── worker/
    │   ├── Dockerfile      # Python 3.10 镜像 + ffmpeg
    │   └── src/            # Worker 源码（main.py + config.py + pipelines/）
    ├── nginx/
    │   └── nginx.conf      # SPA 路由 + /api 反向代理
    └── initdb/
        └── schema.sql      # 数据库表结构（MySQL 首次启动自动执行）
```

---

## 三、本地打包

### 3.1 前置条件

- JDK 21
- Maven 3.9+（`D:\software\apache-maven-3.9.12`）
- Node.js + npm
- Python 3.10+（仅用于本地验证 Worker，打包阶段无需安装依赖）
- Docker Desktop（用于本地验证，可选）

### 3.2 手动打包

```bash
# 后端
cd mmdp-backend
mvn clean package -DskipTests
# 产物：target/mmdp-backend-1.0.0-SNAPSHOT.jar

# 前端
cd mmdp-frontend
npm run build
# 产物：dist/

# Worker（只需复制源码，pip 依赖由 Docker 构建时安装）
# 无需本地构建，copy-deploy.bat 自动复制
```

### 3.3 一键打包（推荐）

双击项目根目录的 `copy-deploy.bat`，自动完成：

1. 后端 `mvn clean package -DskipTests`
2. 前端 `npm run build`
3. 复制 JAR → `deploy/backend/mmdp-backend.jar`
4. 复制 `dist/` → `deploy/frontend/dist/`
5. 复制 `schema.sql` → `deploy/initdb/schema.sql`
6. 复制 `mmdp-worker/` 源码 → `deploy/worker/src/`

> `.env.example` 文件如缺失，可在 `deploy/` 下手动创建。内容参见第四章节。

---

## 四、环境变量配置

### 4.1 .env 文件模板

在 `deploy/` 目录下创建 `.env`，格式如下：

```ini
# ============================================
# MMDP Deploy Environment Variables (v0.4.0)
# ============================================

# ---- Database（Docker 内部网络，hostname 固定为 mmdp-mysql）----
MMDP_DB_URL=jdbc:mysql://mmdp-mysql:3306/mmdp_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
MMDP_DB_USERNAME=root
MMDP_DB_PASSWORD=<你的强密码>

# ---- Storage：阿里云 OSS（Backend 与 Worker 共享）----
MMDP_STORAGE_DEFAULT_PROVIDER=OSS
MMDP_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
MMDP_OSS_ACCESS_KEY_ID=<你的AccessKey ID>
MMDP_OSS_ACCESS_KEY_SECRET=<你的AccessKey Secret>
MMDP_OSS_BUCKET=<你的Bucket名称>
MMDP_OSS_REGION=cn-hangzhou

# ---- Python Worker ----
# 后端地址（Docker 内部网络，容器名 mmdp-backend，端口 19021）
MMDP_BACKEND_URL=http://mmdp-backend:19021
# Worker 本地临时工作目录（容器内路径，一般无需修改）
MMDP_WORKER_WORK_DIR=/tmp/mmdp-worker
# 轮询间隔（秒），默认 5
MMDP_WORKER_POLL_INTERVAL=5
```

### 4.2 环境变量速查

| 变量 | 使用者 | 说明 |
|------|--------|------|
| `MMDP_DB_URL` | Backend | JDBC 连接串，hostname 固定 `mmdp-mysql` |
| `MMDP_DB_USERNAME` | Backend | 数据库用户名 |
| `MMDP_DB_PASSWORD` | Backend + MySQL | 数据库密码，MySQL 容器启动时用同样密码 |
| `MMDP_STORAGE_DEFAULT_PROVIDER` | Backend | 存储提供商，默认 `OSS` |
| `MMDP_OSS_ENDPOINT` | Backend + Worker | OSS 端点地址 |
| `MMDP_OSS_ACCESS_KEY_ID` | Backend + Worker | OSS AccessKey ID |
| `MMDP_OSS_ACCESS_KEY_SECRET` | Backend + Worker | OSS AccessKey Secret |
| `MMDP_OSS_BUCKET` | Backend + Worker | OSS Bucket 名称 |
| `MMDP_OSS_REGION` | Backend | OSS 区域 |
| `MMDP_BACKEND_URL` | Worker | 后端 API 地址，Docker 内部用 `http://mmdp-backend:19021` |
| `MMDP_WORKER_WORK_DIR` | Worker | 临时工作目录（容器内），默认 `/tmp/mmdp-worker` |
| `MMDP_WORKER_POLL_INTERVAL` | Worker | 任务轮询间隔（秒），默认 `5` |

### 4.3 Git 管理策略

| 文件 | 是否提交 | 说明 |
|------|----------|------|
| `.env` | ❌ 不提交 | 含真实密钥，已在 `.gitignore` 中排除 |
| `.env.example` | ✅ 提交 | 仅模板占位符，便于团队同步 |
| `backend/*.jar` | ❌ 不提交 | 构建产物 |
| `frontend/dist/` | ❌ 不提交 | 构建产物 |
| `worker/src/` | ❌ 不提交 | 从源码目录复制的构建中间物 |

---

## 五、容器配置详解

### 5.1 docker-compose.yml（v0.4.0 完整版）

```yaml
services:
  mmdp-mysql:
    image: mysql:8.4.2
    container_name: mmdp-mysql
    restart: unless-stopped
    environment:
      MYSQL_ROOT_PASSWORD: ${MMDP_DB_PASSWORD}
      MYSQL_DATABASE: mmdp_db
      MYSQL_ROOT_HOST: "%"
    ports:
      - "13306:3306"
    volumes:
      - ./mysql-data:/var/lib/mysql
      - ./initdb:/docker-entrypoint-initdb.d
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MMDP_DB_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 30s
    networks:
      - mmdp-net

  mmdp-backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    image: mmdp-backend:latest
    container_name: mmdp-backend
    restart: unless-stopped
    depends_on:
      mmdp-mysql:
        condition: service_healthy
    environment:
      MMDP_DB_URL: ${MMDP_DB_URL}
      MMDP_DB_USERNAME: ${MMDP_DB_USERNAME}
      MMDP_DB_PASSWORD: ${MMDP_DB_PASSWORD}
      MMDP_STORAGE_DEFAULT_PROVIDER: ${MMDP_STORAGE_DEFAULT_PROVIDER}
      MMDP_OSS_ENDPOINT: ${MMDP_OSS_ENDPOINT}
      MMDP_OSS_ACCESS_KEY_ID: ${MMDP_OSS_ACCESS_KEY_ID}
      MMDP_OSS_ACCESS_KEY_SECRET: ${MMDP_OSS_ACCESS_KEY_SECRET}
      MMDP_OSS_BUCKET: ${MMDP_OSS_BUCKET}
      MMDP_OSS_REGION: ${MMDP_OSS_REGION}
    networks:
      - mmdp-net

  mmdp-worker:
    build:
      context: ./worker
      dockerfile: Dockerfile
    image: mmdp-worker:latest
    container_name: mmdp-worker
    restart: unless-stopped
    depends_on:
      - mmdp-backend
    environment:
      MMDP_BACKEND_URL: ${MMDP_BACKEND_URL}
      MMDP_OSS_ENDPOINT: ${MMDP_OSS_ENDPOINT}
      MMDP_OSS_ACCESS_KEY_ID: ${MMDP_OSS_ACCESS_KEY_ID}
      MMDP_OSS_ACCESS_KEY_SECRET: ${MMDP_OSS_ACCESS_KEY_SECRET}
      MMDP_OSS_BUCKET: ${MMDP_OSS_BUCKET}
      MMDP_WORKER_WORK_DIR: ${MMDP_WORKER_WORK_DIR:-/tmp/mmdp-worker}
      MMDP_WORKER_POLL_INTERVAL: ${MMDP_WORKER_POLL_INTERVAL:-5}
    networks:
      - mmdp-net

  mmdp-nginx:
    image: nginx:1.27-alpine
    container_name: mmdp-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./frontend/dist:/usr/share/nginx/html:ro
    depends_on:
      - mmdp-backend
    networks:
      - mmdp-net

networks:
  mmdp-net:
    driver: bridge
```

### 5.2 Worker Dockerfile

在 `deploy/worker/Dockerfile`：

```dockerfile
FROM python:3.10-slim

# 安装系统依赖（ffmpeg 供视频处理 Pipeline 使用）
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /app

# 安装 Python 依赖
COPY src/requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# 复制 Worker 源码
COPY src/ /app/

# 确保工作目录存在
RUN mkdir -p /tmp/mmdp-worker

ENTRYPOINT ["python", "main.py"]
```

### 5.3 关键配置说明

#### docker-compose.yml 要点

| 配置项 | 取值 | 说明 |
|--------|------|------|
| MySQL 镜像 | `mysql:8.4.2` | 固定版本，避免升级引入兼容问题 |
| MySQL 端口 | `13306:3306` | 宿主机 13306 → 容器 3306，供调试/Navicat 隧道使用 |
| MySQL 健康检查 | `mysqladmin ping` | 30s 超时窗口 + 5 次重试，确保 Backend 启动时数据库已就绪 |
| `MYSQL_ROOT_HOST` | `%` | 允许容器间 root 远程连接 |
| Backend 依赖 | `condition: service_healthy` | MySQL 健康后才启动 |
| Worker 依赖 | `depends_on: mmdp-backend` | Backend 启动后再启动 Worker（不等待健康检查，Worker 内置轮询重试） |
| Worker 基础镜像 | `python:3.10-slim` | 轻量 Python 镜像 + ffmpeg |
| Nginx 端口 | `80:80` / `443:443` | 绑定宿主机所有网卡接口 |
| Nginx 挂载 | `:ro`（只读） | 配置文件和前端文件设为只读，防篡改 |
| `restart` | `unless-stopped` | 宿主机重启后容器自动恢复 |
| 数据持久化 | `./mysql-data` | MySQL 数据文件挂载到宿主机，容器删除不丢数据 |

#### nginx.conf 要点

| 配置 | 值 | 说明 |
|------|-----|------|
| SPA 路由 | `try_files $uri $uri/ /index.html` | Vue Router history 模式 |
| API 代理 | `http://mmdp-backend:19021` | Docker 内部 DNS 解析服务名 |
| 上传限制 | `client_max_body_size 200m` | 与后端匹配 |
| 超时 | `proxy_read_timeout 300s` | 大文件上传不中断 |
| 安全拦截 | `return 444` × 3 条规则 | 阻断敏感文件探测 |

#### 数据目录

| 宿主机路径 | 容器内路径 | 说明 |
|------------|------------|------|
| `./mysql-data` | `/var/lib/mysql` | MySQL 数据文件 |
| `./initdb` | `/docker-entrypoint-initdb.d` | 首次启动自动执行的 SQL |

---

## 六、服务器部署

### 6.1 服务器要求

- Ubuntu 22.04/24.04
- 已安装 Docker（安装命令见 6.2）
- 磁盘空间 ≥ 20GB（Worker 临时文件 + Pipeline 产物需要更多空间）
- 内存 ≥ 4GB（MySQL + JVM + Python Worker 同时运行）
- 创建部署目录：`mkdir -p /data/mmdp`

### 6.2 安装 Docker（新服务器首次）

```bash
# 一键安装 Docker
curl -fsSL https://get.docker.com | bash

# 启动并设置开机自启
systemctl enable docker --now

# 验证
docker --version
docker compose version
```

### 6.3 配置 Docker 镜像加速（国内服务器必做）

```bash
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "registry-mirrors": [
    "https://docker.1ms.run",
    "https://docker.xuanyuan.me"
  ]
}
EOF

sudo systemctl daemon-reload
sudo systemctl restart docker
```

> 阿里云 ECS 用户可前往 [cr.console.aliyun.com](https://cr.console.aliyun.com/cn-hangzhou/instances/mirrors) 获取专属加速地址，内网访问更快。

### 6.4 上传部署包

```bash
# 本地执行（替换 <服务器IP> 和密钥文件路径）
scp -i your-key.pem -r deploy/ root@<服务器IP>:/data/mmdp/
```

### 6.5 启动服务

```bash
cd /data/mmdp/deploy

# 编辑 .env 填入真实密钥（如果还未配置）
vim .env

# 首次启动（拉取镜像 + 构建 + 后台运行）
docker compose up -d --build
```

首次启动流程：
1. 拉取 `mysql:8.4.2`、`nginx:1.27-alpine`、`python:3.10-slim` 镜像
2. 构建 `mmdp-backend` 镜像（Java 21 JRE + JAR）
3. 构建 `mmdp-worker` 镜像（Python 3.10 + ffmpeg + pip 依赖 + 源码）
4. MySQL 容器启动 → 创建 `mmdp_db` 库 → 自动执行 `schema.sql` 建表
5. 等待 MySQL 健康检查通过 → Backend 启动（连接 MySQL）
6. Backend 启动后 → Worker 启动（开始轮询领取任务）
7. Nginx 启动，绑定 80/443 端口

### 6.6 检查运行状态

```bash
# 四个容器均应为 Up
docker compose ps

# 查看所有日志
docker compose logs -f

# 单独查看某服务日志
docker compose logs -f backend
docker compose logs -f worker
```

期望输出：
```
NAME           STATUS
mmdp-mysql     Up (healthy)
mmdp-backend   Up
mmdp-worker    Up
mmdp-nginx     Up
```

Worker 启动日志示例：
```
==================================================
mmdp-worker 启动
  后端: http://mmdp-backend:19021
  OSS: https://oss-cn-hangzhou.aliyuncs.com / mmdp-test
  工作目录: /tmp/mmdp-worker
  轮询间隔: 5s
==================================================
[pipeline] [OK] 注册 BUILD_PLAYBACK (图像序列 → MP4) <- build_playback.py
[pipeline] [OK] 注册 BUILD_STEREO_MP4 (双目图像 → 并排 MP4) <- build_stereo_mp4.py
[pipeline] [OK] 注册 STEREO_IMU_ALIGN (双目 IMU 时间对齐) <- stereo_imu_align.py
...
  已注册 7 个 Pipeline:
    - BUILD_PLAYBACK -- 图像序列 → MP4 v1.0.0 (依赖: ffmpeg)
    - BUILD_STEREO_MP4 -- 双目图像 → 并排 MP4 v1.0.0 (依赖: ffmpeg)
    ...
[manifest] 已生成 pipeline-manifest.json (7 个 Pipeline)
==================================================
```

---

## 七、公网访问配置

### 7.1 阿里云安全组（必须操作）

1. 登录 [阿里云 ECS 控制台](https://ecs.console.aliyun.com/)
2. 进入 **安全组** → 找到实例关联的安全组 → **配置规则**
3. **入方向** 添加以下规则：

| 协议 | 端口 | 来源 | 说明 |
|------|------|------|------|
| TCP | 80 | 0.0.0.0/0 | HTTP 公网访问 |
| TCP | 443 | 0.0.0.0/0 | HTTPS（预留） |

> ⚠️ **端口 13306（MySQL）不要开放 `0.0.0.0/0`**。如需 Navicat 远程连接数据库，使用 SSH 隧道（见 8.6 章节）。

### 7.2 验证公网访问

```bash
# 服务器本地验证
curl http://localhost/          # 应返回 SPA 的 index.html
curl http://localhost/api/tasks # 应返回 JSON 数据

# 浏览器访问
http://<服务器公网IP>/
```

---

## 八、运维操作

> 以下所有命令默认在 `/data/mmdp/deploy` 目录下执行。

---

### 8.1 服务启停控制

#### 启动服务

```bash
cd /data/mmdp/deploy

# 启动（如果已构建过，不需要 --build）
docker compose up -d

# 启动并强制重建镜像（代码/JAR/Worker 源码更新后必须加）
docker compose up -d --build

# 启动单个服务
docker compose up -d backend
docker compose up -d worker
docker compose up -d nginx
```

#### 停止服务

```bash
# 停止所有服务（容器停止，数据不丢失）
docker compose stop

# 停止单个服务
docker compose stop backend
docker compose stop worker
docker compose stop nginx
docker compose stop mmdp-mysql
```

#### 关闭服务（删除容器）

```bash
# 停止并删除所有容器（MySQL 数据文件保留在 ./mysql-data，不丢失）
docker compose down

# ⚠️ 彻底删除容器+网络+数据卷（MySQL 数据丢失！）
docker compose down -v && rm -rf mysql-data/
```

#### 重启服务

```bash
# 重启所有服务
docker compose restart

# 重启单个服务（修改 .env 后只需重启对应服务）
docker compose restart backend
docker compose restart worker

# 停止后重新启动
docker compose down && docker compose up -d
```

#### 启停对比速查

| 命令 | 效果 | 数据是否保留 |
|------|------|-------------|
| `docker compose stop` | 停止容器，保留容器实例 | ✅ 保留 |
| `docker compose start` | 启动已停止的容器 | ✅ 保留 |
| `docker compose restart` | 重启容器（= stop + start） | ✅ 保留 |
| `docker compose down` | 停止并删除容器、网络 | ✅ 保留（mysql-data/ 不受影响） |
| `rm -rf mysql-data/` | 删除数据库物理文件 | ❌ 彻底删除 |

---

### 8.2 运行状态监测

#### 8.2.1 容器状态一览

```bash
# 查看所有容器运行状态（最常用）
docker compose ps

# 更详细的容器信息
docker stats --no-stream
```

正常输出示例：
```
NAME           STATUS             CPU     MEM      NET I/O
mmdp-mysql     Up (healthy)       2.1%    380MiB   200kB / 50kB
mmdp-backend   Up                 0.8%    290MiB   15MB / 2MB
mmdp-worker    Up                 0.5%    120MiB   500kB / 300kB
mmdp-nginx     Up                 0.0%    8MiB     12MB / 10MB
```

#### 8.2.2 Worker 状态监测

```bash
# 查看 Worker 实时日志（观察轮询与任务执行）
docker compose logs -f worker

# 查看 Worker 最近 50 行日志
docker compose logs --tail 50 worker

# 检查 Worker 是否在正常运行（轮询中会打印 "."）
docker compose logs --tail 5 worker
# 正常输出：每 5 秒打印一个 "."

# 查看 Worker 处理的任务统计
docker compose logs worker 2>&1 | grep "\[OK\]" | wc -l     # 成功任务数
docker compose logs worker 2>&1 | grep "\[ERR\]" | wc -l    # 失败任务数
docker compose logs worker 2>&1 | grep "\[领取\]" | wc -l    # 领取任务数
```

#### 8.2.3 实时资源监控

```bash
# 实时刷新 CPU / 内存 / 网络（Ctrl+C 退出）
docker stats

# 查看宿主机整体资源
htop          # CPU + 内存
df -h         # 磁盘使用
free -h       # 内存使用
```

#### 8.2.4 服务健康检查

```bash
# MySQL 是否存活
docker exec mmdp-mysql mysqladmin ping -uroot -p<密码>

# Backend 是否响应
curl http://localhost/api/tasks

# Worker 进程是否在运行
docker exec mmdp-worker ps aux | grep python

# Nginx 是否正常
curl -I http://localhost/
```

#### 8.2.5 查看日志

```bash
# 实时跟踪所有容器日志（Ctrl+C 退出）
docker compose logs -f

# 只看某个服务
docker compose logs -f backend
docker compose logs -f worker
docker compose logs -f nginx
docker compose logs -f mmdp-mysql

# 查看最近 100 行
docker compose logs --tail 100 backend
docker compose logs --tail 100 worker

# 查看最近 30 分钟的日志
docker compose logs --since 30m backend

# 查看特定时间段的日志
docker compose logs --since 2026-07-02T10:00:00 --until 2026-07-02T11:00:00 worker
```

---

### 8.3 访问情况监测

#### 8.3.1 实时查看谁在访问（Nginx 访问日志）

> ⚠️ **关键说明**：Nginx 官方 Docker 镜像将访问日志输出到 `stdout`（Docker 日志系统），**不写入文件**。因此不能 `cat /var/log/nginx/access.log`，必须用 `docker logs` 来查看。

```bash
# 实时跟踪 HTTP 访问记录（最直观，Ctrl+C 退出）
docker logs -f mmdp-nginx

# 只看最近 200 条访问记录
docker logs mmdp-nginx --tail 200

# 只看访问日志（过滤掉启动信息）
docker logs mmdp-nginx --tail 500 2>&1 | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+'
```

#### 8.3.2 统计访问量和常见指标

```bash
# 统计总请求数
docker logs mmdp-nginx 2>&1 | grep -E '^[0-9]+\.' | wc -l

# 统计独立 IP 访问数
docker logs mmdp-nginx 2>&1 | awk '{print $1}' | sort -u | wc -l

# 统计每个 IP 的请求次数排名（Top 10）
docker logs mmdp-nginx 2>&1 | awk '{print $1}' | sort | uniq -c | sort -rn | head -10

# 统计 API 接口调用频率排名
docker logs mmdp-nginx 2>&1 | grep "/api/" | awk '{print $7}' | sort | uniq -c | sort -rn | head -20

# 统计状态码分布（200/404/500 各多少）
docker logs mmdp-nginx 2>&1 | awk '{print $9}' | grep -E '^[0-9]+$' | sort | uniq -c | sort -rn

# 统计错误请求（非 2xx/3xx）
docker logs mmdp-nginx 2>&1 | awk '$9 !~ /^[23][0-9][0-9]$/ && $9 ~ /^[0-9]+$/ {print $0}'
```

#### 8.3.3 服务器整体网络流量

```bash
# 查看端口 80 的连接情况
ss -tunap | grep :80

# 查看当前活跃的 HTTP 连接数
ss -tn state established '( sport = :80 or dport = :80 )' | wc -l

# 实时网卡流量（需安装 nload）
nload eth0
```

---

### 8.4 更新部署

| 场景 | 操作 |
|------|------|
| 前端更新 | 本地 `npm run build` → 更新 `deploy/frontend/dist/` → 上传覆盖 → `docker exec mmdp-nginx nginx -s reload` |
| 后端更新 | 本地 `mvn package` → 更新 `deploy/backend/mmdp-backend.jar` → 上传覆盖 → `docker compose up -d --build backend` |
| Worker 更新 | 本地更新 `mmdp-worker/` 源码 → 复制到 `deploy/worker/src/` → 上传覆盖 → `docker compose up -d --build worker` |
| 新增 Pipeline | 在 `mmdp-worker/pipelines/` 下新增 `.py` 文件 → 复制到 `deploy/worker/src/pipelines/` → 上传覆盖 → `docker compose up -d --build worker` |
| 配置文件更新 | 修改 `docker-compose.yml`/`nginx.conf`/`Dockerfile` → 上传覆盖 → `docker compose up -d --build` |
| 环境变量更新 | 服务器 `vim .env` → `docker compose restart backend` + `docker compose restart worker` |

---

### 8.5 自启动配置

Docker 容器已配置 `restart: unless-stopped`，服务器重启后容器会自动恢复。

```bash
# 验证 Docker 和容器自启是否生效
systemctl is-enabled docker    # 应为 enabled
docker compose ps              # 重启后检查容器状态

# 查看容器重启次数
docker ps --format "table {{.Names}}\t{{.Status}}"
```

---

### 8.6 MySQL 操作

```bash
# 进入 MySQL 命令行
docker exec mmdp-mysql mysql -uroot -p<密码> mmdp_db

# 查看所有表
docker exec mmdp-mysql mysql -uroot -p<密码> mmdp_db -e "SHOW TABLES;"

# 查看表结构
docker exec mmdp-mysql mysql -uroot -p<密码> mmdp_db -e "DESCRIBE data_file;"

# 查询记录数
docker exec mmdp-mysql mysql -uroot -p<密码> mmdp_db -e "SELECT COUNT(*) FROM data_asset;"

# 执行 DDL
docker exec mmdp-mysql mysql -uroot -p<密码> mmdp_db \
  -e "ALTER TABLE data_file ADD COLUMN xxx VARCHAR(32) NOT NULL DEFAULT '' COMMENT '说明' AFTER sha256;"

# 完整备份数据库
docker exec mmdp-mysql mysqldump -uroot -p<密码> mmdp_db > backup_$(date +%Y%m%d).sql

# 恢复数据库
docker exec -i mmdp-mysql mysql -uroot -p<密码> mmdp_db < backup.sql
```

---

### 8.7 Navicat 远程连接（SSH 隧道，推荐）

不在安全组中开放 13306 端口，通过 SSH 加密隧道访问：

1. Navicat 新建连接 → MySQL
2. **常规** 选项卡：
   - 主机：`localhost`，端口：`13306`
   - 用户名：`root`，密码：`.env` 中的 `MMDP_DB_PASSWORD`
3. **SSH** 选项卡（勾选"使用 SSH 隧道"）：
   - 主机：`<服务器公网IP>`，端口：`22`
   - 用户名：`root`
   - 认证方式：选择 `.pem` 密钥文件
4. 测试连接，保存

---

## 九、Worker 运维专题

### 9.1 Worker 工作流程

```
Worker 启动 → 打印已注册 Pipeline 列表 → 进入轮询循环
    ↓
POST /api/worker/jobs/claim  ← 每隔 POLL_INTERVAL 秒
    ↓ 有任务
下载 OSS 输入文件 → 执行对应 Pipeline → 上传产物到 OSS
    ↓                                  ↓ 成功
    POST /api/worker/jobs/{id}/success
    ↓ 失败
    POST /api/worker/jobs/{id}/failure
    ↓
清理临时文件（成功）/ 保留现场（失败）→ 继续轮询
```

### 9.2 Worker 配置调优

| 场景 | 建议配置 |
|------|----------|
| 开发调试 | `MMDP_WORKER_POLL_INTERVAL=3`（快速响应） |
| 生产环境 | `MMDP_WORKER_POLL_INTERVAL=5`（默认，平衡响应与资源） |
| 低负载 | `MMDP_WORKER_POLL_INTERVAL=10`（节省资源） |
| 大文件处理 | 确保宿主机 `/tmp` 或 `MMDP_WORKER_WORK_DIR` 有足够磁盘空间 |

### 9.3 查看已注册的 Pipeline 列表

```bash
# 查看 Worker 启动日志中的 Pipeline 注册信息
docker compose logs worker 2>&1 | grep "\[pipeline\]"

# 在 Worker 容器中列出 Pipeline
docker exec mmdp-worker python main.py --list-pipelines
```

### 9.4 Worker 临时文件管理

Worker 处理成功后会**自动清理**临时工作目录。处理失败时**保留现场**方便排查。

```bash
# 查看 Worker 容器内临时文件占用
docker exec mmdp-worker du -sh /tmp/mmdp-worker/

# 手动清理所有临时文件（谨慎）
docker exec mmdp-worker rm -rf /tmp/mmdp-worker/*
```

---

## 十、故障排查

| 现象 | 可能原因 | 解决方法 |
|------|----------|----------|
| 浏览器访问超时 | 安全组未开放 80 端口 | 阿里云控制台添加安全组规则 |
| `docker compose up` 拉取镜像超时 | Docker Hub 国内不可达 | 配置镜像加速器（见 6.3） |
| nginx 502 Bad Gateway | Backend 未启动或异常 | `docker compose logs backend` 查看错误 |
| Backend 启动后立即退出 | MySQL 连接失败 | 等 MySQL healthy 后 Backend 自动重试；检查 `.env` 密码 |
| MySQL 拒绝 root 连接 | 密码不匹配 | 确认 `MYSQL_ROOT_PASSWORD` 与 `.env` 中 `MMDP_DB_PASSWORD` 一致 |
| 静态页面 404 | `dist/` 未挂载或为空 | 检查 `deploy/frontend/dist/index.html` 是否存在 |
| API 返回 404 | nginx 代理未生效 | `docker compose logs nginx` 确认配置加载成功 |
| 端口冲突 | 宿主机 80/443 被占用 | `lsof -i :80` 查看占用进程，停止或改端口 |
| 数据库表为空 | schema.sql 未执行 | 删除 `./mysql-data` 目录重新启动（注意备份） |
| **Worker 启动后无反应** | Backend 未就绪 | Worker 会持续轮询，Backend 就绪后自动恢复；查看日志确认 `.` 在打印 |
| **Worker 日志全是 `.`** | 无待处理任务 | 正常现象，Worker 在等待后端分配任务 |
| **Worker 报 `Unknown pipeline`** | Pipeline ID 不匹配 | `docker exec mmdp-worker python main.py --list-pipelines` 确认已注册列表 |
| **Worker 报 OSS 下载失败** | OSS 密钥或网络问题 | 检查 `.env` 中 OSS 配置；确认 Worker 容器能访问外网 |
| **Worker 报 `No input files`** | 后端 claim 返回数据不完整 | 检查 Session 导入和数据资产是否正确关联 |
| **Worker 内存/磁盘耗尽** | 大文件处理 + 失败保留现场 | 清理 `/tmp/mmdp-worker/` 下旧目录；增加 `MMDP_WORKER_POLL_INTERVAL` |

---

## 十一、快速重装

如需完全重置（清空数据从头开始）：

```bash
cd /data/mmdp/deploy

# 停止并删除容器、网络
docker compose down

# 清除所有数据（谨慎！）
rm -rf mysql-data/

# 重新启动
docker compose up -d --build
```

---

## 十二、从 v0.3.0 升级指南

如果服务器上已有 v0.3.0 部署（仅 MySQL + Backend + Nginx），升级到 v0.4.0 的步骤：

### 12.1 本地准备

```bash
# 1. 更新 copy-deploy.bat（已包含 Worker 复制步骤），重新打包
#    双击 copy-deploy.bat

# 2. 确认 deploy/ 目录下新增了 worker/ 目录
ls deploy/worker/
# 应看到：Dockerfile  src/
```

### 12.2 服务器升级

```bash
# 1. 上传更新后的 deploy 目录（覆盖）
scp -i your-key.pem -r deploy/ root@<服务器IP>:/data/mmdp/

# 2. 更新 .env，添加 Worker 相关变量
cd /data/mmdp/deploy
vim .env
# 追加以下内容：
#   MMDP_BACKEND_URL=http://mmdp-backend:19021
#   MMDP_WORKER_WORK_DIR=/tmp/mmdp-worker
#   MMDP_WORKER_POLL_INTERVAL=5

# 3. 更新 docker-compose.yml（覆盖旧版），重建并启动
docker compose up -d --build

# 4. 验证四个容器均正常运行
docker compose ps
```

### 12.3 升级注意事项

- MySQL 数据文件（`mysql-data/`）**不会受影响**，数据库数据完整保留
- 升级过程中服务会有短暂中断（Backend + Worker 重建）
- 旧版 `docker-compose.yml` 不含 Worker 服务定义，必须覆盖为新版
- Worker 首次启动会自动生成 `pipeline-manifest.json`，无需手动干预
