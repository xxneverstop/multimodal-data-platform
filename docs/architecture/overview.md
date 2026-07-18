# MMDP 整体架构总览（2026-06-21）

本文档描述多模态数据平台的项目结构、后端/前端/Worker 三层架构、数据库设计、部署拓扑与端到端数据流。

---

## 一、项目单仓结构

```
multimodal-data-platform/
├── mmdp-backend/                  # Java 21 + Spring Boot 3 + MyBatis-Plus
│   ├── src/main/java/
│   │   └── com/honortech/dataplatform/
│   │       ├── MmDataPlatformApplication.java   # 入口
│   │       ├── auth/              # 认证模块
│   │       ├── task/              # 采集任务
│   │       ├── session/           # 采集会话
│   │       ├── file/              # 文件管理（OSS 上传/下载）
│   │       ├── asset/             # 数据资产
│   │       ├── processing/        # 处理作业管理
│   │       ├── pipeline/          # Pipeline 定义
│   │       ├── profile/           # 采集 Profile + 规则引擎
│   │       ├── qc/                # 质检检查与报告
│   │       ├── user/              # 用户管理
│   │       ├── sessionimport/     # Session 目录导入（ZED 等）
│   │       ├── collector/         # 采集端客户端管理
│   │       ├── subject/           # 被试对象管理
│   │       ├── admin/             # 管理员功能
│   │       └── common/            # 通用组件（ApiResponse、Storage、异常、枚举）
│   ├── src/main/resources/
│   │   ├── application.yml        # 配置（端口 19021，单文件最大 200MB）
│   │   └── schema.sql             # 数据库全量表结构
│   └── pom.xml
│
├── mmdp-frontend/                 # Vue 3 + Vite + TypeScript + Tailwind CSS 4
│   ├── src/
│   │   ├── api/                   # 按模块拆分的 API 层（auth/tasks/sessions/files/processing/qc/…）
│   │   │   └── http.ts            # Axios 实例（响应拦截剥离 ApiResponse 外层）
│   │   ├── assets/
│   │   │   └── main.css           # Light-2 设计系统 CSS
│   │   ├── components/            # 全局 UI 组件（AppDialog、DataTableShell、MmdpLogo…）
│   │   ├── router/
│   │   │   └── index.ts           # 路由配置 + 导航守卫（认证/角色检查）
│   │   ├── stores/
│   │   │   └── auth.ts            # Pinia 认证 Store
│   │   ├── types/                 # TypeScript 类型定义（匹配后端实体）
│   │   ├── utils/
│   │   │   └── format.ts          # 格式化工具（状态/日期/文件大小）
│   │   └── views/                 # 页面组件（按功能域划分）
│   │       ├── auth/              # LoginView
│   │       ├── home/              # 平台概览
│   │       ├── upload/            # 数据上传/目录导入/外部登记
│   │       ├── acquisition/       # 采集任务 CRUD
│   │       ├── sessions/          # Session 列表/详情
│   │       ├── processing/        # Pipeline 定义 + 处理作业记录
│   │       ├── annotation/        # 标注任务（占位）
│   │       ├── qc/                # 质检规则展示 + 报告查看
│   │       ├── export/            # 成品资产/数据导出
│   │       ├── management/        # 管理模块（用户/设备/流程/字典）
│   │       └── playback/          # 全屏多模态回放
│   ├── vite.config.ts             # 端口 5173，/api 代理至 19021
│   ├── tailwind.config.ts
│   └── package.json
│
├── mmdp-worker/                   # Python Worker 独立进程
│   ├── main.py                    # 入口：轮询后台领取任务 → 下载输入 → 执行 Pipeline → 上报
│   ├── config.py                  # 环境变量配置（后端 URL、OSS、轮询间隔）
│   ├── requirements.txt           # requests, oss2
│   └── pipelines/                 # Pipeline 脚本目录
│       ├── base.py                # BasePipeline 抽象基类
│       ├── build_playback.py      # 图像序列 → MP4（ffmpeg）
│       └── __init__.py            # 自动发现与注册所有 Pipeline
│
├── docs/                          # 项目文档
│   ├── 00-project-overview.md     # 项目概述
│   ├── 01-mvp-scope.md            # MVP 功能范围
│   ├── 02-domain-model.md         # 领域模型
│   ├── mmdp-deploy.md             # 部署文档（Docker + Nginx + MySQL）
│   ├── system-overview.md         # 采集端→平台端全流程
│   ├── standard-session-directory-v1.md  # Session 目录导入规范
│   └── version/                   # 版本快照（按日期归档）
│       ├── 20260526-current-state.md
│       └── 20260621-architecture-overview.md  ← 本文档
│
├── deploy/                        # 部署物料（上传至服务器 /data/mmdp）
│   ├── docker-compose.yml         # 三容器编排
│   ├── .env.example               # 环境变量模板
│   ├── .env                       # 🔒 真实密钥（不提交 Git）
│   ├── backend/
│   │   ├── Dockerfile             # Java 21 JRE 镜像
│   │   └── mmdp-backend.jar       # 构建产物
│   ├── frontend/
│   │   └── dist/                  # 前端构建产物
│   ├── nginx/
│   │   └── nginx.conf             # SPA 路由 + /api 反向代理 + 安全拦截
│   └── initdb/
│       └── schema.sql             # 数据库建表（首次启动自动执行）
│
├── container-bat/                 # 本地 OSS 启动脚本
├── copy-deploy.bat                # Windows 一键打包部署脚本
├── CLAUDE.md                      # Claude Code 开发协作规范
└── README.md
```

