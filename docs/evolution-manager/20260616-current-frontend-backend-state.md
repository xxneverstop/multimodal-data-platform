# 管理平台前后端现状与核心链路梳理

> 日期：2026-06-16  
> 范围：`multimodal-data-platform` 当前前端、后端、数据库结构与核心业务链路  
> 说明：本文基于当前代码实现梳理，不按历史讨论推断；“已实现”表示代码中已有可调用链路，“规划/占位”表示页面、ViewModel 或接口形态已经预留，但后端能力尚未完整闭环。

## 1. 总体结论

当前平台已经形成了以“采集任务 + 采集 Session”为核心的数据管理骨架。

平台的主链路不是单纯管理文件，而是围绕一次采集过程建立业务对象：

```text
采集任务
  -> 采集 Session
  -> 文件上传 / Session 导入
  -> data_file / data_asset 入库
  -> 质检结果 / 处理任务 / 资产血缘
  -> 采集详情 / 回放 / 导出查看
```

当前已经实现的主干能力包括：

| 模块 | 当前状态 | 说明 |
| --- | --- | --- |
| 登录与权限 | 已实现 | 基于 Session 的登录态；`/api/**` 默认需要登录，`/api/admin/**` 需要管理员 |
| 任务管理 | 已实现 | 支持创建、列表、详情；任务列表已聚合 Session 数与最新 Session 状态 |
| Session 管理 | 已实现 | 支持 Session 列表、任务下 Session、Session 详情、回放数据 |
| 文件上传 | 已实现 | 支持普通文件上传与 OSS 直传登记 |
| Session 导入 | 已实现 | 支持标准 Session 包导入、目录上传后 finalize、OSS 临时对象归档 |
| 数据资产 | 已实现 | 支持按任务查询资产、创建外部资产；Session 详情中按资产聚合展示 |
| 文件级质检 | 已实现 | 上传后可生成基础 QC 报告；当前主要是文件级检查 |
| 处理任务 | 部分实现 | 支持处理任务记录、手工登记结果、Mock 类型处理链路 |
| 导出查看 | 前端聚合实现 | 当前按 Session 展示可下载资产，但没有独立导出包后端 |
| 质检规则 | 前端占位 | 规则管理页面已有，但后端规则实体、绑定与执行编排未落地 |
| 标注 | 前端占位 | 页面从资产推导标注任务视图，未接入真实标注系统 |
| 设备/流程/存储/字典管理 | 前端占位 | 管理入口存在，除用户管理外尚未形成真实业务闭环 |

## 2. 当前前端结构

### 2.1 路由与一级页面

前端使用 Vue 3 + Vite，主路由集中在 `mmdp-frontend/src/router/index.ts`。

| 路由 | 页面 | 当前定位 |
| --- | --- | --- |
| `/login` | 登录页 | 用户登录入口 |
| `/home` | 首页 | 平台总览，数据由前端聚合构建 |
| `/upload` | 上传页 | 平台侧上传工作台，支持文件上传与 Session 包导入 |
| `/acquisition` | 任务列表 | 当前 UI 文案为“任务”，业务对象为采集任务 |
| `/acquisition/new` | 新建任务 | 创建采集任务 |
| `/acquisition/:taskId` | 任务详情 | 查看任务、资产、QC、处理、血缘等信息 |
| `/sessions` | 采集列表 | 当前 UI 文案为“采集”，一行代表一次 Session |
| `/sessions/:sessionId` | 采集详情 | Session 主工作页，聚合数据、质检、导出、元信息 |
| `/processing` | 处理页 | 处理模板、处理任务、资产血缘展示 |
| `/annotation` | 标注页 | 标注任务视图，目前主要为前端派生 |
| `/qc` | 质检页 | 质检规则与质检结果两个视角 |
| `/export` | 导出页 | 按 Session 查看可下载资产 |
| `/collector` | 采集端原型页 | 对接本地采集服务的实验性入口 |
| `/management/users` | 用户管理 | 管理员用户管理 |

兼容路由：

| 路由 | 当前行为 |
| --- | --- |
| `/data` | 重定向到 `/sessions` |
| `/tasks` | 重定向到 `/acquisition` |

这说明当前前端已经从“数据文件列表”转向“Session 列表”作为主视角。

### 2.2 导航结构

主布局位于 `mmdp-frontend/src/layouts/AppLayout.vue`。

当前功能区分为：

