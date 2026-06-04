# MMDP 阿里云资源管理与平台部署交接文档

## 文档目的

本文档用于帮助后续同学快速接手 MMDP 平台的云资源、权限体系、OSS 存储体系与部署流程。

建议阅读顺序：

1. 本文档：理解账号、权限、资源归属、部署边界与交接要点
2. [MMDP 项目部署文档](/D:/workspace/multimodal-data-platform/docs/mmdp-deploy.md:1)：按步骤执行部署
3. [mmdp-backend/README.md](/D:/workspace/multimodal-data-platform/mmdp-backend/README.md:1)：理解后端运行配置、SQL 与 OSS 变量

---

## 一、当前项目云端部署概览

### 1.1 当前部署形态

当前项目推荐部署在一台 Ubuntu 云服务器上，基于 Docker 与 Docker Compose 运行，部署目录固定为：

```text
/data/mmdp
```

当前推荐的服务组成：

- `nginx`：托管前端静态资源，并反向代理 `/api`
- `backend`：Spring Boot 后端服务
- `mysql`：MySQL 数据库
- `oss`：使用阿里云 OSS，不在服务器上部署 MinIO

### 1.2 当前技术栈与端口关系

- 前端：Vue 3 + Vite，本地构建为 `dist/`
- 后端：Spring Boot 3 + Java 21
- 数据库：MySQL
- 对象存储：阿里云 OSS
- 推荐公网入口：`80/443 -> nginx`
- 后端容器内部端口：`19021`
- MySQL 容器内部端口：`3306`

### 1.3 当前项目的实际上传链路

当前仓库内已存在两类 OSS 接入能力：

- 标准后端上传：浏览器把文件传给 Spring Boot，再由后端写入 OSS
- OSS 直传能力：后端可通过 STS 签发临时上传凭证，前端直传 OSS，再回平台登记

说明：

- 后端默认存储提供商已经是 `OSS`，见 [application.yml](/D:/workspace/multimodal-data-platform/mmdp-backend/src/main/resources/application.yml:1)
- 当前项目已经不再以 MinIO 作为正式部署目标
- 交接时需要明确当前环境到底使用“后端中转上传”还是“STS 直传 + 平台登记”，不要混淆

---

## 二、账号与权限体系

### 2.1 主账号

当前所有云资源归属于导师主账号。

包括但不限于：

- ECS
- OSS
- 域名
- SSL 证书
- RAM
- 账单

原则：

```text
主账号只负责资源归属
日常开发、部署、运维不要使用主账号
```

原因：

- 避免手机验证码依赖
- 避免主账号 AccessKey 泄漏
- 避免误删高权限资源

### 2.2 RAM 用户

当前平台管理员账号为：

```text
power-user-access
```

用途：

- 平台开发
- 平台部署
- 平台运维
- OSS 管理
- ECS 管理

日常操作全部使用该账号，不要直接使用导师主账号。

### 2.3 AccessKey 使用原则

Spring Boot 服务端应使用：

```text
RAM 用户 AccessKey
```

不要使用：

```text
主账号 AccessKey
```

原因：

- 主账号权限范围过大
- 一旦泄漏，风险不可控
- RAM 用户更易做权限审计与替换

### 2.4 新同学加入时的推荐做法

不要共享现有账号密码，推荐流程：

1. 创建新的 RAM 用户
2. 分配控制台登录权限
3. 分配与职责对应的策略
4. 如需程序访问，再单独发放 AccessKey
5. 交接结束后记录权限范围与负责人

---

## 三、阿里云资源清单与管理边界

### 3.1 ECS 服务器

当前服务器建议基线：

```text
Ubuntu 22.04
Docker
Docker Compose
```

部署根目录建议如下：

```text
/data/mmdp
├── deploy
├── mysql-data
├── logs
└── backup
```

说明：

- `deploy/`：上传部署物料
- `mysql-data/`：MySQL 宿主机持久化数据
- `logs/`：宿主机侧日志或导出日志
- `backup/`：数据库备份、配置备份、证书备份