---

## 二、后端架构

### 2.1 技术栈

| 层 | 技术 |
|----|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 3 |
| ORM | MyBatis-Plus（Mapper 继承 `BaseMapper<T>`，无 DAO 层） |
| 数据库 | MySQL 8.4 |
| 对象存储 | 阿里云 OSS（通过 `OssStorageService` + `StorageRouter` 封装） |
| 认证 | Session 认证（Spring Session） |

### 2.2 分层规范

所有业务模块统一遵循：

```
Controller  →  Service 接口  →  ServiceImpl  →  Mapper（MyBatis-Plus）
  ↑ 构造函数注入     ↑ API 定义       ↑ 业务逻辑     ↑ 数据访问
```

- 返回体统一为 `ApiResponse<T>`（record，含 code + message + data）
- 异常由 `GlobalExceptionHandler` 统一捕获，输出标准错误响应
- 枚举类统一存放于 `common/enums/`
- 控制器禁止 `@Autowired`，使用构造函数注入

### 2.3 业务模块一览

| 模块 | 接口前缀 | 核心职责 |
|------|---------|---------|
| `auth` | `/api/auth` | 登录/登出/当前用户，Session 认证 |
| `task` | `/api/tasks` | 采集任务 CRUD，任务状态管理 |
| `session` | `/api/sessions` | 采集会话列表/详情/回放数据构建 |
| `file` | `/api/tasks/{taskId}/files` | 文件上传至 OSS，下载代理 |
| `asset` | `/api/assets` | 数据资产统一管理（上传/外部/派生） |
| `processing` | `/api/processing-jobs` | 处理作业创建、状态管理、执行触发 |
| `pipeline` | `/api/pipelines` | Pipeline 定义管理、Profile 关联 |
| `profile` | `/api/profiles` | 采集 Profile 管理，source 定义 |
| `sessionimport` | `/api/session-imports` | 采集端 Session 目录导入 |
| `qc` | `/api/qc` | 质检报告生成与查询 |
| `user` | `/api/admin/users` | 用户管理（CRUD + 启停），仅管理员 |
| `worker` | `/api/worker/jobs` | Worker 领取/上报（内部 API） |

### 2.4 核心数据模型关系

```
acquisition_task (采集任务)
    │
    ├── collection_session (采集会话，多次采集属于同一任务)
    │       │
    │       ├── data_file (原始文件记录，OSS 关键信息)
    │       │       │
    │       │       └── qc_report (质检报告，上传后自动触发)
    │       │
    │       ├── data_asset (数据资产，关联 file 或外部路径)
    │       │       │
    │       │       └── asset_lineage (资产血缘：哪次处理产生了哪个资产)
    │       │
    │       └── processing_job (处理作业，MOCK/MANUAL/PYTHON_WORKER)
    │
    ├── collection_profile (采集配置)
    │       ├── collection_profile_source (数据源定义：sourceKey、playbackKind)
    │       └── profile_pipeline → pipeline_definition (Profile 可用哪些 Pipeline)
    │
    └── subject (被试对象)
```

### 2.5 处理作业状态机

```
CREATED  ──→  CLAIMED  ──→  RUNNING  ──→  SUCCESS
                  │                         │
                  │ (Worker 领取)            │ (Worker 上报成功)
                  │                         │
                  └──→  FAILED  ←───────────┘
                       (Worker 上报失败)
```

---

