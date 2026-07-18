> 保存于 2026-07-02 14:55

# .env 配置管理：从混乱到清晰

## 你眼前的真实困境

先看一下 MMDP 项目目前的 `.env` 分布：

| 文件 | 内容 | 含真实密钥？ |
|------|------|-------------|
| `mmdp-backend/.env` | 后端本地开发用，连开发服务器数据库 | ✅ OSS 密钥 + 数据库密码 |
| `deploy/.env` | Docker Compose 部署用 | ✅ OSS 密钥 + 数据库密码 |
| `mmdp-backend/.env.example` | 后端模板，占位符 | ❌ |
| `deploy/.env.example` | 部署模板，占位符 | ❌ |
| `mmdp-frontend/.env.development.example` | 前端模板 | ❌ |

一眼就能看出几个问题。同一个 OSS 的 AccessKey 出现在了两个地方（`mmdp-backend/.env` 和 `deploy/.env`）。数据库密码不一样——本地开发连的是服务器的数据库（`8.154.36.87:13306`），密码是 `Human+123`；服务器上 Docker 内部连的是容器里的 MySQL，密码是 `123123`。每次改一个配置，你要在两个文件里分别改，很容易漏。

更麻烦的是，`mmdp-backend/.env` 里的 OSS STS 配置（`MMDP_OSS_STS_ROLE_ARN`）在 `deploy/.env` 里没有，但在 `mmdp-backend/.env.example` 模板里又有占位符。说明模板没和实际使用同步好。

这些问题的根源不是你不小心，而是**没有一个明确的原则来区分"配置属于哪个环境"。**

## 核心原则：三层分离

在动手整改之前，先确立一个框架。这个框架只有三条规则，但能覆盖你遇到的所有情况。

### 第一层：敏感 vs 非敏感

**敏感信息**（密钥、密码、Token）永远不能出现在 Git 里。这一点你已经做到了——`.gitignore` 排除了 `.env`。但"不提交"只是第一步，你需要明确知道每个密钥当前是什么、在哪里用。

**非敏感信息**（端口号、服务名、超时时间）可以提交，但应该有个合理的默认值写在代码里。比如 Worker 的轮询间隔默认 5 秒，没必要写到 `.env` 里让每个环境都配一遍。

### 第二层：本地开发 vs 服务器部署

这是最容易搞混的地方。**本地开发时你在 IDE 里跑后端，服务器上是 Docker 容器里跑后端，两者的网络环境完全不同。**

本地开发时，后端连的是哪台 MySQL？可能是 `localhost:13306`（通过 SSH 隧道连服务器），也可能是远程 IP `8.154.36.87:13306`。Docker 部署时后端连的是同网络下的 `mmdp-mysql:3306`。这是两个完全不同的数据库地址。

所以 `.env` 必须分场景：`mmdp-backend/.env` 只管本地 IDE 开发；`deploy/.env` 只管 Docker Compose 部署。它们之间不共享、不互相引用。

### 第三层：环境区分（开发环境 vs 生产环境）

你目前只有一套服务器，所以"开发"和"生产"可能还没分那么清。但这个意识要先建立：如果你将来上了 CI/CD，dev 分支和 main 分支部署的可能需要不同的 Redis 缓存、不同的 OSS Bucket。到时候再拆就来不及了。

这三层总结成一句话：**同一个配置项，在不同的地方应该有不同的值，用不同的文件管理。**

## 本地开发怎么管

### Backend 的 .env

Spring Boot 有个很实用的机制：`spring.config.import: optional:file:.env[.properties]`（你的 `application.yml` 第 8 行已经配了）。这表示启动时自动读取同目录下的 `.env` 文件，作为环境变量注入，但文件不存在也不会报错。

这意味着你的 `mmdp-backend/.env` 可以这么写：

