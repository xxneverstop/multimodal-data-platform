# MMDP v0.5.0 部署方案：G1 数据处理 + 多 Worker 架构

> **版本变更说明**（相对于 v0.4.0）：
> - 新增 Pipeline：MOTION_PHYSICS_METRICS_V2（SMPL 物理指标）、G1_MERGE_CAMERA_ROBOT（ZED SVO2 + 机器人 HDF5 合并）、G1_CONVERT_TO_LEROBOT（LeRobot 训练格式转换）、BUILD_MOTION_VIEWER_DATA、BUILD_PLAYBACK_BUNDLE、BUILD_LOOPED_PLAYBACK
> - Worker Docker 镜像新增 torch（CPU 版）、opencv-python、h5py、scipy、lerobot 依赖
> - Worker 新增 `body_model/` 子包（SMPL BodyModel 前向计算）和 `smpl_models/` 模型文件
> - 新增 `pipeline_definition` 表（Pipeline 注册）、`sys_user` 表（用户认证）、`motion_annotation` 表（动作标注）、`profile_pipeline` 表（Profile-Pipeline 关联）
> - **多 Worker 架构设计**：为后续 GPU 服务器就绪做好准备（详见第八节）
> - 本次部署仅上线 CPU Worker，GPU Pipeline（G1_MERGE_CAMERA_ROBOT）标记为 `enabled=0`

---

## 一、变更范围总览

### 1.1 后端变更

| 文件 | 变更 |
|------|------|
| `AssetType.java` | +5 新类型：PHYSICS_REPORT_V2, G1_ROBOT_HDF5, G1_CAMERA_SVO2, G1_MERGED_HDF5, G1_LEROBOT_PARQUET |
| `WorkerManifestService.java` | 从内存注册表读取，不再依赖文件 |
| `WorkerPipelineRegistry.java` | 新组件，内存注册表（`replaceAll` 逻辑，多 Worker 需后续修复） |
| `ProcessingJobServiceImpl.java` | 新增 `claimJob`（按 input_asset_types 过滤文件）、`createSessionJob`（注册表校验） |
| `WorkerJobController.java` | 新增 `POST /api/worker/pipelines/register` 和 `GET /api/worker/pipelines/registry` |
| `MmdpBackendApplication.java` | 启动时初始化 Pipeline 定义 |
| `schema.sql` | 4 个新表 + 现有表字段/索引调整 |

### 1.2 Worker 变更

| 文件 | 变更 |
|------|------|
| `requirements.txt` | +numpy, h5py, scipy, opencv-python, lerobot |
| `config.py` | +MMDP_SMPL_MODEL_DIR, +MMDP_PHYSICS_DEVICE, +.env 自动加载 |
| `pipelines/base.py` | `manifest()` 返回 camelCase JSON（与后端 API 一致） |
| `pipelines/__init__.py` | 跳过 `body_model` 子包 |
| `pipelines/body_model/` | **新增**：SMPL BodyModel (torch) |
| `pipelines/motion_physics_metrics_v2.py` | **新增**：12 项物理质量指标 |
| `pipelines/g1_merge_camera_robot.py` | **新增**：ZED SVO2 + HDF5 合并（需 ZED SDK + CUDA） |
| `pipelines/g1_convert_to_lerobot.py` | **新增**：HDF5 → LeRobot 训练格式 |
| `pipelines/build_looped_playback.py` | **新增**：循环播放视频 |
| `pipelines/build_motion_viewer_data.py` | **新增**：3D 动作查看器数据 |
| `pipelines/build_playback_bundle.py` | **新增**：播放包构建 |

### 1.3 部署配置变更

| 文件 | 变更 |
|------|------|
| `deploy/worker/Dockerfile` | +阿里云镜像源，torch CPU 版单独安装层，+smpl_models/ COPY |
| `deploy/worker/smpl_models/` | **新增**：SMPL-H male/female/neutral 模型文件 |
| `deploy/sql/g1_robot_profile.sql` | **新增**：G1 Pipeline 定义 + Profile 配置 |
| `deploy/.env.example` | 需新增 MMDP_SMPL_MODEL_DIR, MMDP_PHYSICS_DEVICE |
| `deploy/docker-compose.yml` | Worker 服务需新增环境变量 |
| `deploy/initdb/schema.sql` | 4 个新表 + 索引优化 + 字段调整 |