## 三、前端架构

### 3.1 技术栈

| 层 | 技术 |
|----|------|
| 框架 | Vue 3（Composition API） |
| 类型 | TypeScript |
| 构建 | Vite 6 |
| 样式 | Tailwind CSS 4 + Light-2 设计系统 |
| 路由 | Vue Router 4（history 模式） |
| 状态 | Pinia（auth.ts） |
| 网络 | Axios（封装于 `http.ts`） |

### 3.2 路由全景

```
/login                          独立登录页
/                               平台概览（需登录）
/acquisition                    采集任务列表
/acquisition/:taskId            采集任务详情
/sessions                       采集会话列表
/sessions/:sessionId            采集会话详情
/upload                         数据接入（文件上传/目录导入/外部登记）
/processing                     处理规则模板 + 处理作业记录
/annotation                     标注任务
/qc                             质检规则 + 质检报告
/export                         成品资产/数据导出
/management                     管理模块（用户/设备/流程/字典，仅管理员）
/play/:sessionId                全屏多模态回放（不加载 AppLayout）
```

### 3.3 布局层次

```
AppLayout.vue (顶栏 + 双级侧边栏 + <router-view>)
    ├── TopNavBar (Logo / 当前模块标题 / 通知 / 用户头像)
    ├── Sidebar
    │   ├── Primary nav (概览 / 数据接入 / 采集任务 / 处理 / 标注 / 质检 / 导出)
    │   └── Secondary nav (管理，仅 ADMIN 可见)
    └── Content area (<router-view>)

例外：
- /login        → 独立 LoginView
- /play/:id     → 独立 PlaybackView（全屏，无布局壳）
```

---

## 四、Worker 架构

### 4.1 技术栈

| 项 | 内容 |
|----|------|
| 语言 | Python 3 |
| 环境 | `D:\software\Anaconda3\envs\pose_env` |
| 依赖 | `requests`（HTTP 通信）、`oss2`（OSS 上传/下载） |
| 外部工具 | `ffmpeg`（图像序列 → MP4 编码） |

### 4.2 轮询模型

```
┌─────────────┐    POST /api/worker/jobs/claim   ┌──────────────┐
│             │ ──────────────────────────────────→│              │
│   Worker    │   ← WorkerClaimResponse            │   Backend    │
│  (Python)   │   (含 jobId, pipelineId,           │  (Java)      │
│             │    inputFiles OSS 下载信息)          │              │
│             │                                    │              │
│  ① 从 OSS   │                                    │              │
│  下载输入   │                                    │              │
│  ② 执行     │    POST .../success (产物列表)      │              │
│  Pipeline   │ ──────────────────────────────────→│  创建        │
│  ③ 上传产物 │    或 .../failure (错误信息)         │  DataFile    │
│  到 OSS     │                                    │  DataAsset   │
│             │                                    │  AssetLineage│
└─────────────┘                                    └──────────────┘
```

### 4.3 Pipeline 自动发现

`pipelines/__init__.py` 在 Worker 启动时：

1. `importlib` 扫描 `pipelines/` 目录下所有 `.py` 文件
2. 用 `inspect` 查找 `BasePipeline` 的非抽象子类
3. 校验 `pipeline_id` 唯一性，实例化后注册到 `PIPELINES` 字典
4. 打印注册日志 `[pipeline] [OK] 注册 BUILD_PLAYBACK`

**添加新 Pipeline 只需 3 步：**

1. 在 `pipelines/` 下新建 `.py` 文件
2. 继承 `BasePipeline`，实现 `execute(input_dir, output_dir, input_files) -> List[Dict]`
3. 设置 `pipeline_id`、`display_name`、`input_asset_types`、`output_asset_types` 类属性
4. 重启 Worker，自动生效

### 4.4 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MMDP_BACKEND_URL` | `http://localhost:19021` | 后端地址 |
| `MMDP_OSS_ENDPOINT` | 阿里云 OSS 端点 | 与后端共享 |
| `MMDP_OSS_ACCESS_KEY_ID` | — | 与后端共享 |
| `MMDP_OSS_ACCESS_KEY_SECRET` | — | 与后端共享 |
| `MMDP_OSS_BUCKET` | `mmdp-test` | 与后端共享 |
| `MMDP_OSS_REGION` | `cn-hangzhou` | 与后端共享 |
| `MMDP_WORKER_WORK_DIR` | `/tmp/mmdp-worker` | 本地临时工作目录 |
| `MMDP_WORKER_POLL_INTERVAL` | `5` | 轮询间隔（秒） |