```ini
# mmdp-backend/.env — 本地 IDE 开发专用，不提交 Git
# 以下所有值仅对本地开发有效

# 本地连服务器数据库（通过 SSH 隧道或公网 IP）
MMDP_DB_URL=jdbc:mysql://8.154.36.87:13306/mmdp_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
MMDP_DB_USERNAME=root
MMDP_DB_PASSWORD=Human+123

# OSS — 测试 Bucket
MMDP_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
MMDP_OSS_ACCESS_KEY_ID=<你的AccessKey ID>
MMDP_OSS_ACCESS_KEY_SECRET=<你的AccessKey Secret>
MMDP_OSS_BUCKET=mmdp-test
MMDP_OSS_REGION=cn-hangzhou

# STS（前端直传 OSS 用）
MMDP_OSS_STS_ROLE_ARN=acs:ram::1661180291353519:role/mmdp-oss-upload-role
MMDP_OSS_STS_ROLE_SESSION_NAME=mmdp-direct-upload
MMDP_OSS_STS_DURATION_SECONDS=900
```

对应的模板文件（提交 Git）保持占位符：

```ini
# mmdp-backend/.env.example — 模板，提交 Git
MMDP_DB_URL=jdbc:mysql://<你的数据库地址>:3306/mmdp_db?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
MMDP_DB_USERNAME=root
MMDP_DB_PASSWORD=<数据库密码>
MMDP_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
MMDP_OSS_ACCESS_KEY_ID=<AccessKey ID>
MMDP_OSS_ACCESS_KEY_SECRET=<AccessKey Secret>
MMDP_OSS_BUCKET=<Bucket 名称>
MMDP_OSS_REGION=cn-hangzhou
MMDP_OSS_STS_ROLE_ARN=acs:ram::<账号UID>:role/<RAM 角色名>
MMDP_OSS_STS_ROLE_SESSION_NAME=mmdp-direct-upload
MMDP_OSS_STS_DURATION_SECONDS=900
```

新成员搭环境时，复制 `.env.example` 为 `.env`，填上真实值就行。

### Frontend 的 .env

前端的情况特殊一些。Vite 在编译时会把 `VITE_` 开头的环境变量**硬编码进打包产物**。这意味着前端 `.env` 里绝对不能放敏感信息——一旦 `npm run build`，密钥就被打进 `dist/` 的 JS 文件里，任何人都能在浏览器 DevTools 里看到。

你的 `mmdp-frontend/.env.development.example` 里只有一个配置项 `VITE_API_BASE_URL=/`，这是对的。开发时 Vite 自带代理（`/api` → `localhost:19021`），部署时 Nginx 做反向代理，前端根本不需要知道后端地址。继续保持这个做法。

### Worker 的配置

Worker 没有自己的 `.env` 文件，它完全依赖环境变量。本地开发时，你手动设置：

```bash
export MMDP_BACKEND_URL=http://localhost:19021
export MMDP_OSS_ACCESS_KEY_ID=<你的AccessKey ID>
# ... 其他变量
python main.py
```

Docker 部署时，这些变量从 `deploy/.env` 注入到容器。Worker 的 `config.py` 有硬编码的默认值（第 14-16 行），这其实是个隐患——如果哪天这些默认值泄露了，Git 历史里会永远留着。建议把默认值改成空字符串，强制依赖环境变量注入。

## 部署环境怎么管

### 现状分析

你服务器上 `/data/mmdp/deploy/.env` 的内容有真实密钥，这是必须的——容器需要这些变量才能启动。但问题出在这几个地方：

第一，`deploy/.env` 现在没提交 Git（正确），但它缺少 Worker 需要的变量。你在之前的部署文档里已经定义了 `MMDP_BACKEND_URL` 等变量，但服务器的 `.env` 文件没跟着更新。

第二，同一个 `.env` 同时被 Backend 和 Worker 两个容器使用。大部分变量（OSS 配置）确实应该共享，但有些变量（比如 `MMDP_DB_PASSWORD`）只有 Backend 需要，Worker 完全用不着。这是一种"过度共享"——Worker 拿到了它不需要的数据库密码。

### 整改方案：最小权限

不是要拆成多个文件（太重了），而是要有意识：**每个容器只拿到它真正需要的环境变量。**

`docker-compose.yml` 里已经体现了这个原则——你把环境变量分成了两组，Backend 有数据库变量，Worker 只有后端地址和 OSS 变量，MySQL 只有密码。检视一下这两个 service 的环境变量列表，确保没有多余的。

### .env 文件版本管理

部署环境的 `.env` 不建议提交 Git，但建议用另一种方式备份：在服务器上保留一个备份文件。

