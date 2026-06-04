# 2026-05-18 当前项目状态说明

## 一、文档目的

本文档专门说明当前项目中：

- 哪些链路已经是真实可运行的
- 哪些链路仍然属于 MVP / 占位逻辑
- 哪些环节可以直接拿真实数据测试
- 哪些环节目前还不能算真实业务验证

阅读顺序按“后端当前状态”在前，“前端当前状态”在后组织。

## 二、后端当前详细状态

### 1. 后端目前有两条主线

当前后端已经同时存在两层业务链路：

1. 原有主链路  
   `任务创建 -> 文件上传 -> OSS 存储 -> data_file 入库 -> 基础 QC -> qc_report 入库 -> QC 报告查询`

2. 新增扩展链路  
   `任务创建 -> 数据资产登记 / 上传资产自动建档 -> 可用 pipeline 判断 -> processing job 创建与查询`

这两条链路不是替代关系，而是叠加关系：

- 原有上传与 QC 链路仍然保留
- 新增资产和 pipeline 机制是在原链路之上扩展出来的

### 2. 任务链路当前状态

任务仍然是整个系统的容器。

当前任务字段包括：

- 原有字段：`taskName`、`subjectCode`、`actionName`、`deviceType`、`modality`、`collectDate`、`remark`
- 新增字段：`scene`、`operatorName`、`captureLocation`

任务当前不要求在创建时选择固定 pipeline，也不会把任务类型写死。

这部分已经是**真实链路**：

- 接口真实存在
- 数据真实写入 `acquisition_task`
- 后续文件、资产、QC、processing job 都围绕任务 ID 工作

### 3. 上传文件链路当前状态

上传接口仍然沿用原接口：

- `POST /api/tasks/{taskId}/files`

当前上传链路的实际执行顺序是：

1. 校验任务是否存在
2. 校验文件非空
3. 读取原始文件名、扩展名、内容类型和字节内容
4. 生成 OSS `objectKey`
5. 把文件真实上传到 OSS
6. 更新任务状态为 `UPLOADED`
7. 写入 `data_file`
8. 自动创建 `data_asset`
   - `sourceType = UPLOADED_FILE`
   - `assetType = 用户传入值，默认 OTHER`
9. 调用 QC 服务做同步检查
10. 写入 `qc_report`
11. 根据 QC 结果更新任务状态为 `QC_PASSED / QC_WARNING / QC_FAILED`

这条链路当前是**真实可运行链路**，不是 mock：

- 文件是真实上传到 OSS 的
- 元数据是真实入库的
- QC 是真实执行的
- `data_file` 和 `data_asset` 也是真实落表的

### 4. 数据资产链路当前状态

当前系统已经引入统一的数据资产概念 `data_asset`。

资产分两类来源：

- `UPLOADED_FILE`
- `EXTERNAL_PATH`

支持的 `assetType` 包括：

- `RGB_SEQ_RAW`
- `RGB_VIDEO_MP4`
- `MOCAP_CSV`
- `SMPL_RESULT`
- `CAMERA_PARAM`
- `OTHER`

#### 4.1 上传资产

上传文件后会自动创建一条资产记录，这部分已经接入真实上传链路。

也就是说，现在“文件”和“资产”的关系是：

- `data_file` 记录平台内文件存储与 QC
- `data_asset` 记录这个文件在业务语义上是什么资产

#### 4.2 外部资产

接口：

- `POST /api/tasks/{taskId}/assets/external`

这条链路只登记元数据，不上传文件，不走 OSS，不写 `data_file`。

适用场景包括：

- NAS 路径
- 动捕棚原始目录
- seq 数据目录
- 外部 SMPL 输出目录

这条链路也是**真实链路**，因为：

- 接口存在
- 会真实写 `data_asset`
- 任务资产列表可以真实查询到

但它只是真实“登记”，不是“真实访问和验证外部路径”。

### 5. 任务资产列表链路当前状态

接口：

- `GET /api/tasks/{taskId}/assets`

这条接口会统一返回任务下所有资产，包括：

- 上传资产
- 外部路径资产

它当前也是**真实链路**，因为返回内容来自数据库里的真实资产记录，而不是前端拼接或 mock。

### 6. QC 质检链路当前状态

QC 仍然依赖上传文件触发，不对外部资产单独生成 `qc_report`。

接口：

- `GET /api/tasks/{taskId}/qc-report`

当前 QC 的特点是：

- 真实执行
- 真实写 `qc_report`
- 真实返回结构化 `report_json`

但逻辑本身仍然明显属于**最初 MVP 阶段的基础检查逻辑**。

#### 6.1 仍然保留的最初 QC 逻辑

`txt` 检查仍然是最初那一版逻辑：

- 文件非空
- 是否能读取样例行
- 第一列是否像时间戳
- 抽样行列数是否一致
- 数值列是否能解析

`csv` 检查也仍然是最初 MVP 风格：

- 是否有表头和至少一行数据
- 是否有 `timestamp`
- 是否有 `acc / gyro` 相关字段

`json` 目前仍然只是语法级检查：