---

## 五、数据库表结构

### 5.1 核心业务表

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `acquisition_task` | 采集任务 | task_code, subject_code, action_name, device_type, modality, profile_id |
| `collection_session` | 采集会话 | session_code, task_id, subject_id, session_id（业务去重键）, profile_id, manifest_json, upload_status |
| `data_file` | 物理文件记录 | task_id, session_id, source_key, object_key, bucket_name, file_role, asset_type |
| `data_asset` | 逻辑资产 | task_id, session_id, source_key, asset_type, file_id, produced_by_job_id, source_type |
| `processing_job` | 处理作业 | task_id, session_id, pipeline_id, executor_type（MOCK/MANUAL/PYTHON_WORKER）, status |
| `asset_lineage` | 资产血缘 | task_id, session_id, source_asset_id, target_asset_id, job_id, relation_type |
| `qc_report` | 质检报告 | task_id, session_id, file_id, qc_type（FILE/SESSION）, qc_status, report_json |
| `session_import_record` | Session 导入记录 | task_id, session_record_id, collector_client_id, local_session_id |
| `collector_client` | 采集端设备 | name, client_key, status |
| `subject` | 被试对象 | subject_code, subject_name |

### 5.2 配置与规则表

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `collection_profile` | 采集配置模板 | profile_code, profile_name, package_rule_code, playwright_rule_code（播放规则） |
| `collection_profile_source` | Profile 数据源定义 | profile_id, source_key, source_type, playback_kind（video/imu/imu_curve） |
| `pipeline_definition` | Pipeline 定义 | pipeline_id, display_name, input_asset_types, output_asset_types, executor_type |
| `profile_pipeline` | Profile → Pipeline 多对多关联 | profile_id, pipeline_id |
| `sys_user` | 系统用户 | username, password, role（ADMIN/COLLECTOR/ANNOTATOR/VIEWER） |

### 5.3 建表脚本

完整 DDL 位于 `mmdp-backend/src/main/resources/schema.sql`。

---

## 六、部署架构

### 6.1 拓扑

```
Internet → [ECS 防火墙 :80/:443] → [Nginx :80/:443]
                                        │
             /api/* proxy_pass ──→ [mmdp-backend :19021] (容器内网)
             SPA 静态文件 ←── /usr/share/nginx/html
                                        │
                                        ↓
                                  [mmdp-mysql :3306] (容器内网)
```

三个 Docker 容器通过内部 bridge 网络 `mmdp-net` 通信，仅 Nginx 暴露宿主机端口。Backend 和 MySQL 不直接暴露到公网。

### 6.2 容器规格

| 容器 | 镜像 | 端口映射 | 持久化 |
|------|------|----------|--------|
| mmdp-nginx | `nginx:1.27-alpine` | 80:80, 443:443 | 配置/前端文件只读挂载 |
| mmdp-backend | 自构建（Java 21 JRE） | 无（内网 19021） | 无 |
| mmdp-mysql | `mysql:8.4.2` | 13306:3306（仅调试） | `./mysql-data` 挂载宿主机 |

### 6.3 部署流程

**本地打包**（Windows）：
```bash
# 后端
cd mmdp-backend
mvn clean package -DskipTests

# 前端
cd mmdp-frontend
npm run build

# 或一键：双击 copy-deploy.bat
```

**上传**：
```bash
scp -r deploy/ root@<服务器IP>:/data/mmdp/
```

**启动**：
```bash
cd /data/mmdp/deploy
docker compose up -d --build
```

详细运维操作（容器的启停、日志、备份、Navicat SSH 隧道连接等）参见 `docs/mmdp-deploy.md`。

---

## 七、端到端数据流

### 7.1 主数据流

```
┌─────────────────┐
│ 1. 创建采集任务    │  POST /api/tasks
│   定义被试/动作/设备│
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 2. 数据接入       │
│  a) 平台上传：     │  POST /api/tasks/{id}/files
│     单文件/多文件  │  → OSS 存储 → data_file → data_asset → QC
│  b) 标准目录导入：  │  POST /api/session-imports
│     目录 + manifest│  → 按 sourceKey 校验 → Session + File + Asset
│  c) 外部登记：     │  仅写 data_asset（不传文件，不触发 QC）
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 3. 处理触发       │
│  用户在 Session   │  POST /api/sessions/{id}/processing-jobs
│  详情页点"执行"   │  → 创建 processing_job (status=CREATED)
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 4. Worker 执行   │
│  轮询 claim →    │  从 OSS 下载输入 → 执行 Pipeline → 上传产物到 OSS
│  → success/fail  │  → 后端创建 DataFile + DataAsset + AssetLineage
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 5. 回放查看       │
│  /play/:sessionId│  GET /api/sessions/{id}/playback
│  多视频同步 + IMU │  → Profile 规则引擎 → 视频/IMU 源构建 → 前端同步播放
└─────────────────┘
```