| 分组 | 菜单 | 当前状态 |
| --- | --- | --- |
| 功能 | 上传、任务、采集、处理、标注、质检、导出 | 主业务入口 |
| 管理 | 用户、设备、流程、存储、字典 | 用户管理已接后端，其余偏占位 |
| 系统 | 退出登录 | 调用登录态注销 |

当前命名已经基本符合新的业务语义：

| UI 名称 | 业务含义 |
| --- | --- |
| 任务 | 采集任务，是多次采集 Session 的业务容器 |
| 采集 | 一次采集 Session |
| 数据 | 不再作为一级主对象，已下沉到采集详情 |

### 2.3 前端数据聚合方式

前端存在一层聚合适配文件：

```text
mmdp-frontend/src/api/platform.ts
```

它不是后端直接提供的 BFF 接口，而是在前端组合多个后端 API，形成页面需要的 ViewModel。

主要聚合能力：

| 方法 | 当前作用 |
| --- | --- |
| `fetchPlatformDataset` | 聚合任务、Session、资产、QC、处理任务，构建平台总数据集 |
| `fetchSessionDetail` | 聚合某个 Session 的任务、资产、QC、处理与导出状态 |
| `fetchProcessingTemplates` | 基于前端模板与最新处理任务构建处理模板视图 |
| `fetchAnnotationTasks` | 基于资产派生标注任务视图 |
| `fetchQcRules` | 返回前端占位的质检规则 |
| `fetchQcLogs` | 将后端 QC 报告映射为质检结果日志 |
| `fetchFinalAssets` | 按 Session 聚合可下载资产，形成导出列表 |

需要注意：

| 点位 | 当前情况 |
| --- | --- |
| 总览趋势 | 部分趋势数据由前端生成，用于 MVP 展示，不是完整统计分析 |
| 质检规则 | 规则数据为前端占位，不代表后端已执行这些规则 |
| 导出列表 | 当前本质是“按 Session 展示已有可下载资产”，不是独立导出包 |
| 标注任务 | 当前由前端从资产推导，并带有示例链接，不是正式标注平台任务 |
| 手工上传兼容 | 前端会为未绑定真实 Session 的资产构造“手工上传 Session”视图，避免页面断链 |

## 3. 当前后端结构

### 3.1 后端模块划分

后端是 Spring Boot 项目，主要模块位于：

```text
mmdp-backend/src/main/java/com/mmdp
```

当前核心 Controller 能力如下：

| Controller | 主要接口 | 当前能力 |
| --- | --- | --- |
| `AuthController` | `/api/auth/login`、`/api/auth/logout`、`/api/auth/me` | 登录、退出、当前用户 |
| `AdminUserController` | `/api/admin/users` | 管理员用户管理 |
| `AcquisitionTaskController` | `/api/tasks` | 任务创建、列表、详情 |
| `CollectionSessionController` | `/api/sessions`、`/api/tasks/{taskId}/sessions` | Session 列表、详情、回放 |
| `SessionImportController` | `/api/session-imports` | Session 包导入、直传初始化、finalize |
| `DataFileController` | `/api/tasks/{taskId}/files`、`/api/files/{fileId}` | 文件上传、直传、查询、下载 |
| `DataAssetController` | `/api/tasks/{taskId}/assets` | 资产查询、外部资产登记 |
| `QcReportController` | `/api/tasks/{taskId}/qc-report` | 任务下 QC 报告查询 |
| `ProcessingJobController` | `/api/tasks/{taskId}/processing-jobs` | 处理任务、手工处理结果、血缘 |
| `PipelineController` | `/api/tasks/{taskId}/available-pipelines` | 可用处理流程判断 |

### 3.2 权限链路

当前权限由后端拦截器控制：

| 路径 | 要求 |
| --- | --- |
| `/api/auth/**` | 允许匿名访问 |
| `/api/**` | 需要登录 |
| `/api/admin/**` | 需要管理员 |

前端路由也有对应守卫：

| 路由类型 | 当前行为 |
| --- | --- |
| `requiresAuth` | 未登录跳转 `/login` |
| `requiresAdmin` | 非管理员跳转首页 |

因此当前权限链路是前后端双层控制，后端为最终保护。

## 4. 数据库主对象

当前数据库结构已经支持任务、Session、文件、资产、质检、处理、血缘的基本闭环。

### 4.1 核心业务对象