```bash
# 在服务器上做一次备份
cp /data/mmdp/deploy/.env /data/mmdp/deploy/.env.backup.$(date +%Y%m%d)
```

每次改配置前先备份，改完确认没问题再删旧备份。比版本控制简单，但够用。

## CI/CD 场景下的 .env 管理

这是最核心的问题。CI/CD 把代码的构建和部署流程从你的电脑搬到了 GitLab Runner 上。Runner 怎么拿到 `.env` 里的密钥？

答案是：**.env 文件不上传到 GitLab，密钥通过 GitLab CI/CD Variables 注入。**

### GitLab CI Variables 怎么设

在 GitLab 项目 → Settings → CI/CD → Variables，你可以添加变量。这些变量在 CI 流程运行时被注入为环境变量，Runner 能读到，但不会出现在任何代码或日志里。

关键的是区分两种变量类型：

**Variable（普通变量）**：Runner 直接能读到，适合非敏感的配置（比如 ACR 仓库地址、ECS IP）。

**Masked Variable（掩码变量）**：值不会出现在 CI 日志中。如果日志里不小心打印了这个值，GitLab 会自动替换为 `[MASKED]`。适合 OSS 的 AccessKey 等半敏感信息。

**Protected Variable（受保护变量）**：只对受保护分支（如 main）或受保护标签生效。适合生产环境的密钥，dev 分支的 CI 读不到。

**File Variable（文件变量）**：GitLab 把变量的值写成一个临时文件，Runner 通过读文件获取内容。适合 SSH 私钥、多行证书等长文本。

### MMDP 项目的 CI 变量规划

按"哪些东西 CI Runner 需要知道"来分类：

**Docker 构建阶段需要知道的**：其实什么都不需要。`docker build` 不注入环境变量，`.env` 里的值在容器启动时才生效。Docker 镜像里不应该包含密钥。

**推送到 ACR 需要知道的**：ACR 的用户名和密码。这两个用 Masked Variable。

**SSH 到 ECS 需要知道的**：ECS 的 IP（普通变量）、SSH 私钥（File Variable）。SSH 私钥是敏感信息，用 File Variable 类型存。

**服务器上的 docker compose 需要知道的**：`.env` 中的所有部署配置。但这些不需要经过 CI——它们已经在服务器上的 `/data/mmdp/deploy/.env` 里了。CI 流程只需要告诉服务器"去拉新镜像并重启"，不需要把数据库密码再传一遍。

这就是关键点：**CI Runner 不需要知道你的数据库密码和 OSS 密钥。** 这些值已经在服务器上了，Runner 只需要登录 ECS 然后执行命令的权利。

### 具体操作：CI/CD 的 .env 流

```
GitLab CI Variables（只有 CI 需要的东西）
  ├── ACR_USERNAME        → Runner 用来登录阿里云 ACR 推送镜像
  ├── ACR_PASSWORD        → 同上
  ├── ECS_HOST            → 告诉 Runner SSH 到哪里
  ├── ECS_USER            → SSH 用户名
  └── ECS_SSH_PRIVATE_KEY → SSH 私钥（File Variable）

服务器 /data/mmdp/deploy/.env（运行时需要的所有东西）
  ├── MMDP_DB_URL         → 容器连接数据库
  ├── MMDP_DB_PASSWORD    → 数据库密码
  ├── MMDP_OSS_*          → OSS 配置
  ├── MMDP_BACKEND_URL    → Worker 连 Backend
  └── ...                 → 其他运行时配置
```

Runner 登录 ECS 后，执行 `docker compose up -d`，Docker Compose 自己会读服务器上的 `.env` 文件。Runner 从头到尾没见过数据库密码。

### .gitlab-ci.yml 怎么写

在 CI 配置中引用这些变量时注意——普通变量直接 `$VAR_NAME`，File Variable 需要先读文件内容。

SSH 私钥的处理示例：