### 7.2 文件上传 QC 链路（文件级）

```
用户上传文件
    │
    ▼
校验任务存在 → 校验文件非空 → 上传到 OSS → 写入 data_file
    │
    ▼
自动创建 data_asset (sourceType=UPLOADED_FILE)
    │
    ▼
同步执行 QC 检查（文件非空、扩展名、大小、格式可读性、表头规则）
    │
    ▼
写入 qc_report (report_json + summary + status)
    │
    ▼
更新 acquisition_task.status (基于最近一次 QC 结果)
```

### 7.3 Session 导入链路（目录级）

```
前端选择本地标准目录
    │
    ▼
前端预检：manifest.json 存在性、JSON 合法性、路径一致性
    │
    ▼
逐文件上传到 OSS 临时区 imports/{taskId}/{importKey}/{relativePath}
    │
    ▼
POST /api/session-imports/finalize（传 manifest.json + 已上传文件列表）
    │
    ▼
后端校验：sourceKey 白名单、required source 完整性、对象大小一致性
    │
    ▼
创建 CollectionSession → 创建 DataFile → 创建 DataAsset（仅 sources）
    │
    ▼
关联 Profile，为后续回放/处理提供 source 上下文
```

---

## 八、功能实现度逐模块详评

> 评估标记：✅ 真实可用　⚠️ MVP/占位/缺陷　🔶 有 UI 无逻辑壳子　❌ 未实现

---

### 8.1 Auth（认证模块）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| POST /login | ✅ | BCrypt 密码匹配，Session 管理，拦截停用用户 |
| POST /logout | ✅ | 使 HttpSession 失效 |
| GET /me | ✅ | 返回当前登录用户 |
| 角色枚举（ADMIN/COLLECTOR/ANNOTATOR/VIEWER） | ✅ | 创建/更新时校验，前端路由守卫检查 |
| 方法级权限注解 | ❌ | 无 `@PreAuthorize`，权限仅控制在前端路由 + AdminUserController 层 |

---

### 8.2 Task（采集任务）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| POST /api/tasks（创建） | ✅ | `BusinessCodeGenerator` 生成编码，自动关联 Subject/Profile |
| GET /api/tasks（列表） | ✅ | 分页、多条件筛选（状态/subjectCode/actionName/日期/关键词）、排序 |
| GET /api/tasks/{id}（详情） | ✅ | 包含关联的 session 摘要 |
| PUT /api/tasks/{id}（更新） | ❌ | 端点不存在 |
| DELETE /api/tasks/{id}（删除） | ⚠️ | 仅 AdminController 有，普通 TaskController 无 |
| 状态机 CREATED→UPLOADED→QC_PASSED/WARNING/FAILED | ⚠️ | 状态混合了"任务生命周期"和"最近一次 QC 结果"两重含义，语义不纯 |

---

### 8.3 Session（采集会话）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| GET /api/sessions（列表） | ✅ | 联表聚合 files/assets 计数、QC 状态、导出状态，支持排序 |
| GET /api/tasks/{taskId}/sessions | ✅ | 按任务筛选 |
| GET /api/sessions/{sessionId}（详情） | ✅ | 含关联 assets、profile sources |
| GET …/playback（回放数据） | ✅ | Profile 驱动的真实构建，按 sourceKey 匹配文件 |
| GET …/playback/check（回放条件检查） | ✅ | canPlay() 逻辑完整 |
| Session 去重（session_id 唯一索引） | ✅ | 采集端导入时防止重复 |

---

### 8.4 File（文件管理）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| POST 文件上传（后端中转） | ✅ | 校验任务→校验非空→上传 OSS→写 DataFile→自动 QC |
| POST 文件上传（OSS 直传） | ✅ | 分配 STS 临时凭证，前端直传后 headObject 验证 |
| ZIP 包解压上传 | ✅ | ArchiveUtils.extract 自动识别格式 |
| GET 文件下载 | ✅ | 流式分块传输，OSS 302 重定向或代理下载 |
| 文件角色枚举（UPLOADED_FILE/PROCESSED_OUTPUT/SESSION_MANIFEST…） | ✅ | 完整 |