| 表 | 当前职责 |
| --- | --- |
| `acquisition_task` | 采集任务，是平台任务级业务容器 |
| `collection_session` | 采集 Session，表示一次采集或一次导入 |
| `session_import_record` | Session 导入记录，记录导入状态、manifest、错误等 |
| `data_file` | 文件记录，保存文件路径、大小、上传状态、对象存储信息 |
| `data_asset` | 数据资产，面向业务使用的资产记录，可关联文件、任务、Session |
| `qc_report` | 文件级 QC 报告 |
| `processing_job` | 处理任务记录 |
| `asset_lineage` | 资产血缘，记录输入资产到输出资产的关系 |

### 4.2 辅助对象

| 表 | 当前职责 |
| --- | --- |
| `user_account` | 用户账号与角色 |
| `subject` | 被试信息 |
| `collection_profile` | 采集 Profile |
| `collector_client` | 采集端客户端标识 |

### 4.3 当前关系主线

当前数据关系可以概括为：

```text
acquisition_task
  -> collection_session
  -> session_import_record
  -> data_file
  -> data_asset
  -> qc_report / processing_job / asset_lineage
```

需要注意：

| 对象 | 当前特点 |
| --- | --- |
| `data_file` | 更接近物理文件或对象存储文件记录 |
| `data_asset` | 更接近业务资产，可用于处理、质检、导出、回放 |
| `collection_session` | 已成为平台视图聚合中心 |
| `qc_report` | 当前主要关联文件，不是完整 Session 级 QC |

## 5. 核心链路

### 5.1 任务创建链路

任务创建是平台侧的业务入口。

```text
前端新建任务页
  -> POST /api/tasks
  -> AcquisitionTaskController
  -> AcquisitionTaskService
  -> acquisition_task 入库
  -> 返回任务详情
```

当前任务字段用于描述采集业务背景，例如任务名称、任务编号、被试、动作、Profile、采集日期等。

任务列表会额外返回：

| 聚合字段 | 来源 |
| --- | --- |
| Session 数量 | `collection_session` |
| 最新 Session ID | `collection_session` |
| 最新 Session 编号 | `collection_session` |
| 最新 Session 状态 | `collection_session` |

这说明后端任务列表已经开始向“任务 + Session 概览”靠拢。

### 5.2 平台侧普通文件上传链路

普通文件上传主要用于任务下补充或手工上传数据。

```text
前端上传页 / 任务详情上传区
  -> POST /api/tasks/{taskId}/files/initiate
  -> 前端使用 OSS STS 凭证直传文件
  -> POST /api/files/{fileId}/complete
  -> data_file 标记上传成功
  -> 创建 data_asset
  -> 执行基础 QC
  -> 更新 Session / Task 状态
```

也存在后端接收 multipart 文件的兼容接口：

```text
POST /api/tasks/{taskId}/files
```

当前特征：

| 点位 | 当前实现 |
| --- | --- |
| Session 处理 | 普通上传会解析或创建 Session，保证页面不因缺少 Session 断链 |
| OSS 直传 | 已实现 STS 初始化、前端 multipart 上传、后端 complete 登记 |
| QC | 上传完成后执行基础文件级 QC |
| 资产生成 | 上传成功后会创建 `data_asset` |

### 5.3 Session 包导入链路

Session 包导入是当前更符合“采集 Session 整体进入平台”的主链路。

```text
前端上传页选择 Session 包 / 标准目录
  -> 校验 manifest 与目录结构
  -> POST /api/tasks/{taskId}/session-imports/uploads/initiate
  -> 前端将 manifest、数据文件、归档文件上传到 OSS 临时区
  -> POST /api/session-imports/finalize
  -> 后端校验任务、文件、manifest、路径规则
  -> 创建或复用 collection_session
  -> 创建 session_import_record
  -> 将临时对象归档到正式任务 / Session 路径
  -> 创建 data_file
  -> 创建 data_asset
  -> 设置 Session imported / playable
  -> 更新任务状态
```

后端也保留直接导入接口：

```text
POST /api/session-imports
```

当前特征：

| 点位 | 当前实现 |
| --- | --- |
| 幂等 | 根据请求和本地 Session 标识做重复导入保护 |
| manifest | 会规范化、校验，并写入导入记录 |
| Profile 规则 | 后端已有 Profile 规则注册机制，用于包结构、解析、归档、回放校验 |
| 数据落库 | 导入后同时形成文件记录和业务资产 |
| 回放 | 导入成功后可通过 Session 回放接口组织播放数据 |