---

## 二、本地打包

### 2.1 前置条件

- JDK 21、Maven 3.9+
- Node.js + npm
- `deploy/worker/smpl_models/` 已放置 SMPL-H 模型文件（male/female/neutral 三个目录，含 `.npy` 文件）

### 2.2 执行打包

```bash
# 方式1：一键打包（推荐）
双击项目根目录 copy-deploy.bat

# 方式2：手动打包
cd mmdp-backend && mvn clean package -DskipTests
cd mmdp-frontend && npm run build
# 然后手动复制产物到 deploy/ 对应子目录
```

### 2.3 打包后检查清单

执行 `copy-deploy.bat` 后，确认以下目录和文件存在：

```
deploy/
├── .env                          ✅ 已填真实密钥
├── .env.example                  ✅
├── docker-compose.yml            ✅
├── backend/
│   ├── Dockerfile                ✅
│   └── mmdp-backend.jar          ✅ > 40MB
├── frontend/
│   └── dist/
│       └── index.html            ✅
├── worker/
│   ├── Dockerfile                ✅ 含 torch + smpl_models
│   ├── smpl_models/
│   │   ├── male/                 ✅
│   │   ├── female/               ✅
│   │   └── neutral/              ✅
│   └── src/
│       ├── main.py               ✅
│       ├── config.py             ✅ 含 .env 自动加载
│       ├── requirements.txt      ✅ 含 lerobot
│       └── pipelines/
│           ├── body_model/       ✅ 子包
│           ├── g1_merge_camera_robot.py  ✅
│           ├── g1_convert_to_lerobot.py  ✅
│           └── motion_physics_metrics_v2.py ✅
├── initdb/
│   └── schema.sql                ✅ 新表结构
├── sql/
│   └── g1_robot_profile.sql      ✅
└── nginx/
    └── nginx.conf                ✅
```

---

## 三、部署前准备工作（重要！）

### 3.1 更新 docker-compose.yml

Worker 服务需新增 `MMDP_SMPL_MODEL_DIR` 和 `MMDP_PHYSICS_DEVICE` 环境变量：

```yaml
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
      MMDP_SMPL_MODEL_DIR: ${MMDP_SMPL_MODEL_DIR:-/app/smpl_models}       # ← 新增
      MMDP_PHYSICS_DEVICE: ${MMDP_PHYSICS_DEVICE:-cpu}                     # ← 新增
    networks:
      - mmdp-net
```

### 3.2 更新 .env.example

```ini
# ---- Python Worker (v0.5.0 新增) ----
# SMPL-H 模型文件目录（容器内路径，Dockerfile 已 COPY 到 /app/smpl_models）
MMDP_SMPL_MODEL_DIR=/app/smpl_models
# 物理指标计算设备：cpu 或 cuda（当前服务器 CPU 机型，设为 cpu）
MMDP_PHYSICS_DEVICE=cpu
```

### 3.3 更新服务器 .env

在服务器 `/data/mmdp/deploy/.env` 末尾追加：

```ini
# ---- Python Worker (v0.5.0 新增) ----
MMDP_SMPL_MODEL_DIR=/app/smpl_models
MMDP_PHYSICS_DEVICE=cpu
```

---

## 四、数据库迁移

> ⚠️ **重要**：服务器已有生产数据，不要用 `initdb/schema.sql` 直接覆盖（它只对 MySQL 首次启动自动执行生效）。需要手动执行增量 SQL。

### 4.1 创建新表 + 修改现有表

在服务器上通过 Navicat SSH 隧道连接（见部署文档 8.6），依次执行以下 SQL 块。

#### 4.1.1 新表 1：Pipeline 定义表