---

### 8.5 Asset（数据资产）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| 上传资产自动生成 | ✅ | sourceType=UPLOADED_FILE，幂等去重 |
| 外部资产手动登记 | ✅ | sourceType=EXTERNAL_PATH |
| 派生资产（处理产物） | ✅ | producedByJobId 标记，sourceType=PROCESSED_OUTPUT |
| 导入资产 | ✅ | sourceType=ACQUISITION_SYNC |
| AssetType 枚举 14 种 | ✅ | RGB_SEQ_RAW / RGB_VIDEO_MP4 / MOCAP_CSV / SMPL_RESULT / RAW_IMU_CSV … |
| 跨表联查（file + asset + job） | ✅ | 列表查询带 fileSize、uploadStatus |

---

### 8.6 Processing（处理作业）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| createJob（任务级 MOCK） | ⚠️ | 记录真实，但执行器返回硬编码 mock 结果 |
| createSessionJob（Session 级 PYTHON_WORKER） | ✅ | 创建 CREATED 状态 job，等 Worker 领取 |
| createManualJob（人工登记） | ✅ | 立即标记 SUCCESS，建立输入→输出血缘 |
| claimJob（Worker 领取） | ✅ | 取最早 CREATED+PYTHON_WORKER，构建 inputFiles 含 OSS 信息 |
| completeJob（Worker 成功上报） | ✅ | 创建 DataFile+DataAsset+AssetLineage，完整血缘 |
| failJob（Worker 失败上报） | ✅ | 记录 errorMessage，标记 FAILED |
| Job 状态机 6 态 | ✅ | CREATED→CLAIMED→RUNNING→REGISTERED→SUCCESS/FAILED |
| 处理进度百分比 | ❌ | 无进度字段，Worker 无上报进度 API |
| 处理耗时显式记录 | ❌ | 只能通过 created_at/updated_at 推算 |
| 重试按钮/重新执行 | ❌ | 失败后只能创建新 job |
| 清理残留产物 | ⚠️ | Admin API 有删除端点，但非用户自助 |

---

### 8.7 Pipeline（流水线定义）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| Pipeline CRUD | ✅ | 创建/查询/更新/软删除（enabled=0） |
| Profile-Pipeline 多对多关联 | ✅ | profile_pipeline 表管理 |
| 按 Session 查询可用 Pipeline | ✅ | 根据 Profile 关联 + 已有资产类型过滤 |
| Pipeline 可用性检查（readiness） | ✅ | 检查必需资产是否齐全 |
| 自动注册（Worker 端） | ✅ | pipeline_id 匹配 BasePipeline 子类 |

---

### 8.8 Profile（采集配置）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| Profile CRUD | ✅ | 创建时可批量添加 sources |
| Source CRUD | ✅ | per-sourceKey 的 playback_kind/parsed_asset_type 配置 |
| 默认 Profile 自动创建 | ✅ | BINOCULAR_HMD_IMU_V1，含 4 个 sources |
| PlaybackRuleResolver（播放规则解析） | ⚠️ | 接口存在，DefaultPlaybackRuleResolver 有基本实现，但 ProfileRuleRegistry 中其他 rule code 无匹配实现 |
| PackageRuleResolver（打包规则） | 🔶 | 仅有接口，无实现 |
| ParserRuleResolver（解析规则） | 🔶 | 仅有接口，无实现 |
| ArchiveRuleResolver（归档规则） | 🔶 | 仅有接口，无实现 |

---

### 8.9 QC（质量检查）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| fileNotEmpty | ✅ | 空文件→FAILED |
| supportedExtension | ✅ | 白名单检查（13 种格式） |
| fileSizeReasonable | ✅ | <64B 警告，>100MB 警告 |
| readableCsv / timestampHeader / imuHeaders | ✅ | CSV 语义级检查（表头、timestamp 列、acc/gyro 列） |
| mocapQuaternionHeaders | ✅ | quat/quaternion 列检查 |
| readablePreview / timestampLikeFirstColumn / stableColumnCount / numericValueColumns | ✅ | TXT 时序文件专项检查 |
| jsonParseable | ✅ | JSON 解析检查 |
| imageMagicBytes | ✅ | JPG(FFD8FF)/PNG(89504E47) 幻数校验 |
| hdf5Signature / wavRiffHeader / npzZipSignature | ✅ | 二进制格式签名校验 |
| videoMp4Extension / videoContentType / videoFileSize | ✅ | MP4 格式校验 |
| 跨文件/Session 级 QC | ❌ | 仅有 FILE 级，无 SESSION 级 |
| 跨模态一致性检查 | ❌ | 无（如 IMU 时间范围是否覆盖视频时长） |
| 数据漂移检测 | ❌ | 无 |
| 丢帧检测 | ❌ | 无 |

