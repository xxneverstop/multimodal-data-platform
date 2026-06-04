# MMDP 项目部署文档

## 一、部署架构

```
Internet → [服务器防火墙 :80/:443 放行] → [mmdp-nginx :80/:443]
                                                   |
              /api/* proxy_pass ──────────→ [mmdp-backend :19021]
                                                   |
              SPA 静态文件 ← /usr/share/nginx/html  |
                                                   ↓
                                           [mmdp-mysql :3306]
```

三个 Docker 容器通过内部 bridge 网络 `mmdp-net` 通信，仅 Nginx 暴露宿主机端口。Backend 不直接暴露到公网。

---

## 二、项目目录结构

```
项目根目录/
├── mmdp-frontend/          # Vue3 前端源码
├── mmdp-backend/           # Spring Boot 3 后端源码
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
```

### 3.3 一键打包（推荐）

双击项目根目录的 `copy-deploy.bat`，自动完成：

1. 后端 `mvn clean package -DskipTests`
2. 前端 `npm run build`
3. 复制 JAR → `deploy/backend/mmdp-backend.jar`
4. 复制 `dist/` → `deploy/frontend/dist/`
5. 复制 `schema.sql` → `deploy/initdb/schema.sql`

> `.env.example` 文件如缺失，可在 `deploy/` 下手动创建。内容参见第四章节。

---

## 四、环境变量配置

### 4.1 .env 文件模板

在 `deploy/` 目录下创建 `.env`，格式如下：

```ini
# ============================================
# MMDP Deploy Environment Variables
# ============================================

# Database（Docker 内部网络，hostname 固定为 mmdp-mysql）
MMDP_DB_URL=jdbc:mysql://mmdp-mysql:3306/mmdp_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
MMDP_DB_USERNAME=root
MMDP_DB_PASSWORD=<你的强密码>

# Storage：阿里云 OSS
MMDP_STORAGE_DEFAULT_PROVIDER=OSS
MMDP_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
MMDP_OSS_ACCESS_KEY_ID=<你的AccessKey ID>
MMDP_OSS_ACCESS_KEY_SECRET=<你的AccessKey Secret>
MMDP_OSS_BUCKET=<你的Bucket名称>
MMDP_OSS_REGION=cn-hangzhou
```

### 4.2 Git 管理策略

| 文件 | 是否提交 | 说明 |
|------|----------|------|
| `.env` | ❌ 不提交 | 含真实密钥，已在 `.gitignore` 中排除 |
| `.env.example` | ✅ 提交 | 仅模板占位符，便于团队同步 |
| `backend/*.jar` | ❌ 不提交 | 构建产物 |
| `frontend/dist/` | ❌ 不提交 | 构建产物 |

---

## 五、服务器部署

### 5.1 服务器要求

- Ubuntu 22.04/24.04
- 已安装 Docker（安装命令见 5.2）
- 磁盘空间 ≥ 10GB
- 创建部署目录：`mkdir -p /data/mmdp`

### 5.2 安装 Docker（新服务器首次）

```bash
# 一键安装 Docker
curl -fsSL https://get.docker.com | bash

# 启动并设置开机自启
systemctl enable docker --now

# 验证
docker --version
docker compose version
```

### 5.3 配置 Docker 镜像加速（国内服务器必做）

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

### 5.4 上传部署包

```bash
# 本地执行（替换 <服务器IP> 和密钥文件路径）
scp -i your-key.pem -r deploy/ root@<服务器IP>:/data/mmdp/
```

### 5.5 启动服务

```bash
cd /data/mmdp/deploy

# 编辑 .env 填入真实密钥（如果还未配置）
vim .env

# 首次启动（拉取镜像 + 构建 + 后台运行）
docker compose up -d --build
```

首次启动流程：
1. 拉取 `mysql:8.4.2`、`nginx:1.27-alpine` 镜像
2. 构建 `mmdp-backend` 镜像
3. MySQL 容器启动 → 创建 `mmdp_db` 库 → 自动执行 `schema.sql` 建表
4. 等待 MySQL 健康检查通过 → Backend 启动（连接 MySQL）
5. Nginx 启动，绑定 80/443 端口

### 5.6 检查运行状态

```bash
# 三个容器均应为 Up
docker compose ps

# 查看所有日志
docker compose logs -f

# 单独查看后端日志
docker compose logs -f backend
```

期望输出：
```
NAME           STATUS
mmdp-mysql     Up (healthy)
mmdp-backend   Up
mmdp-nginx     Up
```