```sql
CREATE TABLE IF NOT EXISTS pipeline_definition (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Pipeline主键ID',
    pipeline_id VARCHAR(64) NOT NULL COMMENT 'Pipeline编码',
    display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    description VARCHAR(512) NULL COMMENT '描述',
    input_asset_types JSON NULL COMMENT '所需输入资产类型列表',
    output_asset_types JSON NULL COMMENT '产出资产类型列表',
    executor_type VARCHAR(32) NOT NULL DEFAULT 'PYTHON_WORKER' COMMENT '执行方式',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE INDEX uk_pipeline_id (pipeline_id)
) COMMENT='处理Pipeline定义表';
```

#### 4.1.2 新表 2：用户表

```sql
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户主键ID',
    username VARCHAR(64) NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
    display_name VARCHAR(128) NOT NULL COMMENT '展示名称',
    role_code VARCHAR(32) NOT NULL COMMENT '角色编码',
    is_admin TINYINT NOT NULL DEFAULT 0 COMMENT '是否管理员',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态',
    phone VARCHAR(32) NULL COMMENT '手机号',
    email VARCHAR(128) NULL COMMENT '邮箱',
    remark VARCHAR(512) NULL COMMENT '备注',
    last_login_at DATETIME NULL COMMENT '最近登录时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    UNIQUE INDEX uk_sys_user_username (username),
    INDEX idx_sys_user_role_code (role_code),
    INDEX idx_sys_user_status (status)
) COMMENT='平台用户表';
```

#### 4.1.3 新表 3：动作标注表

```sql
CREATE TABLE IF NOT EXISTS motion_annotation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标注记录主键ID',
    asset_id BIGINT NOT NULL COMMENT '关联数据资产ID',
    status VARCHAR(32) NOT NULL DEFAULT 'UNANNOTATED' COMMENT '标注状态: UNANNOTATED/IN_PROGRESS/ANNOTATED',
    quality_rating VARCHAR(8) NULL COMMENT '质量评级: A/B/C/D',
    motion_tags JSON NULL COMMENT '动作标签JSON数组',
    frame_issues JSON NULL COMMENT '帧级问题标注JSON数组',
    overall_comment VARCHAR(1024) NULL COMMENT '综合评语',
    annotator_id BIGINT NULL COMMENT '标注人用户ID',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    motiondb_defects JSON NULL COMMENT 'MotionDB风格缺陷评估: {testSet,flatScene,jointJump,jointDeformity,sliding,floatPenetrate,displacementMissing,temporalConsistency}',
    text_descriptions JSON NULL COMMENT '多条文本描述JSON数组',
    CONSTRAINT fk_annotation_asset FOREIGN KEY (asset_id) REFERENCES data_asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_annotation_annotator FOREIGN KEY (annotator_id) REFERENCES sys_user(id),
    UNIQUE INDEX uk_annotation_asset_id (asset_id),
    INDEX fk_annotation_annotator (annotator_id),
    INDEX idx_annotation_status (status),
    INDEX idx_annotation_quality_rating (quality_rating)
) COMMENT='动作标注表,与data_asset一对一';
```

#### 4.1.4 新表 4：Profile-Pipeline 关联表

```sql
CREATE TABLE IF NOT EXISTS profile_pipeline (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Profile-Pipeline关联主键',
    profile_id BIGINT NOT NULL COMMENT '关联Profile ID',
    pipeline_id VARCHAR(64) NOT NULL COMMENT '关联Pipeline ID',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    CONSTRAINT fk_pp_profile FOREIGN KEY (profile_id) REFERENCES collection_profile(id),
    CONSTRAINT fk_pp_pipeline FOREIGN KEY (pipeline_id) REFERENCES pipeline_definition(pipeline_id),
    UNIQUE INDEX uk_profile_pipeline (profile_id, pipeline_id),
    INDEX fk_pp_pipeline (pipeline_id)
) COMMENT='Profile与Pipeline关联表';
```

#### 4.1.5 现有表修改