---

### 8.10 SessionImport（采集导入）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| Manifest 解析（新旧格式兼容） | ✅ | 支持 localRefs/platformRefs 和扁平字段 |
| 去重（localSessionId + session_id） | ✅ | 已导入的跳过重复处理 |
| sourceKey 白名单校验 | ✅ | 对照 Profile sources |
| Required source 完整性检查 | ✅ | Profile 标记 required 的必须存在 |
| 路径安全校验 | ✅ | 拒绝 ../、..\\ 等危险路径 |
| 任务自动创建 | ✅ | 未关联已有 Task 时按 manifest 信息创建 |
| Subject 自动解析 | ✅ | 按 code 或名称查找/创建 |
| OSS 文件归档 | ✅ | 从临时区拷贝到最终路径 |
| 文件大小 OSS 校验 | ✅ | headObject 对比 |
| 大文件处理 | ⚠️ | 当前为单文件上传，超大 Session 可能超时 |

---

### 8.11 User（用户管理）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| CRUD | ✅ | 创建/列表/更新/启停 |
| BCrypt 密码加密 | ✅ | Spring Security PasswordEncoder |
| 用户名唯一性校验 | ✅ | 大小写不敏感 |
| 角色/状态枚举校验 | ✅ | normalize() 转换 |

---

### 8.12 Admin（管理功能）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| DELETE 处理作业产物 | ✅ | 级联删除 DataFile + DataAsset |
| DELETE Session | ✅ | 含关联文件/资产清理 |
| DELETE Task | ✅ | 级联清理 |

---

### 8.13 Worker（Python 处理进程）

| 功能点 | 状态 | 说明 |
|--------|------|------|
| 轮询领取任务 | ✅ | 每 5 秒 claim，取最早 CREATED job |
| OSS 下载输入文件 | ✅ | oss2 SDK |
| Pipeline 自动发现 | ✅ | importlib + inspect 扫描注册 |
| Pipeline 执行 | ✅ | BuildPlaybackPipeline（图像序列→MP4，ffmpeg） |
| OSS 上传产物 | ✅ | processed/sessions/{id}/jobs/{id}/ |
| 成功/失败上报 | ✅ | 产物列表或错误信息 5000 字截断 |
| 临时目录清理 | ✅ | 成功后清空，失败时保留现场 |
| 重试机制 | ⚠️ | 无显式重试，靠下一轮轮询重新领取 |
| 进度上报 | ❌ | Worker 端无 API，后端无字段 |
| 算力标签（GPU vs CPU） | ❌ | 不区分设备类型 |

---

### 8.14 前端视图逐页评估

| 视图页面 | 数据源 | 状态 | 说明 |
|----------|--------|------|------|
| LoginView | 真实 API | ✅ | 登录表单，重定向逻辑完整 |
| HomeOverviewView | 真实 + 伪造 | ⚠️ | 指标统计来自真实 API，30 天趋势图用 `Math.sin()` 生成 |
| TaskListView | 真实 API | ✅ | 分页、筛选、排序 |
| TaskDetailView | 真实 API | ✅ | Tabs：概览/资产/处理/QC/血缘 |
| TaskCreateView | 真实 API | ✅ | 创建表单 |
| SessionListView | 真实 API | ✅ | 筛选（QC 状态、导出状态）、排序 |
| SessionDetailView | 真实 API | ✅ | 资产分组、处理触发、QC 摘要 |
| PlaybackView | 真实 API | ✅ | 多视频同步 + IMU 实时数据、速度控制、布局切换 |
| UploadEntryView | 真实 API | ✅ | 单文件/多文件上传 + 目录导入 + 外部登记 |
| ProcessingTemplateView | 真实 API | ✅ | Pipeline CRUD + 处理作业列表、自动刷新 |
| ProfileManagementView | 真实 API | ✅ | Profile + Source CRUD |
| UserManagementView | 真实 API | ✅ | 用户 CRUD |
| QcWorkspaceView | 真实 + fake | ⚠️ | 报告列表来自真实 API，规则列表为前端写死的 3 条 |
| AnnotationTaskView | 完全伪造 | 🔶 | `assetId % 3` 取模推断标注状态，仅 UI 布局，"跳转外部标注"按钮 |
| FinalAssetView | 伪造 | 🔶 | 合成数据处理 |
| ManagementModuleView | 真实 API | ✅ | 路由器/容器页面 |
| AcquisitionListView | 伪造 | 🔶 | 基于 platform.ts 合成数据 |
| PlaceholderView | 无 | 🔶 | 纯文本占位 |
| CollectorPrototypeView | 真实 | ✅ | 连接采集器客户端（localhost:19022），WebSocket + MJPEG |