这条链路是当前平台最接近目标形态的核心链路。

### 5.4 Session 查看与回放链路

采集列表和详情页围绕 Session 展示。

```text
前端采集列表
  -> GET /api/sessions
  -> 展示每次 Session

前端采集详情
  -> GET /api/sessions/{sessionId}
  -> 前端聚合任务、资产、QC、处理、导出状态
  -> 按数据、质检、导出、元信息展示

前端回放页
  -> GET /api/sessions/{sessionId}/playback
  -> 获取可回放数据组织
```

当前 Session 详情不是简单读取单个后端接口，而是前端聚合多个接口形成完整页面。

### 5.5 质检链路

当前质检已经有真实后端执行，但能力范围主要是文件级。

```text
文件上传 / Session 导入
  -> 生成 data_file / data_asset
  -> QcInspectionService 执行基础检查
  -> qc_report 入库
  -> 前端质检页 / Session 详情展示结果
```

当前已实现的检查包括：

| 检查类型 | 当前能力 |
| --- | --- |
| 通用文件 | 非空、大小、扩展名 |
| 视频 | MP4 基础识别 |
| 文本时序 | TXT 基础行内容检查 |
| CSV | 表头与关键字段检查 |
| JSON | JSON 可解析检查 |
| 图片 | 基础魔数检查 |
| HDF5 / NPZ / WAV | 基础文件签名检查 |

当前尚未完整实现：

| 能力 | 当前状态 |
| --- | --- |
| 质检规则实体 | 前端占位，后端未建规则管理闭环 |
| 规则绑定 | 未形成任务、Session、Profile 到规则组的持久化绑定 |
| Session 级 QC | 页面有 Session 维度展示，但后端主要产出文件级 QC |
| 多规则编排 | 未形成可配置执行计划 |

### 5.6 处理与血缘链路

处理模块当前已经有后端记录能力，但处理执行仍偏 MVP。

```text
任务详情 / 处理页
  -> 查询可用 pipeline
  -> 创建处理任务或手工登记处理结果
  -> processing_job 入库
  -> 生成输出 data_asset
  -> asset_lineage 记录输入输出关系
```

当前特征：

| 点位 | 当前实现 |
| --- | --- |
| 可用流程判断 | 根据任务资产判断是否满足流程输入 |
| 自动流程 | 当前主要支持 `RGB_MOCAP_ALIGNMENT`，执行类型偏 Mock |
| 手工处理结果 | 可登记处理结果，并生成输出资产 |
| 血缘 | 已有 `asset_lineage` 记录输入输出关系 |

### 5.7 导出链路

导出页当前是“按 Session 组织可下载资产”的前端聚合视图。

```text
前端导出页
  -> fetchFinalAssets
  -> 聚合 Session 与资产
  -> 根据 asset.storageUrl 判断是否可下载
  -> 展示 Session 级导出状态与详情
```

当前没有独立后端导出任务或导出包管理。

| 能力 | 当前状态 |
| --- | --- |
| 按 Session 展示 | 已实现 |
| 查看 Session 下可下载资产 | 已实现 |
| 下载已有资产 | 已实现，依赖现有文件下载或存储 URL |
| 生成导出包 | 未实现 |
| 导出任务状态机 | 未实现 |
| 导出记录表 | 未实现 |

## 6. 前后端对齐情况

### 6.1 已对齐的部分

| 业务对象 | 前端 | 后端 | 数据库 |
| --- | --- | --- | --- |
| 用户 | 登录页、用户管理 | Auth、AdminUser | `user_account` |
| 任务 | 任务列表、新建、详情 | Task Controller/Service | `acquisition_task` |
| Session | 采集列表、采集详情、回放 | Session Controller/Service | `collection_session` |
| Session 导入 | 上传页标准目录 / 包导入 | SessionImportService | `session_import_record`、`data_file`、`data_asset` |
| 文件 | 上传页、任务详情文件 | DataFileService | `data_file` |
| 资产 | 任务详情、Session 详情、导出 | DataAssetService | `data_asset` |
| QC 报告 | 质检页、Session 详情 | QcReportController | `qc_report` |
| 处理任务 | 处理页、任务详情 | ProcessingJobService | `processing_job` |
| 资产血缘 | 处理页、任务详情 | ProcessingJobService | `asset_lineage` |

### 6.2 半对齐的部分