```sql
-- acquisition_task: subject_code 和 action_name 改为可选
ALTER TABLE acquisition_task
    MODIFY subject_code VARCHAR(64) NULL COMMENT '被试编号（可选，优先从Session获取）',
    MODIFY action_name VARCHAR(128) NULL COMMENT '采集动作名称（可选，优先从Session获取）';

-- collection_session: upload_status 默认值改为 UPLOADED
ALTER TABLE collection_session
    MODIFY upload_status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT '上传状态';

-- processing_job: error_message 改为 TEXT（支持长错误信息）
ALTER TABLE processing_job
    MODIFY error_message TEXT NULL COMMENT '错误信息';

-- processing_job: 新增依赖作业字段（如果不存在）
ALTER TABLE processing_job
    ADD COLUMN IF NOT EXISTS depends_on_job_ids VARCHAR(512) NULL COMMENT '依赖的前置Job ID列表(JSON数组), 如[1,2], NULL表示无前置依赖'
    AFTER updated_at;

-- 新增索引（如果不存在）
CREATE INDEX IF NOT EXISTS fk_session_subject ON collection_session(subject_id);
CREATE INDEX IF NOT EXISTS fk_data_file_task ON data_file(task_id);
CREATE INDEX IF NOT EXISTS fk_qc_report_task ON qc_report(task_id);
CREATE INDEX IF NOT EXISTS fk_qc_report_file ON qc_report(file_id);
CREATE INDEX IF NOT EXISTS fk_data_asset_task ON data_asset(task_id);
CREATE INDEX IF NOT EXISTS fk_data_asset_file ON data_asset(file_id);
CREATE INDEX IF NOT EXISTS fk_asset_lineage_task ON asset_lineage(task_id);
CREATE INDEX IF NOT EXISTS fk_asset_lineage_source_asset ON asset_lineage(source_asset_id);
CREATE INDEX IF NOT EXISTS fk_asset_lineage_target_asset ON asset_lineage(target_asset_id);
CREATE INDEX IF NOT EXISTS fk_asset_lineage_job ON asset_lineage(job_id);
CREATE INDEX IF NOT EXISTS fk_session_import_record_task ON session_import_record(task_id);
CREATE INDEX IF NOT EXISTS fk_session_import_record_session ON session_import_record(session_record_id);
CREATE INDEX IF NOT EXISTS fk_session_import_record_archive_file ON session_import_record(archive_file_id);
CREATE INDEX IF NOT EXISTS idx_processing_job_task_status ON processing_job(task_id, status);
CREATE INDEX IF NOT EXISTS idx_processing_job_session_status ON processing_job(session_id, status);
```

### 4.2 插入 Pipeline 定义