- 能否被解析
- 不检查具体业务结构

`bvh / fbx` 仍然没有真实解析：

- 只做扩展名支持判断
- 只给出“内容检查有限”的 warning

#### 6.2 基于 assetType 的轻量增强

这次在原有 QC 上做了小幅增强，但还不是深度业务解析：

`MOCAP_CSV`

- 在原有 CSV 检查上额外看 `quaternion / quat`
- 但不会真正理解动捕 CSV 的完整结构

`SMPL_RESULT`

- 只检查扩展名是否为 `npz / json / pkl`
- 不会读取 SMPL 数据内容

`RGB_VIDEO_MP4`

- 检查扩展名
- 检查 content-type
- 检查大小阈值
- 不会读取视频帧

`RGB_SEQ_RAW`

- 对外部资产场景不做文件级 QC
- 只要求登记路径和必要描述

#### 6.3 QC 当前性质总结

因此，QC 当前应定义为：

- **执行是真实的**
- **规则仍然主要是最初的基础文件检查逻辑**

它适合做：

- 文件是否大致合理
- 格式是否明显错误
- 基础结构是否可读

它还不适合做：

- 真实语义级校验
- 模态间一致性验证
- 深度视频/骨架/SMPL 内容解析

### 7. 可用 pipeline 判断链路当前状态

接口：

- `GET /api/tasks/{taskId}/available-pipelines`

当前只实现了一个 pipeline：

- `RGB_MOCAP_ALIGNMENT`
- 展示名：`RGB/SMPL 与动捕服 CSV 时间对齐`

当前判断规则是：

- 必需资产：`MOCAP_CSV`、`SMPL_RESULT`
- 可选资产：`RGB_VIDEO_MP4`、`RGB_SEQ_RAW`、`CAMERA_PARAM`

当前 `readinessStatus` 只保留两种：

- `READY`
- `MISSING_REQUIRED_ASSETS`

这条链路是**真实判断链路**，因为：

- 它不是写死返回
- 它是根据当前任务下 `data_asset.asset_type` 实时计算出来的

但它当前只判断“资产是否存在”，不判断：

- 资产内容是否真的可对齐
- 这些资产之间是否真的来自同一批数据
- 外部路径是否真实存在和可读

所以它是：

- **真实存在性判断**
- **不是深度业务可执行性验证**

### 8. Processing Job 链路当前状态

接口包括：

- `POST /api/tasks/{taskId}/processing-jobs`
- `GET /api/tasks/{taskId}/processing-jobs`
- `GET /api/processing-jobs/{jobId}`

当前 processing job 的实际流程是：

1. 校验任务存在
2. 校验 `pipelineId` 当前只允许 `RGB_MOCAP_ALIGNMENT`
3. 查询任务下真实资产
4. 检查是否已经具备 `MOCAP_CSV` 和 `SMPL_RESULT`
5. 如果缺失，返回明确错误
6. 如果满足条件，创建 `processing_job`
7. 同步执行 mock executor
8. 写回 `result_json`
9. 状态变为 `SUCCESS` 或 `FAILED`

#### 8.1 这条链路哪些是真实的

以下部分都是真实的：

- job 创建是真实入库
- job 状态流转是真实写库
- job 查询接口是真实可查
- 创建前的资产存在性校验是真实的

#### 8.2 这条链路哪些仍然是占位逻辑

真正占位的是执行器本身。

当前 `RGB_MOCAP_ALIGNMENT` 还没有接 Python 时间对齐脚本，也没有真实 EasyMocap 或真实对齐算法。

当前 `resultJson` 里的字段：

- `offsetMs`
- `matchedFrames`
- `qualityStatus`
- `message`

都是 mock executor 生成的占位结果，不是算法计算结果。

所以 processing job 当前应定义为：

- **结构与状态流转是真实的**
- **算法执行是占位的**

### 9. 哪些后端链路可以拿真实数据测试

当前可以直接用真实数据测试的部分包括：

- 任务创建
- 文件上传到 OSS
- `data_file` 入库
- 上传资产自动建档到 `data_asset`
- 外部资产登记
- 任务资产列表查询
- pipeline readiness 判断
- processing job 创建与查询
- 基础 QC 报告生成与查询

一个建议的真实测试闭环是：

1. 创建一个任务
2. 上传一个真实 `MOCAP_CSV` 文件
3. 登记一个真实 `SMPL_RESULT` 外部路径
4. 查询任务资产列表
5. 查询 available pipelines
6. 创建 processing job
7. 查询 processing job 列表和详情
8. 查询原有 QC 报告

### 10. 哪些后端过程目前不能算真实业务验证

以下部分目前不能算真实算法或真实业务验证：

- `RGB_MOCAP_ALIGNMENT` 的结果
  - 当前只是 mock，不是时间对齐算法结果
- `SMPL_RESULT` 的内容校验
  - 只看扩展名，不看内部结构
- `RGB_VIDEO_MP4` 的内容校验
  - 不读视频帧
- `bvh / fbx` 的业务解析
  - 仍未实现