### 3.2 OSS Bucket

当前 Bucket：

```text
mmdp-test
```

正式环境建议：

```text
mmdp-prod
```

建议按环境拆分 Bucket，不要测试与生产混用。

### 3.3 推荐的 OSS 目录规划

```text
raw/
processed/
qc/
report/
export/
```

含义：

- `raw/`：原始采集数据
- `processed/`：处理结果
- `qc/`：质检结果
- `report/`：分析报告
- `export/`：导出文件

### 3.4 域名与证书

如项目需要公网长期访问，应统一由主账号名下的域名和 SSL 证书管理。

交接时至少要确认：

- 域名归属
- DNS 解析入口
- 证书签发位置
- 证书续期责任人
- 证书在 Nginx 中的挂载位置

---

## 四、当前项目的部署方式

### 4.1 本地打包、服务器只运行

当前项目推荐采用以下模式：

```text
本地打包前端 dist
本地打包后端 jar
上传 deploy/ 到服务器
服务器执行 docker compose up -d --build
```

这是当前仓库最稳妥的交付方式，原因是：

- 服务器不需要安装 Node.js / Maven
- 构建环境统一在本地
- 服务器职责仅为运行容器

### 4.2 deploy 目录建议

推荐按如下结构组织部署物料：

```text
deploy/
├─ .env
├─ docker-compose.yml
├─ backend
│  ├─ Dockerfile
│  └─ mmdp-backend.jar
├─ frontend
│  └─ dist
├─ nginx
│  └─ nginx.conf
└─ sql
   ├─ schema.sql
   └─ schema-storage-oss.sql
```

说明：

- `deploy/` 只放最终部署产物与部署配置
- 不放源码
- 上传服务器时可以整体上传 `deploy/`

### 4.3 服务器目录结构建议

上传后的服务器结构推荐为：

```text
/data/mmdp
├── deploy
│   ├── .env
│   ├── docker-compose.yml
│   ├── backend
│   ├── frontend
│   ├── nginx
│   └── sql
├── mysql-data
├── logs
└── backup
```

### 4.4 服务更新流程

本地：

- 打包前端
- 打包后端
- 更新 `deploy/` 目录中的产物

上传：

- 将本地新的 `deploy/` 内容上传覆盖到服务器 `/data/mmdp/deploy`

服务器：

```bash
cd /data/mmdp/deploy
docker compose up -d --build
```

查看日志：

```bash
docker compose logs -f
docker compose logs -f backend
docker compose logs -f nginx
docker compose logs -f mysql
```

---

## 五、数据库与 SQL 交接要求

### 5.1 当前数据库

当前项目使用 MySQL。

表结构主脚本位于：

- [schema.sql](/D:/workspace/multimodal-data-platform/mmdp-backend/src/main/resources/schema.sql:1)

如旧库缺少 `data_file.storage_provider` 字段，还需要补充迁移 SQL。

### 5.2 storage_provider 字段要求

因为当前部署只使用 OSS，`data_file.storage_provider` 默认值应为：

```sql
'OSS'
```

交接时要确认：

- 新库初始化脚本中已包含该字段
- 老库升级脚本不会再默认写成 `MINIO`

### 5.3 SQL 交付建议

部署交接时，建议将以下 SQL 一并放入部署物料：

- `schema.sql`：全量建表
- `schema-storage-oss.sql`：旧库补丁
- 如后续有新的业务迁移，再新增带日期前缀的增量 SQL

### 5.4 数据库备份建议

至少约定：

- 备份周期
- 备份目录
- 恢复演练责任人
- 是否保留最近 7 天 / 30 天备份

对于单机 MVP，建议先做：

- 每日 `mysqldump`
- 备份文件保存到 `/data/mmdp/backup/mysql`

---

## 六、OSS 存储管理

### 6.1 当前项目的 OSS 配置变量

后端运行至少依赖以下环境变量：