```sql
-- CPU Pipeline（本次上线）
INSERT INTO pipeline_definition (pipeline_id, display_name, description, input_asset_types, output_asset_types, executor_type, enabled, created_at, updated_at)
VALUES
('BUILD_PLAYBACK', '图像序列→MP4', '将图像序列帧合成为MP4视频播放文件', '[]', '["RGB_VIDEO_MP4"]', 'PYTHON_WORKER', 1, NOW(), NOW()),
('BUILD_STEREO_MP4', '双目图像→并排MP4', '将双目图像并排合成为单个MP4视频', '[]', '["RGB_VIDEO_MP4"]', 'PYTHON_WORKER', 1, NOW(), NOW()),
('STEREO_IMU_ALIGN', '双目IMU时间对齐', '将双目图像时戳与IMU数据时间对齐', '["RGB_IMAGE_LEFT", "RGB_IMAGE_RIGHT", "IMU_CSV"]', '["IMU_ALIGNED_CSV", "ALIGNMENT_REPORT"]', 'PYTHON_WORKER', 1, NOW(), NOW()),
('BUILD_PLAYBACK_BUNDLE', '播放包构建', '构建完整播放包（视频+IMU曲线+同步索引）', '["RGB_VIDEO_MP4", "IMU_CSV"]', '["PLAYBACK_BUNDLE"]', 'PYTHON_WORKER', 1, NOW(), NOW()),
('BUILD_LOOPED_PLAYBACK', '循环播放视频', '将短动作序列循环播放为指定时长视频', '["RGB_VIDEO_MP4"]', '["RGB_VIDEO_MP4"]', 'PYTHON_WORKER', 1, NOW(), NOW()),
('BUILD_MOTION_VIEWER_DATA', '3D动作查看器数据', '将SMPL NPZ转换为前端3D MotionViewer可渲染格式', '["SMPL_NPZ"]', '["MOTION_VIEWER_JSON"]', 'PYTHON_WORKER', 1, NOW(), NOW()),
('MOTION_PHYSICS_METRICS', '物理质量指标V1', '基于SMPL关节计算的物理质量指标V1版本', '["SMPL_RESULT"]', '["PHYSICS_REPORT"]', 'PYTHON_WORKER', 1, NOW(), NOW()),
('MOTION_PHYSICS_METRICS_V2', '物理质量指标V2', '基于SMPL BodyModel前向计算的完整物理质量指标（12项）', '["SMPL_NPZ"]', '["PHYSICS_REPORT_V2"]', 'PYTHON_WORKER', 1, NOW(), NOW()),
('G1_CONVERT_TO_LEROBOT', 'G1数据→LeRobot训练格式', '将合并后的G1 HDF5数据转换为LeRobot VLA训练格式（Parquet+Video）', '["G1_MERGED_HDF5"]', '["G1_LEROBOT_PARQUET"]', 'PYTHON_WORKER', 1, NOW(), NOW()),
('G1_MERGE_CAMERA_ROBOT', 'G1相机-机器人数据合并', '将ZED双目相机SVO2数据与机器人HDF5遥操作数据合并', '["G1_ROBOT_HDF5", "G1_CAMERA_SVO2"]', '["G1_MERGED_HDF5"]', 'PYTHON_WORKER', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE display_name=VALUES(display_name), updated_at=NOW();
```

> ⚠️ **注意**：`G1_MERGE_CAMERA_ROBOT` 的 `enabled=0`，它需要 ZED SDK + CUDA，等 GPU 服务器就绪后再启用。

### 4.3 插入 G1 Profile 配置

执行 `deploy/sql/g1_robot_profile.sql`（该文件已含 DELETE 清理 + INSERT + 验证查询，可重复执行）。或在 Navicat 中直接打开该文件执行。

---

## 五、服务器部署

### 5.1 上传部署包

```bash
# 本地执行
scp -i your-key.pem -r deploy/ root@<服务器IP>:/data/mmdp/
```

> 上传期间现有服务不受影响（容器在运行，只有执行 `docker compose up -d --build` 时才会重建）。

### 5.2 更新 .env

```bash
# SSH 到服务器
cd /data/mmdp/deploy
vim .env
```

在文件末尾追加：

```ini
# ---- Python Worker (v0.5.0 新增) ----
MMDP_SMPL_MODEL_DIR=/app/smpl_models
MMDP_PHYSICS_DEVICE=cpu
```

### 5.3 执行数据库迁移

通过 Navicat SSH 隧道连接，按顺序执行第四章的 SQL：
1. 创建新表（4.1.1 ~ 4.1.4）
2. 修改现有表（4.1.5）
3. 插入 Pipeline 定义（4.2）
4. 插入 G1 Profile（4.3）

### 5.4 更新镜像并启动

```bash
cd /data/mmdp/deploy

# 只重建 Worker（JAR 也更新了所以 backend 也要重建）
docker compose up -d --build
```

预计中断时间：Backend 重建约 10-20 秒，Worker 重建约 1-2 分钟（pip install torch + 依赖）。

### 5.5 验证