---

### 8.15 前端 API 层逐文件评估

| API 文件 | 状态 | 说明 |
|----------|------|------|
| http.ts | ✅ | Axios 封装，401 自动派发 `mmdp-auth-expired` |
| auth.ts | ✅ | 3 函数 |
| tasks.ts | ✅ | 6 函数 |
| sessions.ts | ✅ | 10 函数 |
| files.ts | ✅ | 1 函数 |
| assets.ts | ✅ | 2 函数 |
| processing.ts | ✅ | 9 函数 |
| pipelines.ts | ✅ | 6 函数 |
| profiles.ts | ✅ | 8 函数 |
| qc.ts | ✅ | 1 函数 |
| users.ts | ✅ | 4 函数 |
| admin.ts | ✅ | 3 函数 |
| collector.ts | ✅ | 连接采集器，WebSocket+MJPEG |
| platform.ts | ⚠️ | 概览趋势图用 `Math.sin()` 合成，标注状态用取模推断 |

---

## 九、完成度总览

### 8 条主链路完成度

| 链路 | 状态 | 成熟度 |
|------|------|--------|
| ① 任务创建与管理 | ✅ 真实 | ★★★★☆（缺更新端点） |
| ② 数据接入（上传/导入/登记） | ✅ 真实 | ★★★★★（三种方式完整） |
| ③ 文件级 QC | ✅ 真实 | ★★★☆☆（检查项有实际意义，但无跨模态） |
| ④ Session 管理 | ✅ 真实 | ★★★★★ |
| ⑤ 处理作业编排 | ⚠️ 半真实 | ★★★☆☆（状态机完整，执行器 mock） |
| ⑥ Worker 异步执行 | ✅ 真实 | ★★★☆☆（流程完整，仅 1 个真实 Pipeline） |
| ⑦ 多模态回放 | ✅ 真实 | ★★★☆☆（视频同步+IMU 可用，无逐帧步进/标注叠加） |
| ⑧ 标注 | 🔶 壳子 | ★☆☆☆☆（仅 UI 布局） |

### 统计

| 指标 | 数量 |
|------|------|
| ✅ 真实可用 | 50+ 功能点 |
| ⚠️ MVP/占位/缺陷 | ~15 功能点 |
| 🔶 仅壳子 | 6 个前端页面 |
| ❌ 未实现 | ~10 功能点 |

### 最成熟的模块（可直接用于演示）
Auth / Session / File / Asset / SessionImport / User / Pipeline 定义

### 最薄弱的模块（最需要投入）
1. **标注** — 纯壳子，无任何真实功能
2. **处理执行** — MOCK executor，无真实算法
3. **QC 深度** — 只有文件级检查，无业务语义
4. **Profile 规则引擎** — 4 个 RuleResolver 只有接口
5. **前端概览/导出页** — 合成数据，非真实统计

---

## 十、开发环境启动

### 本地开发

```bash
# 终端 1：启动后端
cd mmdp-backend
mvn spring-boot:run                          # 端口 19021

# 终端 2：启动前端
cd mmdp-frontend
npm install && npm run dev                   # 端口 5173，/api 代理至 19021

# 终端 3：启动 Worker（可选）
cd mmdp-worker
# 设置环境变量后
python main.py                               # 每 5 秒轮询后端
```

前端通过 Vite 代理 `localhost:5173/api/* → localhost:19021/api/*` 开发，不跨域。

### 生产部署

见第六节及 `docs/mmdp-deploy.md`。

---

## 十一、版本信息

| 项 | 内容 |
|----|------|
| 文档创建日期 | 2026-06-21 |
| 当前分支 | `dev` |
| 最近版本标签 | `feat(release): ship pipeline worker and profile workflows` |
| 上一版快照 | `docs/version/20260526-current-state.md` |
| 后续更新 | 建议按 `docs/version/YYYYMMDD-xxx.md` 追加新文档 |