```env
MMDP_STORAGE_DEFAULT_PROVIDER=OSS
MMDP_OSS_ENDPOINT=...
MMDP_OSS_ACCESS_KEY_ID=...
MMDP_OSS_ACCESS_KEY_SECRET=...
MMDP_OSS_BUCKET=...
MMDP_OSS_REGION=...
```

如果启用 STS 直传，还需要：

```env
MMDP_OSS_STS_ROLE_ARN=...
MMDP_OSS_STS_ROLE_SESSION_NAME=mmdp-direct-upload
MMDP_OSS_STS_DURATION_SECONDS=900
```

### 6.2 文件删除原则

OSS 文件支持以下删除方式：

- 控制台删除
- SDK 删除
- 生命周期规则删除

注意：

```text
OSS 文件删除通常不可恢复
```

建议：

- 先做数据库逻辑删除
- 再做 OSS 延迟删除
- 不要直接对生产 Bucket 大范围手工清理

### 6.3 Bucket 权限原则

不要把 Bucket 改成公有读写。

推荐策略：

- 应用服务端使用 RAM 用户 AccessKey
- 浏览器直传时使用 STS 临时凭证
- 文件访问如需授权，应通过平台接口、签名 URL 或 Nginx 层控制

---

## 七、STS 与直传说明

### 7.1 当前状态

当前仓库已经具备 STS 相关配置项和后端能力，但交接时必须区分：

- 当前环境是否已经启用了 STS 直传
- 还是仍以“浏览器上传到后端，再由后端上传 OSS”为主

不要仅因为代码里有 STS 变量，就默认线上已经切换完成。

### 7.2 推荐的 RAM 角色

角色名称：

```text
mmdp-oss-upload-role
```

角色 ARN 示例：

```text
acs:ram::1661180291353519:role/mmdp-oss-upload-role
```

### 7.3 自定义权限策略

策略名称建议：

```text
MMDPOssDirectUploadPolicy
```

用途：

```text
仅允许上传
禁止读取
禁止删除
```

作用范围示例：

```text
mmdp-test/*
```

### 7.4 启用 STS 时的原则

不要做的事情：

- 删除上传 Role
- 修改 Trust Policy 后不留记录
- 直接给前端长期 AccessKey
- 允许任意路径上传

推荐做法：

- 使用最小权限角色
- 限定对象路径前缀
- 限制临时凭证有效期
- 文档记录 Role ARN、策略名称、用途与责任人

---

## 八、公网访问与网络暴露

### 8.1 当前推荐的公网访问方式

推荐只通过 Nginx 对外暴露：

- `80/tcp`
- `443/tcp`

不推荐直接暴露：

- 后端 `19021`
- MySQL `3306`

原因：

- 前端当前使用相对路径 `/api`
- Nginx 同域反代可避免跨域问题
- 安全面更清晰

### 8.2 必须检查的网络项

阿里云安全组：

- 放行 `80/tcp`
- 放行 `443/tcp`
- `22/tcp` 最好限制来源 IP

服务器本机防火墙：

- 放行 `80/tcp`
- 放行 `443/tcp`

Docker Compose：

- 只将 Nginx 的 `80/443` 映射到宿主机
- Backend 与 MySQL 只保留容器内部互通

### 8.3 域名接入要求

如使用域名，应确认：

- 域名 `A` 记录指向 ECS 公网 IP
- Nginx 配置中已接入证书
- 证书路径与续期流程有交接记录

---

## 九、环境变量、密钥与 Git 管理

### 9.1 .env 管理原则

部署使用的 `.env` 只应保留在本地部署目录和服务器上，不应提交 Git。

当前仓库的 `.gitignore` 已包含以下忽略规则：

- `deploy/.env`
- `deploy/backend/*.jar`
- `deploy/frontend/dist/`

见 [.gitignore](/D:/workspace/multimodal-data-platform/.gitignore:1)。

### 9.2 推荐的交付方式

提交到仓库的文件：