```bash
# 1. 四个容器均 Up
docker compose ps
# 期望: mmdp-mysql (healthy), mmdp-backend Up, mmdp-worker Up, mmdp-nginx Up

# 2. Worker 日志检查
docker compose logs worker --tail 30
# 期望看到 11 个 Pipeline 注册成功
# 特别注意: MOTION_PHYSICS_METRICS_V2, G1_CONVERT_TO_LEROBOT 应该是 [OK]
# G1_MERGE_CAMERA_ROBOT 也会注册但 DB 中 enabled=0，前端不会显示

# 3. Backend 日志检查
docker compose logs backend --tail 20
# 期望: Pipeline 定义初始化成功，Worker 注册表收到 11 个 Pipeline

# 4. 注册表验证
curl http://localhost/api/worker/pipelines/registry
# 期望返回 11 个 Pipeline 的 JSON 列表

# 5. 前端访问验证
curl -I http://localhost/
```

---

## 六、启动顺序与时间线

```
T+0s     上传 deploy/ 目录（覆盖）
T+10s    执行数据库迁移 SQL
T+30s    docker compose up -d --build
T+35s    MySQL 已运行（无变化）
T+45s    Backend 重建完成（新 JAR 启动）
T+120s   Worker 重建完成（pip install torch + 依赖最耗时）
T+130s   Worker 向 Backend 注册 11 个 Pipeline
T+135s   系统就绪 ✅
```

> 总停机时间：约 30-60 秒（Backend 重建期间 API 不可用）。Worker 重建期间不影响已运行的 API。

---

## 七、回退方案

如果部署出现问题需要回退：

```bash
cd /data/mmdp/deploy

# 1. 停止服务
docker compose down

# 2. 恢复旧版 JAR（假设已备份）
cp backup/mmdp-backend.jar backend/

# 3. 恢复旧版 docker-compose.yml（如有改动）
git checkout -- docker-compose.yml

# 4. 数据库：由于只新增了表和索引，没有删除或修改数据，无需回退
#    如果必须清除新表：
#    docker exec mmdp-mysql mysql -uroot -p<密码> mmdp_db -e "
#      DROP TABLE IF EXISTS profile_pipeline;
#      DROP TABLE IF EXISTS pipeline_definition;
#      DROP TABLE IF EXISTS motion_annotation;
#    "

# 5. 重新启动旧版
docker compose up -d
```

---

## 八、多 Worker 架构方案（供后续实施）

### 8.1 当前问题

`WorkerPipelineRegistry.replaceAll()` 是全量替换逻辑（`clear()` + `putAll`），后注册的 Worker 会覆盖先注册的。多 Worker 场景下会导致 Pipeline 注册表不完整。

### 8.2 目标架构

```
当前服务器 (7.1GB CPU)                   GPU 服务器 (按量计费, cn-hangzhou)
┌──────────────────────────┐          ┌──────────────────────────────┐
│ mmdp-nginx :80           │          │                              │
│ mmdp-backend :19021      │◄─────────│ mmdp-worker-gpu              │
│ mmdp-mysql :3306         │  内网    │  ├ G1_MERGE_CAMERA_ROBOT     │
│ mmdp-worker (CPU)        │  HTTP    │  ├ MOTION_PHYSICS_METRICS_V2 │
│  ├ BUILD_PLAYBACK        │          │  └ 未来 GPU Pipeline         │
│  ├ BUILD_STEREO_MP4      │          │                              │
│  ├ STEREO_IMU_ALIGN      │          │ 基础镜像: nvidia/cuda:12.1   │
│  ├ BUILD_PLAYBACK_BUNDLE │          │ + ZED SDK 4.x               │
│  ├ BUILD_LOOPED_PLAYBACK │          │ + CUDA torch                │
│  ├ BUILD_MOTION_VIEWER   │          └──────────────────────────────┘
│  ├ MOTION_PHYSICS_METRICS│
│  ├ MOTION_PHYSICS_V2     │          中间存储
│  └ G1_CONVERT_TO_LEROBOT │          ┌──────────────────────────────┐
│                          │          │ 阿里云 OSS (cn-hangzhou)     │
│ ZED SDK ❌  CUDA ❌      │          │ 数据上传/处理产物均通过 OSS   │
└──────────────────────────┘          └──────────────────────────────┘
```

### 8.3 需要改的代码