---

## 六、公网访问配置

### 6.1 阿里云安全组（必须操作）

1. 登录 [阿里云 ECS 控制台](https://ecs.console.aliyun.com/)
2. 进入 **安全组** → 找到实例关联的安全组 → **配置规则**
3. **入方向** 添加以下规则：

| 协议 | 端口 | 来源 | 说明 |
|------|------|------|------|
| TCP | 80 | 0.0.0.0/0 | HTTP 公网访问 |
| TCP | 443 | 0.0.0.0/0 | HTTPS（预留） |

> ⚠️ **端口 13306（MySQL）不要开放 `0.0.0.0/0`**。如需 Navicat 远程连接数据库，使用 SSH 隧道（见 7.4 章节）。

### 6.2 验证公网访问

```bash
# 服务器本地验证
curl http://localhost/          # 应返回 SPA 的 index.html
curl http://localhost/api/tasks # 应返回 JSON 数据

# 浏览器访问
http://<服务器公网IP>/
```

---

## 七、运维操作

> 以下所有命令默认在 `/data/mmdp/deploy` 目录下执行。

---

### 7.1 服务启停控制

#### 启动服务

```bash
cd /data/mmdp/deploy

# 启动（如果已构建过，不需要 --build）
docker compose up -d

# 启动并强制重建镜像（代码/JAR 更新后必须加）
docker compose up -d --build

# 启动单个服务
docker compose up -d backend
docker compose up -d nginx
```

#### 停止服务

```bash
# 停止所有服务（容器停止，数据不丢失）
docker compose stop

# 停止单个服务
docker compose stop backend
docker compose stop nginx
docker compose stop mmdp-mysql
```

#### 关闭服务（删除容器）

```bash
# 停止并删除所有容器（MySQL 数据文件保留在 ./mysql-data，不丢失）
docker compose down

# 停止并删除容器 + 网络（同上，数据不丢）
docker compose down -v

# ⚠️ 彻底删除容器+网络+数据卷（MySQL 数据丢失！）
docker compose down -v && rm -rf mysql-data/
```

#### 重启服务

```bash
# 重启所有服务
docker compose restart

# 重启单个服务（修改 .env 后只需重启 backend）
docker compose restart backend

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
| `docker compose down -v` | 停止并删除容器、网络、匿名卷 | ✅ 保留（具名卷和宿主机目录不受影响） |
| `rm -rf mysql-data/` | 删除数据库物理文件 | ❌ 彻底删除 |

---

### 7.2 运行状态监测

#### 7.2.1 容器状态一览

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
mmdp-nginx     Up                 0.0%    8MiB     12MB / 10MB
```

#### 7.2.2 实时资源监控

```bash
# 实时刷新 CPU / 内存 / 网络（Ctrl+C 退出）
docker stats

# 查看宿主机整体资源
htop          # CPU + 内存
df -h         # 磁盘使用
free -h       # 内存使用
```

#### 7.2.3 服务健康检查

```bash
# MySQL 是否存活
docker exec mmdp-mysql mysqladmin ping -uroot -p<密码>

# Backend 是否响应
curl http://localhost/api/tasks

# Nginx 是否正常
curl -I http://localhost/
```

#### 7.2.4 查看日志

```bash
# 实时跟踪所有容器日志（Ctrl+C 退出）
docker compose logs -f

# 只看某个服务
docker compose logs -f backend
docker compose logs -f nginx
docker compose logs -f mmdp-mysql

# 查看最近 100 行
docker compose logs --tail 100 backend

# 查看最近 30 分钟的日志
docker compose logs --since 30m backend

# 查看特定时间段的日志
docker compose logs --since 2026-06-02T10:00:00 --until 2026-06-02T11:00:00 backend
```

---

### 7.3 访问情况监测

#### 7.3.1 实时查看谁在访问（Nginx 访问日志）

> ⚠️ **关键说明**：Nginx 官方 Docker 镜像将访问日志输出到 `stdout`（Docker 日志系统），**不写入文件**。因此不能 `cat /var/log/nginx/access.log`，必须用 `docker logs` 来查看。

```bash
# 实时跟踪 HTTP 访问记录（最直观，Ctrl+C 退出）
docker logs -f mmdp-nginx

# 只看最近 200 条访问记录
docker logs mmdp-nginx --tail 200

# 只看访问日志（过滤掉启动信息）
docker logs mmdp-nginx --tail 500 2>&1 | grep -E '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+'
```

每条访问记录格式：
```
192.168.1.1 - - [02/Jun/2026:10:30:45 +0800] "GET /api/tasks HTTP/1.1" 200 1234 "-" "Mozilla/5.0..."
```
解读：`客户端IP - 时间 - 请求方法 URL - 状态码 - 响应大小 - User-Agent`

#### 7.3.2 统计访问量和常见指标

> 以下命令中 `docker logs mmdp-nginx` 将日志输出到终端，再由宿主机 `awk`/`sort`/`grep` 处理。

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

#### 7.3.3 服务器整体网络流量

```bash
# 查看端口 80 的连接情况
ss -tunap | grep :80

# 查看当前活跃的 HTTP 连接数
ss -tn state established '( sport = :80 or dport = :80 )' | wc -l

# 实时网卡流量（需安装 nload）
nload eth0

# 或使用 iftop（需安装）
iftop -i eth0
```

#### 7.3.4 配置 Nginx 详细日志格式（可选）

默认日志格式已包含基本信息。如需更详细的格式（含请求耗时、上游响应时间等），编辑 `deploy/nginx/nginx.conf`：

```nginx
# 在 http 块中定义详细日志格式
log_format detailed '$remote_addr - $remote_user [$time_local] '
                    '"$request" $status $body_bytes_sent '
                    '"$http_referer" "$http_user_agent" '
                    'rt=$request_time uct="$upstream_connect_time" '
                    'uht="$upstream_header_time" urt="$upstream_response_time"';

# 在 server 块中启用（输出到 stdout，仍通过 docker logs 查看）
access_log /dev/stdout detailed;
```

> 修改后执行 `docker compose restart nginx` 生效。日志仍然通过 `docker logs mmdp-nginx` 查看。

#### 7.3.5 将日志持久化到宿主机文件（可选）

如果希望日志保留更长时间、方便离线分析，可以将 nginx 日志写入宿主机目录：

1. 修改 `docker-compose.yml`，在 `mmdp-nginx` 的 `volumes` 中添加：
   ```yaml
   - ./nginx/logs:/var/log/nginx
   ```

2. 修改 `nginx.conf`，将 access_log 指向文件：
   ```nginx
   access_log /var/log/nginx/access.log detailed;
   ```

3. 重建容器：
   ```bash
   docker compose up -d --build nginx
   ```

4. 之后即可直接在宿主机上操作文件：
   ```bash
   tail -f /data/mmdp/deploy/nginx/logs/access.log
   cat /data/mmdp/deploy/nginx/logs/access.log | awk '{print $1}' | sort -u | wc -l
   ```

#### 7.3.6 Nginx 安全防护规则

公网服务必然遭遇自动扫描器探测。Nginx 已内置以下拦截规则，阻止常见的敏感文件泄露尝试：

```nginx
# 阻断敏感文件后缀
location ~* \.(env|git|svn|hg|bzr|cvs|DS_Store|htpasswd|htaccess)$ {
    return 444;
}

# 阻断备份/临时文件后缀
location ~* \.(sql|log|bak|backup|old|orig|save|swp|tmp|copy|test)$ {
    return 444;
}

# 阻断隐藏文件（以 . 开头的文件/目录名）
location ~* /\. {
    return 444;
}
```

> **444 状态码**是 Nginx 特有行为——直接断开 TCP 连接，不返回任何 HTTP 响应。攻击者连错误信息都拿不到。

验证拦截是否生效：

```bash
# 这些请求应该被 444 阻断（无响应或连接断开）
curl -v http://localhost/.env 2>&1 | grep -E "< HTTP|Connection refused|Empty reply"
curl -v http://localhost/.git/config 2>&1 | grep -E "< HTTP|Connection refused|Empty reply"
curl -v http://localhost/backup.sql 2>&1 | grep -E "< HTTP|Connection refused|Empty reply"

# 正常请求不受影响
curl -o /dev/null -w "%{http_code}" http://localhost/
curl -o /dev/null -w "%{http_code}" http://localhost/api/tasks
```

---

### 7.4 更新部署

| 场景 | 操作 |
|------|------|
| 前端更新 | 本地 `npm run build` → 更新 `deploy/frontend/dist/` → 上传覆盖 → `docker exec mmdp-nginx nginx -s reload` |
| 后端更新 | 本地 `mvn package` → 更新 `deploy/backend/mmdp-backend.jar` → 上传覆盖 → `docker compose up -d --build backend` |
| 配置文件更新 | 修改 `docker-compose.yml`/`nginx.conf`/`Dockerfile` → 上传覆盖 → `docker compose up -d --build` |
| 环境变量更新 | 服务器 `vim .env` → `docker compose restart backend` |

---

### 7.5 自启动配置

Docker 容器已配置 `restart: unless-stopped`，服务器重启后容器会自动恢复。

```bash
# 验证 Docker 和容器自启是否生效
systemctl is-enabled docker    # 应为 enabled
docker compose ps              # 重启后检查容器状态

# 查看容器重启次数
docker ps --format "table {{.Names}}\t{{.Status}}"
# Status 列显示 "Up XX seconds" 说明刚重启过
```

---

### 7.6 MySQL 操作

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

### 7.7 Navicat 远程连接（SSH 隧道，推荐）

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

## 八、关键配置说明

### 8.1 docker-compose.yml 要点

| 配置项 | 取值 | 说明 |
|--------|------|------|
| MySQL 镜像 | `mysql:8.4.2` | 固定版本，避免升级引入兼容问题 |
| MySQL 端口 | `13306:3306` | 宿主机 13306 → 容器 3306，供调试/Navicat隧道使用 |
| MySQL 健康检查 | `mysqladmin ping` | 30s 超时窗口 + 5 次重试，确保 Backend 启动时数据库已就绪 |
| `MYSQL_ROOT_HOST` | `%` | 允许容器间 root 远程连接 |
| Backend 依赖 | `condition: service_healthy` | MySQL 健康后才启动，非仅等容器启动 |
| Nginx 端口 | `80:80` / `443:443` | 绑定宿主机所有网卡接口 |
| Nginx 挂载 | `:ro`（只读） | 配置文件和前端文件设为只读，防篡改 |
| `restart` | `unless-stopped` | 宿主机重启后容器自动恢复 |
| 数据持久化 | `./mysql-data` | MySQL 数据文件挂载到宿主机，容器删除不丢数据 |

### 8.2 nginx.conf 要点

| 配置 | 值 | 说明 |
|------|-----|------|
| SPA 路由 | `try_files $uri $uri/ /index.html` | Vue Router history 模式，非文件路径返回 SPA 壳 |
| API 代理 | `http://mmdp-backend:19021` | Docker 内部 DNS 解析服务名 |
| 上传限制 | `client_max_body_size 200m` | 与后端 `spring.servlet.multipart.max-file-size` 匹配 |
| 超时 | `proxy_read_timeout 300s` | 大文件上传不中断 |
| 安全拦截 | `return 444` × 3 条规则 | 阻断 `.env`/`.git`/`.sql`/`备份文件`/隐藏文件探测，直接断开 TCP 连接 |

### 8.3 数据目录

| 宿主机路径 | 容器内路径 | 说明 |
|------------|------------|------|
| `./mysql-data` | `/var/lib/mysql` | MySQL 数据文件 |
| `./initdb` | `/docker-entrypoint-initdb.d` | 首次启动自动执行的 SQL |

---

## 九、故障排查

| 现象 | 可能原因 | 解决方法 |
|------|----------|----------|
| 浏览器访问超时 | 安全组未开放 80 端口 | 阿里云控制台添加安全组规则 |
| `docker compose up` 拉取镜像超时 | Docker Hub 国内不可达 | 配置镜像加速器（见 5.3） |
| nginx 502 Bad Gateway | Backend 未启动或异常 | `docker compose logs backend` 查看错误 |
| Backend 启动后立即退出 | MySQL 连接失败 | 等 MySQL healthy 后 Backend 自动重试；检查 `.env` 密码 |
| MySQL 拒绝 root 连接 | 密码不匹配 | 确认 `MYSQL_ROOT_PASSWORD` 与 `.env` 中 `MMDP_DB_PASSWORD` 一致 |
| 静态页面 404 | `dist/` 未挂载或为空 | 检查 `deploy/frontend/dist/index.html` 是否存在 |
| API 返回 404 | nginx 代理未生效 | `docker compose logs nginx` 确认配置加载成功 |
| 端口冲突 | 宿主机 80/443 被占用 | `lsof -i :80` 查看占用进程，停止或改端口 |
| 数据库表为空 | schema.sql 未执行 | 删除 `./mysql-data` 目录重新启动（注意备份） |

---

## 十、快速重装

如需完全重置（清空数据从头开始）：

```bash
cd /data/mmdp/deploy

# 停止并删除容器、网络
docker compose down -v

# 清除所有数据（谨慎！）
rm -rf mysql-data/

# 重新启动
docker compose up -d --build
```