```yaml
deploy:
  stage: deploy
  before_script:
    # GitLab 会把 File Variable 的内容写到 $ECS_SSH_PRIVATE_KEY 指向的文件
    - mkdir -p ~/.ssh
    - cp "$ECS_SSH_PRIVATE_KEY" ~/.ssh/id_rsa
    - chmod 600 ~/.ssh/id_rsa
    - ssh-keyscan -H $ECS_HOST >> ~/.ssh/known_hosts
  script:
    - ssh $ECS_USER@$ECS_HOST "
        cd /data/mmdp/deploy &&
        docker login --username=$ACR_USERNAME --password=$ACR_PASSWORD $ACR_REGISTRY &&
        IMAGE_TAG=$CI_COMMIT_REF_SLUG docker compose pull &&
        IMAGE_TAG=$CI_COMMIT_REF_SLUG docker compose up -d
      "
```

注意 `$ECS_SSH_PRIVATE_KEY` 是一个文件路径（GitLab File Variable 自动生成的临时文件），不是私钥内容本身。用 `cp "$ECS_SSH_PRIVATE_KEY"` 而不是 `echo "$ECS_SSH_PRIVATE_KEY"`，这是最容易踩的坑。

## 一条安全底线

这篇文章一直在讲"怎么管配置"，最后说一条不容妥协的底线。

**永远不要在 CI 日志中打印环境变量。**

CI 日志可能被多人看到（团队成员、GitLab 管理员），而且通常会保存一段时间。你不小心在日志里暴露了密钥，跟把密钥写在 README 里没有区别。

具体做法：
- 不要 `echo $MMDP_DB_PASSWORD`
- 不要 `printenv` 打印全部环境变量
- 代码里如果 debug 模式打印了环境变量，上线前关掉
- 配置 GitLab 的 Masked Variable，它会在日志中自动替换敏感值

另外还有一个容易被忽略的点：你当前 `mmdp-worker/config.py` 的第 14-16 行硬编码了 OSS 的 AccessKey：

```python
OSS_ACCESS_KEY_ID = os.getenv("MMDP_OSS_ACCESS_KEY_ID", "")
OSS_ACCESS_KEY_SECRET = os.getenv("MMDP_OSS_ACCESS_KEY_SECRET", "")
```

这些默认值已经通过 Git 存在了代码历史中。如果你用的是公开仓库（GitHub `xxneverstop/multimodal-data-platform`），任何人都能在 commit 历史里看到这本应保密的 AccessKey。你需要做两件事：

第一，立即去阿里云 RAM 控制台禁用或删除这个 AccessKey，换一个新的。第二，把默认值改成空字符串：

```python
OSS_ACCESS_KEY_ID = os.getenv("MMDP_OSS_ACCESS_KEY_ID", "")
OSS_ACCESS_KEY_SECRET = os.getenv("MMDP_OSS_ACCESS_KEY_SECRET", "")
```

这样部署时如果忘记设环境变量，Worker 启动会直接报错"缺少必要环境变量"（`config.py` 里的 `validate()` 函数已经做了这个检查），而不是静默地用了一个硬编码的旧密钥。

## 整改清单

把这些散落各处的点收拢成一个可操作的列表：

1. **[ ] 轮换密钥**：去阿里云 RAM 控制台删除硬编码在 `mmdp-worker/config.py` 里的 AccessKey，创建新的
2. **[ ] 清理 config.py 默认值**：把 Worker 配置中的 OSS 密钥默认值改为空字符串
3. **[ ] 统一 .env.example**：确保 `mmdp-backend/.env.example` 和 `deploy/.env.example` 包含所有需要配置的变量（目前 `deploy/.env.example` 缺少 Worker 相关变量）
4. **[ ] 服务器 .env 补充**：在服务器的 `/data/mmdp/deploy/.env` 中补上 Worker 需要的变量（`MMDP_BACKEND_URL`、`MMDP_WORKER_WORK_DIR`、`MMDP_WORKER_POLL_INTERVAL`）
5. **[ ] 服务器备份 .env**：在服务器上执行 `cp /data/mmdp/deploy/.env /data/mmdp/deploy/.env.backup.$(date +%Y%m%d)`
6. **[ ] 团队文档**：把 `.env.example` 的位置和用法写入项目 README，新人搭环境知道复制哪几个文件
7. **[ ] CI 变量配置**：在 GitLab 中添加必要的 CI Variables（ACR 凭证、ECS SSH），注意区分 Protected 和 Masked