| 组件 | 改动 | 优先级 |
|------|------|--------|
| `WorkerPipelineRegistry` | `replaceAll` → `merge`（按 workerId 追加，心跳过期清理） | P0 |
| Worker `config.py` | 新增 `MMDP_WORKER_ID`、`MMDP_WORKER_PIPELINE_FILTER` 环境变量 | P0 |
| Worker `main.py` | 注册时携带 workerId，claim 时携带 workerId | P0 |
| `ProcessingJobServiceImpl.claimJob()` | 按 workerId 的 Pipeline 能力过滤可领取的 Job | P0 |
| `Pipeline` 基类 | 新增 `requires_hardware: "CPU" | "GPU"` 类属性 | P0 |
| `docker-compose.yml` | GPU Worker 服务定义（`deploy.resources.reservations.devices`） | P1 |
| GPU Worker Dockerfile | 基于 `nvidia/cuda:12.1-runtime-ubuntu22.04` + ZED SDK | P1 |

### 8.4 GPU 实例按量自动化流程

```
用户在前端点击"执行处理" → 创建 ProcessingJob (status=CREATED)
                                    ↓
                            CPU Worker 轮询 → 检查 pipeline 是否为 GPU 类型
                              ├── CPU Pipeline → 正常领取处理
                              └── GPU Pipeline → 跳过（不领取）
                                    ↓
                            GPU Job 等待队列中...
                                    ↓
                    触发 GPU Worker 启动（手动 / 定时 / API）
                                    ↓
┌────────── GPU 实例自动化 ──────────┐
│ 1. aliyun CLI 启动 GPU ECS 实例    │  ← 从"停止"→"运行", ~60s
│ 2. Docker daemon 就绪              │
│ 3. docker compose up -d worker-gpu │  ← 仅启动 Worker 容器
│ 4. Worker 注册 GPU Pipeline        │
│ 5. Worker 领取并处理 GPU Job       │  ← 正常处理
│ 6. 处理完成，无待处理 GPU Job       │
│ 7. aliyun CLI 停止 GPU ECS 实例    │  ← "运行"→"停止", 费用停止
└────────────────────────────────────┘
```

自动化脚本示例（`/data/mmdp/scripts/gpu-worker.sh`）：

```bash
#!/bin/bash
# GPU Worker 按需启动脚本
# 用法: ./gpu-worker.sh start | stop | status

GPU_INSTANCE_ID="i-xxxx"  # GPU ECS 实例 ID
GPU_IP="10.0.x.x"         # GPU 实例内网 IP

case "$1" in
  start)
    echo "启动 GPU 实例..."
    aliyun ecs StartInstance --InstanceId $GPU_INSTANCE_ID
    echo "等待实例就绪..."
    aliyun ecs WaitInstanceReady --InstanceId $GPU_INSTANCE_ID
    echo "等待 Worker 注册..."
    sleep 30
    curl -s http://$GPU_IP:19021/api/worker/pipelines/registry | jq .
    ;;
  stop)
    echo "停止 GPU 实例..."
    aliyun ecs StopInstance --InstanceId $GPU_INSTANCE_ID
    ;;
  status)
    aliyun ecs DescribeInstanceStatus --InstanceId $GPU_INSTANCE_ID
    ;;
esac
```

### 8.5 GPU 实例费用估算（cn-hangzhou）

| 实例规格 | GPU | vCPU | 内存 | 按量（元/小时） | 包月（元/月） |
|---------|-----|------|------|---------------|-------------|
| `ecs.gn7i-c8g1.2xlarge` | 1×T4 | 8 | 16GB | ~5.5 | ~1500 |
| `ecs.gn6v-c8g1.2xlarge` | 1×V100 | 8 | 32GB | ~15 | ~4000 |

> 按每天使用 2 小时计算：T4 ≈ ¥330/月，远低于包月的 ¥1500。
> 
> 云盘费用（即使实例停止也收取）：40GB ESSD ~¥0.1/天 ≈ ¥3/月，可忽略。

---

## 九、验证清单

部署完成后逐项检查：