- `.env.example`
- `docker-compose.yml`
- `Dockerfile`
- `nginx.conf`
- SQL 模板与部署文档

不要提交到仓库的文件：

- 真实 `.env`
- RAM AccessKey
- 数据库真实密码
- OSS 真实密钥
- 打包产物

### 9.3 密钥轮换建议

当发生以下情况时，应考虑轮换：

- 有同学离组
- AccessKey 传播范围不明
- 怀疑密钥泄漏
- 生产环境权限模型调整

交接时应明确：

- 当前 AccessKey 的负责人
- 最后一次轮换时间
- 如何替换并重启服务

---

## 十、后续维护原则

### 10.1 不要做的事情

不要：

- 使用主账号 AccessKey
- 删除 RAM 用户
- 删除上传 Role
- 修改 Trust Policy 后不通知团队
- 直接开放 Bucket 公有读写
- 直接将 MySQL 暴露到公网
- 将真实 `.env` 提交 Git

### 10.2 推荐做法

推荐：

- 新同学单独创建 RAM 用户
- 权限按职责最小化分配
- 部署目录、证书、SQL、备份路径都形成书面记录
- 所有云资源改动保留变更说明

---

## 十一、常见问题排查

### 11.1 AccessKey 创建失败

可能原因：

```text
缺少 RAM 权限
```

重点检查：

```text
AliyunSTSAssumeRoleAccess
```

### 11.2 STS 获取失败

检查：

- Role 是否存在
- Trust Policy 是否正确
- Role 是否具有 OSS 相关权限
- 环境变量中的 Role ARN 是否正确

### 11.3 上传 AccessDenied

检查：

- Bucket 名称是否正确
- 策略资源路径是否正确
- 对象路径是否匹配策略范围
- 当前是否把测试 Bucket / 生产 Bucket 配混

### 11.4 页面能打开但接口报错

检查：

- Nginx 是否正确代理 `/api`
- Backend 容器是否正常启动
- `.env` 中数据库与 OSS 变量是否完整
- 安全组是否仅开放了 80，但后端未启动

### 11.5 数据库连接失败

检查：

- `MMDP_DB_URL`
- MySQL 容器状态
- 容器网络中数据库主机名是否与 Compose 一致
- 初始化 SQL 是否执行成功

---

## 十二、交接时必须提供的信息

交接时至少应提供以下内容：

```text
RAM 账号
RAM 密码
RAM AccessKey / Secret
OSS Bucket 名称
OSS 所在 Region
STS Role ARN（如启用）
服务器公网 IP
部署目录
域名
SSL 证书位置
当前 docker compose 入口目录
数据库备份位置
最近一次部署时间
最近一次密钥轮换时间
```

建议额外提供：

- 当前负责人
- 当前环境是测试还是生产
- 当前是否启用 STS 直传
- 当前 MySQL 数据库名
- 当前 Nginx 配置文件路径

---

## 十三、推荐交接动作清单

新同学接手后，建议按以下顺序完成确认：

1. 登录 RAM 控制台，确认 ECS、OSS、RAM、域名、证书访问权限
2. 登录服务器，确认 `/data/mmdp` 目录结构
3. 检查 `deploy/.env` 中数据库、OSS、STS 变量是否齐全
4. 检查 MySQL 数据是否正常、表结构是否完整
5. 检查 Nginx、Backend、MySQL 三个容器是否正常运行
6. 验证前端访问、接口访问、文件上传、文件下载
7. 确认备份路径、证书路径、部署更新流程
8. 记录本次接手时间与负责人

---

## 附：当前项目的重要事实

- 当前项目使用 `MySQL + OSS`，不再以 MinIO 为正式部署目标
- 当前项目推荐公网架构为 `80/443 -> Nginx -> Backend`
- 当前项目支持后端上传 OSS，也具备 STS 直传相关能力
- 当前项目后端运行依赖 `.env` 中的数据库与 OSS 环境变量
- 当前仓库已忽略 `deploy/.env`、部署产物 JAR 与前端 `dist`