- 外部路径资产的路径可访问性
  - 当前不会访问或验证外部路径

### 11. 后端当前一句话结论

后端当前已经完成“任务 + 上传文件 + OSS + 基础 QC + 数据资产 + pipeline readiness + processing job”的真实业务骨架，但其中 QC 仍主要是最初的基础检查逻辑，processing job 的算法执行仍是占位实现。

## 三、前端当前详细状态

### 1. 前端目前的页面组织

前端当前主要围绕以下页面工作：

- 任务列表页
- 任务创建页
- 任务详情页
- QC 报告页

其中“任务详情页”已经从原来的文件上传页，扩展为当前的统一工作台。

### 2. 前端当前的任务创建链路

任务创建页现在负责录入采集本身，而不是决定 pipeline。

它当前支持填写：

- `taskName`
- `subjectCode`
- `actionName`
- `deviceType`
- `modality`
- `collectDate`
- `scene`
- `operatorName`
- `captureLocation`
- `remark`

这部分前端已经接真实后端接口，属于**真实交互链路**。

### 3. 前端当前的任务详情页链路

任务详情页现在有五块核心能力：

1. 任务摘要
2. 上传平台内资产
3. 登记外部资产
4. 查看可用 pipeline
5. 查看 processing jobs
6. 保留原有文件列表与 QC 报告入口

这意味着当前详情页已经从“文件上传页”升级为：

- 资产录入中心
- pipeline 判断入口
- processing job 查看入口

### 4. 前端哪些部分已经接真实后端

当前前端已接真实接口的部分包括：

- 创建任务
- 获取任务详情
- 上传文件资产
- 获取文件列表
- 登记外部资产
- 获取资产列表
- 获取可用 pipeline
- 创建 processing job
- 获取 processing job 列表
- 获取 processing job 详情
- 获取 QC 报告

所以当前前端不是静态页面，也不是纯 mock 页面，而是：

- **页面是真实接后端的**
- **交互结果大部分来自真实数据库和真实接口返回**

### 5. 前端哪些展示是真实结果

以下前端展示当前都是真实数据：

- 任务摘要
- 上传后的文件列表
- 任务资产列表
- pipeline readiness
- processing job 状态和列表
- QC 报告页中的结构化报告

这些内容要么来自：

- `acquisition_task`
- `data_file`
- `data_asset`
- `processing_job`
- `qc_report`

因此数据来源是真实的。

### 6. 前端哪些展示虽然来自真实接口，但内容仍是占位

最典型的是 processing job 详情里的 `resultJson`。

这里前端展示的对象本身是真实从后端接口取回来的，但它的内容是后端 mock executor 生成的占位结果，而不是算法真实输出。

因此这一块应理解为：

- **接口真实**
- **结果内容占位**

### 7. 前端哪些流程可以用真实数据测试

当前可以在前端完整验证的流程包括：

1. 新建任务并填写新增字段
2. 在详情页上传一个真实文件资产并选择 `assetType`
3. 在详情页登记一个真实外部路径资产
4. 查看资产列表是否刷新
5. 查看 pipeline readiness 是否变化
6. 创建 processing job
7. 查看 processing job 列表与详情
8. 从文件列表进入 QC 报告页查看基础 QC 结果

### 8. 前端哪些流程目前不能算真实业务验证

以下前端流程虽然可以点通，但还不能算真实业务算法验证：

- processing job 结果展示
  - 因为展示的是后端 mock `resultJson`
- pipeline readiness 的“可执行性”
  - 当前只代表资产存在，不代表数据真的对得齐

### 9. 前端当前一句话结论

前端当前已经完成从“任务 + 文件上传 + QC”到“任务 + 资产 + pipeline + processing job”的详情工作台升级，接口接入是真实的，但 processing result 展示的仍是后端占位结果。

## 四、整体结论

### 1. 当前最真实、最可运行的部分

当前项目里最真实、最适合立即联调和真实测试的部分是：

- 任务创建
- 文件上传
- OSS 存储
- `data_file` / `data_asset` / `qc_report` / `processing_job` 入库
- 基础 QC
- 资产统一管理
- pipeline readiness 判断

### 2. 当前仍然属于 MVP / 占位阶段的部分

当前仍然明显是占位或 MVP 逻辑的部分是：

- `RGB_MOCAP_ALIGNMENT` 的真实时间对齐算法
- Python 脚本接入
- EasyMocap 等真实外部处理流程
- SMPL / 视频 / BVH / FBX 的深度内容解析
- 外部路径资产的可访问性校验

### 3. 当前系统的准确定位

截至 2026-05-18，当前系统已经不再只是“文档 + 原型”，而是一个具备真实任务管理、真实文件上传、真实对象存储、真实基础 QC、真实资产管理和真实 processing job 骨架的平台雏形。

但它还不是一个“真实算法全部接通”的成品平台。更准确地说，它现在是：

- **业务骨架真实**
- **文件链路真实**
- **资产链路真实**
- **QC 执行真实但规则仍偏基础**
- **processing job 结构真实但算法执行仍占位**