- [ ] `docker compose ps` 四个容器均为 Up
- [ ] `curl http://localhost/api/tasks` 返回 JSON
- [ ] 前端页面正常加载（浏览器访问 `http://<公网IP>/`）
- [ ] Worker 日志显示 11 个 Pipeline 注册成功
- [ ] `curl http://localhost/api/worker/pipelines/registry` 返回 11 条
- [ ] 前端处理管理页面能看到 9 个可用 Pipeline（G1_MERGE_CAMERA_ROBOT 不在列表中，因为 enabled=0）
- [ ] 提交一个 CPU Pipeline 处理任务（如 BUILD_PLAYBACK），确认 Worker 正常领取并执行
- [ ] Navicat 连接确认新表已创建（pipeline_definition, sys_user, motion_annotation, profile_pipeline）
- [ ] OSS 上传下载正常（Worker 能下载输入文件、上传产物）

---

## 十、新增 Pipeline 能力矩阵

| Pipeline | 输入 | 输出 | 硬件需求 | 本次上线 | 状态 |
|----------|------|------|---------|---------|------|
| BUILD_PLAYBACK | 图像序列 | RGB_VIDEO_MP4 | CPU | ✅ | 已上线 |
| BUILD_STEREO_MP4 | 双目图像 | RGB_VIDEO_MP4 | CPU | ✅ | 已上线 |
| STEREO_IMU_ALIGN | 图像+IMU CSV | IMU_ALIGNED_CSV + REPORT | CPU | ✅ | 已上线 |
| BUILD_PLAYBACK_BUNDLE | 视频+IMU | PLAYBACK_BUNDLE | CPU | ✅ | 新增 |
| BUILD_LOOPED_PLAYBACK | 视频 | RGB_VIDEO_MP4 | CPU | ✅ | 新增 |
| BUILD_MOTION_VIEWER_DATA | SMPL_NPZ | MOTION_VIEWER_JSON | CPU | ✅ | 新增 |
| MOTION_PHYSICS_METRICS | SMPL_RESULT | PHYSICS_REPORT | CPU | ✅ | 已上线 |
| MOTION_PHYSICS_METRICS_V2 | SMPL_NPZ | PHYSICS_REPORT_V2 | CPU (慢) / GPU | ✅ | 新增 |
| G1_CONVERT_TO_LEROBOT | G1_MERGED_HDF5 | G1_LEROBOT_PARQUET | CPU | ✅ | 新增 |
| G1_MERGE_CAMERA_ROBOT | HDF5 + SVO2 | G1_MERGED_HDF5 | **GPU + ZED SDK** | ❌ | enabled=0，等 GPU |

---

## 附录 A：torch CPU 版性能参考

| 处理任务 | 数据量 | CPU (当前) | GPU (T4 预估) |
|---------|--------|-----------|--------------|
| MOTION_PHYSICS_METRICS_V2 | 1×NPZ (30s动作) | ~2-5 分钟 | ~10-30 秒 |
| MOTION_PHYSICS_METRICS_V2 | 10×NPZ | ~20-50 分钟 | ~2-5 分钟 |
| G1_MERGE_CAMERA_ROBOT | 1×episode (60s) | ❌ 不可用 | ~1-2 分钟 |

> 当前 CPU 版的 MOTION_PHYSICS_METRICS_V2 可正常工作但速度较慢。批量处理大量数据时建议等 GPU 就绪。

---

## 附录 B：当前服务器资源使用

| 容器 | 内存 | 说明 |
|------|------|------|
| mmdp-mysql | ~530 MB | 数据库 |
| mmdp-backend | ~470 MB | JVM |
| mmdp-worker (旧) | ~30 MB | 空闲，新 Worker 加 torch 后预计 ~500MB-1GB |
| mmdp-nginx | ~3 MB | 反向代理 |
| hbbs + hbbr | ~4 MB | RustDesk 中继 |
| **总计** | **~1.5 GB** | 剩余 ~5.3 GB 可用 |

> 新 Worker 上线后总内存预计 ~2-2.5 GB，剩余充足，不需要调整配置。