| 模块 | 当前情况 | 风险 |
| --- | --- | --- |
| 质检规则 | 前端有规则管理视图，后端暂无规则实体 | 用户可能误以为规则已经真实生效 |
| 导出 | 前端按 Session 展示可下载资产，后端暂无导出包 | “导出状态”更多是前端推导，不是后端任务状态 |
| 标注 | 前端有标注任务视图，后端无标注任务模型 | 标注结果无法回写形成闭环 |
| 首页统计 | 前端聚合并生成部分展示数据 | 统计口径容易和真实数据库分析不一致 |
| 管理模块 | 导航有设备、流程、存储、字典 | 真实后端管理能力尚未完整提供 |

### 6.3 需要特别区分的两种上传

当前平台同时存在两条上传语义：

| 上传类型 | 业务含义 | 当前定位 |
| --- | --- | --- |
| 普通文件上传 | 给任务补充单个或多个文件 | 兼容手工数据录入 |
| Session 包导入 | 把一次采集 Session 作为整体导入平台 | 平台目标主链路 |

这两条链路都能生成资产，但语义不同。

后续 UI 和文档应持续强调：

```text
采集 Session 作为整体进入平台，优先走 Session 导入；
零散文件补充，才走普通文件上传。
```

## 7. 已实现能力清单

### 7.1 平台基础能力

| 能力 | 状态 |
| --- | --- |
| 用户登录 | 已实现 |
| 当前用户查询 | 已实现 |
| 退出登录 | 已实现 |
| 管理员用户列表 | 已实现 |
| 管理员创建用户 | 已实现 |
| 管理员更新用户 | 已实现 |
| 启用/禁用用户 | 已实现 |

### 7.2 任务与 Session

| 能力 | 状态 |
| --- | --- |
| 创建采集任务 | 已实现 |
| 查询任务列表 | 已实现 |
| 查询任务详情 | 已实现 |
| 查询全局 Session 列表 | 已实现 |
| 查询任务下 Session | 已实现 |
| 查询 Session 详情 | 已实现 |
| 查询 Session 回放数据 | 已实现 |
| Session 导入记录 | 已实现 |

### 7.3 上传与数据入库

| 能力 | 状态 |
| --- | --- |
| Multipart 文件上传 | 已实现 |
| OSS 直传初始化 | 已实现 |
| 前端 OSS multipart 上传 | 已实现 |
| OSS 上传完成登记 | 已实现 |
| 标准 Session 包上传 | 已实现 |
| Session 导入 finalize | 已实现 |
| 文件记录入库 | 已实现 |
| 资产业务记录入库 | 已实现 |
| 外部资产登记 | 后端已实现，上传页仅预留入口 |

### 7.4 数据质量与处理

| 能力 | 状态 |
| --- | --- |
| 基础文件级 QC | 已实现 |
| QC 报告查询 | 已实现 |
| 质检结果页面 | 已实现 |
| 质检规则页面 | 前端占位 |
| 可用处理流程判断 | 已实现 |
| 处理任务创建 | 已实现 |
| 手工处理结果登记 | 已实现 |
| 输出资产生成 | 已实现 |
| 资产血缘记录 | 已实现 |

### 7.5 查看、回放与导出

| 能力 | 状态 |
| --- | --- |
| 任务详情查看 | 已实现 |
| Session 列表查看 | 已实现 |
| Session 详情查看 | 已实现 |
| 数据按 Session 聚合展示 | 已实现 |
| Session 回放入口 | 已实现 |
| 按 Session 展示导出资产 | 已实现 |
| 独立导出包生成 | 未实现 |

## 8. 规划中或占位的能力

### 8.1 质检规则系统

当前前端已经把质检拆成“规则”和“结果”，但后端仍以基础文件检查为主。

后续完整链路应补齐：

| 能力 | 目标 |
| --- | --- |
| 规则实体 | 持久化规则名称、类型、适用对象、优先级、启用状态 |
| 规则绑定 | 支持任务、Profile、Session 绑定规则组 |
| 执行计划 | 上传或导入时按规则组执行 |
| Session 级结果 | 生成明确的 Session 级 QC 结论 |
| 文件级结果 | 保留单文件检查明细 |

### 8.2 导出包管理

当前导出页已经按 Session 展示，但后端没有真正的导出任务模型。

后续完整链路应补齐：

| 能力 | 目标 |
| --- | --- |
| 导出任务 | 按 Session 创建导出任务 |
| 导出配置 | 选择资产、格式、压缩方式、是否包含 manifest |
| 导出产物 | 生成独立可下载包 |
| 导出状态 | 记录等待、处理中、成功、失败 |
| 导出历史 | 可查看历史导出记录 |

### 8.3 标注系统

当前标注页面还不是完整标注系统。

后续完整链路应补齐：

| 能力 | 目标 |
| --- | --- |
| 标注任务模型 | 按 Session 或资产创建标注任务 |
| 标注工具链接 | 接入真实标注工具 |
| 标注结果回写 | 标注产物作为资产入库 |
| 标注血缘 | 建立原始资产到标注资产的关系 |

### 8.4 采集端对接

平台前端已有 `collector` 原型页，可与本地采集服务交互。

当前定位更像实验性联调入口：

| 能力 | 当前状态 |
| --- | --- |
| 创建采集端任务 | 已有前端调用 |
| 获取设备状态 | 已有前端调用 |
| 当前采集状态 | 已有前端调用 |
| 实时状态通道 | 前端支持 WebSocket |
| 保存/丢弃/上传 Session | 前端已有调用入口 |

后续需要与正式采集端统一接口、鉴权、Session 编号、上传协议和错误恢复策略。

### 8.5 管理配置

当前用户管理是实功能，其余管理入口偏预留。

后续可以补齐：

| 模块 | 目标 |
| --- | --- |
| 设备管理 | 管理采集设备、采集端客户端、在线状态 |
| 流程管理 | 管理处理 pipeline 与参数模板 |
| 存储管理 | 管理 OSS bucket、路径策略、归档策略 |
| 字典管理 | 管理资产类型、动作类型、Profile 字典 |

## 9. 当前核心链路判断

当前平台最稳定、最接近目标形态的链路是：

```text
平台创建任务
  -> 上传标准 Session 包
  -> 后端校验 manifest 与文件
  -> 创建 collection_session
  -> 写入 data_file / data_asset
  -> 生成基础 QC
  -> 前端以 Session 维度查看数据
  -> 后续进入处理、回放、导出查看
```

当前仍需要兼容的链路是：

```text
平台创建任务
  -> 普通文件上传
  -> 后端创建文件与资产
  -> 前端构造或归并到手工上传 Session 视图
```

这条兼容链路可以保留，但不应成为平台的主叙事。

平台主叙事应该继续收敛为：

```text
任务是业务容器；
采集是一次 Session；
数据是 Session 下的文件与资产明细；
质检、处理、导出都围绕 Session 展开。
```

## 10. 当前主要风险

| 风险 | 说明 | 影响 |
| --- | --- | --- |
| 前端聚合逻辑较重 | 很多页面状态由 `platform.ts` 聚合多个 API 生成 | 后端接口变化时前端易漂移 |
| 规则页面与后端能力不一致 | 质检规则前端已展示，后端未真正执行 | 用户可能误判系统能力 |
| 导出状态不是后端状态机 | 当前导出状态主要由资产是否可下载推导 | 难以支撑正式导出任务追踪 |
| Session 级 QC 未闭环 | 后端主要产出文件级 QC | 无法形成完整采集质量结论 |
| 手工上传与 Session 导入语义并存 | 两条链路都会生成资产 | UI 和文档需要持续区分 |
| 管理入口过多 | 部分入口是占位 | 容易让用户以为已有完整后台管理 |

## 11. 后续演进建议

短期应优先保证主链路稳定：

| 优先级 | 建议 |
| --- | --- |
| P0 | 明确 Session 导入是平台主上传链路，普通文件上传是补充链路 |
| P0 | 后端补充 Session 级详情聚合接口，减少前端 `platform.ts` 的业务推导 |
| P1 | 落地质检规则实体、绑定关系和 Session 级 QC 结果 |
| P1 | 建立导出任务与导出包模型 |
| P1 | 将采集端上传协议与平台 Session 导入协议完全对齐 |
| P2 | 补齐标注任务、设备管理、流程管理等后台配置能力 |
| P2 | 将首页趋势和统计从前端模拟改为后端真实统计 |

## 12. 现状一句话

当前系统已经具备“任务 -> Session -> 上传/导入 -> 文件与资产入库 -> 查看/回放/基础 QC/处理记录”的 MVP 主链路；尚未完整闭环的是规则化质检、正式导出包、真实标注系统、采集端生产级对接和后台配置管理。
