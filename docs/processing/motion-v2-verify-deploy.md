> 保存于 2026-07-09

# MOTION_PHYSICS_METRICS_V2 Pipeline 验证与部署指南

## 这篇文章解决什么问题

前面我们已经把 `MOTION_PHYSICS_METRICS_V2` Pipeline 的代码写好了——一个依赖 torch 和 SMPL 人体模型的完整物理指标计算管道。代码放在那里，但要让它在真实环境中跑起来，还需要做一系列验证和部署工作。

这篇文章分两条线来讲：**本地开发验证**和**服务器部署**。两条线的环境差异很大，搞清楚各自的前提条件能省去大量排查时间。

## 本地开发环境的配置机制（重要）

在讲具体验证步骤之前，必须先搞清楚本地开发时后端和 Worker 各自是怎么拿到配置的。搞清楚这一点，"为什么后端能连上数据库但 Worker 报 OSS 密钥为空"这种问题就不会出现了。

### 后端：自动读 `.env` 文件

Spring Boot 后端在 `application.yml` 中配置了这样一行（源码：`mmdp-backend/src/main/resources/application.yml:7`）：

```yaml
spring:
  config:
    import: optional:file:.env[.properties]
```

这行配置的意思是：启动时自动尝试加载当前工作目录下的 `.env` 文件，将其中的键值对注入到 Spring 配置上下文中。`optional:` 前缀表示文件不存在也不报错。

实际使用的 `.env` 文件位于 `mmdp-backend/.env`，里面定义了远端 MySQL 连接串和阿里云 OSS 密钥。当你执行 `mvn spring-boot:run` 时，工作目录就是 `mmdp-backend/`，Spring Boot 自动找到同目录下的 `.env` 并加载。整个过程对开发者透明——你不需要手动 `set` 任何环境变量，后端就能连上远端数据库。

### Worker：通过 `.env` 文件自动加载

Python Worker 的配置方式最初和后端不同——`config.py` 通过 `os.getenv()` 直接从操作系统环境变量读取，没有文件自动加载机制。这意味着每次新终端启动 Worker，都需要手动 `set` 一堆 `MMDP_OSS_*` 变量。

为了消除这个不便，`config.py` 顶部增加了一段自动加载逻辑（源码：`mmdp-worker/config.py:7-16`）：

```python
# 本地开发便利：自动加载同目录下的 .env 文件
# 服务器部署时 Docker Compose 通过 environment: 注入，此文件不存在则静默跳过
_env_path = os.path.join(os.path.dirname(__file__), '.env')
if os.path.exists(_env_path):
    with open(_env_path) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#') and '=' in line:
                k, v = line.split('=', 1)
                os.environ.setdefault(k.strip(), v.strip())
```

这段代码在 `os.getenv()` 调用之前执行。它查找 `config.py` 同目录下的 `.env` 文件（即 `mmdp-worker/.env`），逐行解析 `KEY=VALUE` 格式的配置，用 `os.environ.setdefault()` 注入到系统环境变量中。`setdefault` 意味着如果环境变量已经被设置（比如 Docker Compose 通过 `environment:` 注入的，或者终端里手动 `set` 的），就保留已有值不覆盖。文件不存在（比如服务器容器内）则静默跳过。

Worker 启动时 `config.validate()`（源码：`mmdp-worker/config.py:45-52`）检查四个 `MMDP_OSS_*` 变量非空——现在这些值由 `.env` 文件提供，不再需要每次手动 `set`。

### 配置机制对比

| 维度 | 后端 (Spring Boot) | Worker (Python) |
|------|-------------------|-----------------|
| 配置来源 | `mmdp-backend/.env` 文件 | `mmdp-worker/.env` 文件（自动加载） |
| 加载机制 | `spring.config.import: optional:file:.env` | `config.py` 顶部逐行解析 + `os.environ.setdefault()` |
| DB 连接串 | 从 `.env` 自动注入 `MMDP_DB_URL` | 不涉及（Worker 不直连 DB） |
| OSS 密钥 | 从 `.env` 自动注入 | 从 `mmdp-worker/.env` 自动注入 |
| 启动校验 | Spring 启动时校验 datasource 连接 | `config.validate()` 校验四个 OSS 变量非空 |
| 服务器部署 | 容器内无 `.env`，Docker Compose `environment:` 注入 | 容器内无 `.env`，Docker Compose `environment:` 注入（`setdefault` 保留已有值） |
| 密钥文件 | `mmdp-backend/.env`（远端 MySQL: `8.154.36.87:13306`） | `mmdp-worker/.env`（仅 OSS + 本地路径，不含 DB 配置） |

### mmdp-worker/.env 文件

Worker 的 `.env` 只需写入与默认值不同的变量。`config.py` 中所有变量都有默认值，但三个关键变量在 Windows 本地必须覆盖：

```ini
# mmdp-worker/.env —— 本地开发用，不提交 Git

# OSS 密钥（必须，config.validate() 要求非空）
MMDP_OSS_ACCESS_KEY_ID=<你的AccessKey ID>
MMDP_OSS_ACCESS_KEY_SECRET=<你的AccessKey Secret>
# OSS 通用配置（与后端 .env 一致，显式写清更安全）
MMDP_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
MMDP_OSS_BUCKET=mmdp-test

# 本地路径覆盖（config.py 默认值是 Linux/Docker 路径，Windows 上必须改）
MMDP_WORKER_WORK_DIR=D:\tmp\mmdp-worker
MMDP_SMPL_MODEL_DIR=D:\models\smplh
```

不需要写 `MMDP_BACKEND_URL`（默认 `http://localhost:19021` 正好是本地地址）、`MMDP_OSS_REGION`（默认 `cn-hangzhou` 正确）、`MMDP_PHYSICS_DEVICE`（默认 `cpu` 正确）、`MMDP_WORKER_POLL_INTERVAL`（默认 `5` 正确）。

**服务器部署不受影响**：Docker Compose 在 `deploy/docker-compose.yml` 中通过 `environment:` 注入变量到容器进程，`os.environ.setdefault()` 发现 key 已存在就跳过；容器内也不存在 `mmdp-worker/.env`（该文件在 `.gitignore` 中，不会被打包进镜像或源码拷贝）。

这样一来，后端和 Worker 的本地启动都只需要一行命令——配置各自从同目录的 `.env` 文件自动注入。

## 本地开发验证

### 前提条件

本地开发需要远端 MySQL（`mmdp-backend/.env` 中配置的 `8.154.36.87:13306`）和阿里云 OSS 可连通。前端 Vite dev server 的 `/api` 请求代理至本地后端 19021 端口。

### 环境准备

Python 环境路径 `D:\software\Anaconda3\envs\pose_env`，已安装 torch。配置通过 `mmdp-worker/.env` 自动加载，无需手动 `set` 环境变量：

```bash
conda activate pose_env

# 确认 .env 就位即可，Worker 启动时 config.py 自动加载
cat mmdp-worker/.env
# 应包含 MMDP_OSS_ACCESS_KEY_ID, MMDP_OSS_ACCESS_KEY_SECRET,
#        MMDP_OSS_ENDPOINT, MMDP_OSS_BUCKET,
#        MMDP_WORKER_WORK_DIR=D:\tmp\mmdp-worker,
#        MMDP_SMPL_MODEL_DIR=D:\models\smplh
```

首次使用前创建 `mmdp-worker/.env`（内容见上文），之后每次启动 Worker 只需 `conda activate pose_env && python main.py`。

### SMPL 模型文件

V2 Pipeline 需要 SMPL-H 人体模型文件——一个 `.npz` 文件，包含人体模板网格、形状混合形状、姿态相关形变、关节回归矩阵和蒙皮权重。三个性别变体（neutral/male/female）各约 30MB。

本地开发时，模型文件放在 `MMDP_SMPL_MODEL_DIR` 指向的目录下：

```
D:\models\smplh\
  neutral\
    model.npz
  male\
    model.npz
  female\
    model.npz
```

文件来源：SMPL 官网（https://smpl.is.tue.mpg.de/）注册下载 SMPL-H 模型，或从 motion_vis 项目使用的路径拷贝。

### 语法与导入验证

确认所有文件语法正确、包导入链畅通：

```bash
cd D:\workspace\multimodal-data-platform\mmdp-worker
conda activate pose_env

# 语法检查
python -c "import ast; ast.parse(open('pipelines/motion_physics_metrics_v2.py', encoding='utf-8').read()); print('V2: OK')"
python -c "import ast; ast.parse(open('pipelines/body_model/lbs.py', encoding='utf-8').read()); print('lbs: OK')"
python -c "import ast; ast.parse(open('pipelines/body_model/body_model.py', encoding='utf-8').read()); print('body_model: OK')"

# 导入链验证
python -c "import sys; sys.path.insert(0, '.'); from pipelines.motion_physics_metrics_v2 import MotionPhysicsMetricsV2Pipeline, HAS_TORCH; print(f'V2 OK, HAS_TORCH={HAS_TORCH}')"
```

导入链依赖 `from .body_model.body_model import BodyModel` 能在 Worker 包上下文内正常解析。本地直接 `python xxx.py` 测试时如果遇到 `ImportError: attempted relative import with no known parent package`，这是因为 Python 的包相对导入需要正确的包层级——在 Worker 的 `pipelines/__init__.py` 通过 `importlib.import_module(".motion_physics_metrics_v2", package="pipelines")` 触发时能正常解析。

### 算法级测试（不依赖后端/DB/OSS）

这是最快的验证方式——直接调用 Pipeline 的核心计算方法，不需要启动任何服务，不需要 OSS 和 DB。

写一个测试脚本 `test_v2_local.py`：

```python
"""本地测试 V2 Pipeline 核心算法"""
import sys, os, json

os.environ["MMDP_SMPL_MODEL_DIR"] = r"D:\models\smplh"
os.environ["MMDP_PHYSICS_DEVICE"] = "cpu"

sys.path.insert(0, r"D:\workspace\multimodal-data-platform\mmdp-worker")

from pipelines.motion_physics_metrics_v2 import MotionPhysicsMetricsV2Pipeline

pipeline = MotionPhysicsMetricsV2Pipeline()
report = pipeline._compute_metrics_v2(
    npz_path=r"D:\test_data\walk.npz",
    filename="walk.npz",
)
print(json.dumps(report, ensure_ascii=False, indent=2))
```

运行：

```bash
cd D:\workspace\multimodal-data-platform\mmdp-worker
conda activate pose_env
set MMDP_SMPL_MODEL_DIR=D:\models\smplh
python test_v2_local.py
```

预期输出 12 个物理指标数值。关键验证点：

- `phys_err_mm` 正常范围 0~200，正常走路约 5-30mm
- `jitter` 正常动作 < 50
- `skate_ratio` 站立 < 10%，走路 20-50%
- 各 `_pop_ratio` 远小于 1%，>5% 说明数据有异常

### Worker 级测试（需要远端 MySQL + OSS）

算法通过后，在本地启动完整 Worker，走通轮询-领取-执行-上报全链路。

**步骤一：确认后端在线**（`http://localhost:19021`），且远端 MySQL 可达。

**步骤二：启动 Worker**（`.env` 已就位，无需手动 set）：

```bash
cd D:\workspace\multimodal-data-platform\mmdp-worker
conda activate pose_env
python main.py
```

Worker 启动时 `config.py` 自动加载同目录的 `.env`，无需手动 `set`。

**步骤三：观察启动日志。** 应该看到 `MOTION_PHYSICS_METRICS_V2` 出现在已注册 Pipeline 列表中：

```
[pipeline] [OK] 注册 MOTION_PHYSICS_METRICS_V2 (动作物理指标计算V2) <- motion_physics_metrics_v2.py
  已注册 8 个 Pipeline:
    ...
    - MOTION_PHYSICS_METRICS_V2 -- 动作物理指标计算V2 v2.0.0
```

同时 `pipeline-manifest.json` 会被重新生成，新增 V2 条目。

**步骤四：通过后端 API 创建处理作业。** 在后端管理后台或通过 API 为某个包含 `SMPL_NPZ` 资产的 Session 创建 ProcessingJob，指定 `pipelineId=MOTION_PHYSICS_METRICS_V2`。

**步骤五：观察 Worker 日志。** Worker 领取任务后会下载 NPZ、加载 BodyModel、计算指标、上传报告 JSON：

```
[领取] job-123 pipeline=MOTION_PHYSICS_METRICS_V2
[MOTION_PHYSICS_METRICS_V2] 处理: walk.npz
[MOTION_PHYSICS_METRICS_V2] 加载 BodyModel: D:\models\smplh\neutral\model.npz
[MOTION_PHYSICS_METRICS_V2] 生成: walk_phys_v2.json (1234 bytes)
[job-123] [OK] 完成
```

**步骤六：检查产物。** Session 的 Asset 列表中应出现 `PHYSICS_REPORT_V2` 类型资产。下载 JSON 验证 12 个指标均有数值。

### 本地验证的两种模式对比

| 维度 | 算法级测试 | Worker 级测试 |
|------|-----------|-------------|
| 需要远端 MySQL | 否 | 是（后端 DB） |
| 需要远端 OSS | 否 | 是（Worker 下载/上传） |
| 需要后端在线 | 否 | 是 |
| 需要 Docker | 否 | 否 |
| 测试内容 | Pipeline 算法逻辑 | 完整轮询-领取-执行-上报链路 |
| 耗时 | ~10 秒 | ~2 分钟（含创建 Job 等操作） |
| 适用场景 | 调试指标计算、验证数据格式 | 验证前后端集成 |

## 服务器部署（Docker Compose）

### 部署架构

MMDP 的 Docker Compose 部署包含四个服务（源码：`deploy/docker-compose.yml`）：

- **mmdp-mysql**：MySQL 8.4，初始化脚本在 `deploy/initdb/schema.sql`
- **mmdp-backend**：Spring Boot JAR，端口 19021（内部），从 `deploy/backend/Dockerfile` 构建
- **mmdp-worker**：Python Worker，从 `deploy/worker/Dockerfile` 构建，轮询后端领取任务
- **mmdp-nginx**：Nginx 反向代理，前端静态文件 + `/api` 代理至后端

服务器上所有服务通过 Docker 内部网络 `mmdp-net` 互联。Worker 通过容器名 `mmdp-backend` 访问后端（环境变量 `MMDP_BACKEND_URL=http://mmdp-backend:19021`），通过环境变量中配置的阿里云 OSS endpoint 直连 OSS。

V2 Pipeline 的改动集中在 Worker 镜像上。数据库需要新增一条 Pipeline 定义记录。

### SMPL 模型文件准备

在 `deploy/worker/` 目录下创建 `smpl_models/` 目录：

```
deploy/worker/
  smpl_models/
    neutral/model.npz
    male/model.npz
    female/model.npz
```

Dockerfile 中已写入 `COPY smpl_models/ /app/smpl_models/`，构建时打包进镜像。容器内默认路径 `/app/smpl_models` 对应 `MMDP_SMPL_MODEL_DIR` 的默认值。

### Docker 镜像构建

```bash
cd D:\workspace\multimodal-data-platform\deploy

# 构建 Worker 镜像
docker build -t mmdp-worker:v2 ./worker/

# 验证镜像
docker run --rm mmdp-worker:v2 python -c "
import torch; print('torch:', torch.__version__);
import os; print('models:', os.listdir('/app/smpl_models'))
"
```

如果需要重建后端镜像（因为 `AssetType.java` 新增了 `PHYSICS_REPORT_V2`）：

```bash
cd mmdp-backend
mvn clean package -DskipTests
copy target\mmdp-backend-1.0.0-SNAPSHOT.jar ..\deploy\backend\mmdp-backend.jar

cd ..\deploy
docker build -t mmdp-backend:latest ./backend/
```

### .env 与 docker-compose.yml 更新

`deploy/.env` 中增加（也可不加，Pipeline 有默认值）：

```ini
MMDP_SMPL_MODEL_DIR=/app/smpl_models
MMDP_PHYSICS_DEVICE=cpu
```

`docker-compose.yml` 中 `mmdp-worker` 服务的 environment 增加对应的变量传递。

### 数据库注册

在远端 MySQL `mmdp_db` 中执行：

```sql
INSERT INTO pipeline_definition (pipeline_id, display_name, description, input_asset_types, output_asset_types, executor_type, enabled, created_at, updated_at)
VALUES (
    'MOTION_PHYSICS_METRICS_V2',
    '动作物理指标计算V2',
    '基于SMPL BodyModel前向计算的完整物理质量指标',
    '["SMPL_NPZ"]',
    '["PHYSICS_REPORT_V2"]',
    'PYTHON_WORKER',
    1, NOW(), NOW()
);
```

如果需要绑定到已有 Profile：

```sql
INSERT INTO profile_pipeline (profile_id, pipeline_id, enabled, created_at)
SELECT cp.id, 'MOTION_PHYSICS_METRICS_V2', 1, NOW()
FROM collection_profile cp WHERE cp.profile_code = '<Profile Code>';
```

### 启动与验证

```bash
cd deploy
docker-compose down
docker-compose up -d
docker logs mmdp-worker --tail 30
```

日志中应看到 `MOTION_PHYSICS_METRICS_V2` 已注册。端到端验证流程与本地 Worker 级测试相同：在管理后台创建 ProcessingJob → Worker 领取执行 → 检查 `PHYSICS_REPORT_V2` 产物。

## 故障排查

### Pipeline 执行报 "torch is not installed"

本地：确认当前 conda 环境是 `pose_env`。

容器：`docker exec mmdp-worker python -c "import torch; print(torch.__version__)"`，若失败说明镜像构建时 torch 安装未成功，检查 Dockerfile 中 `--extra-index-url https://download.pytorch.org/whl/cpu` 是否正确。

### Pipeline 执行报 "SMPL 模型文件不存在"

本地：确认 `MMDP_SMPL_MODEL_DIR` 指向的目录下有 `neutral/model.npz`。

容器：`docker exec mmdp-worker ls /app/smpl_models/neutral/model.npz`，若不存在说明 smpl_models 目录未正确 COPY 进镜像。

### 内存不足（OOM）

NPZ 帧数 > 2000 时可能触发。Pipeline 有分块机制（chunk_size=256），但如果容器内存 < 2GB 仍可能 OOM。解决：增加内存限制，或调小 `DEFAULT_PHYS_PARAMS["chunk_size"]`。

### phys_err_mm 异常大

超过 200mm 通常说明 `trans` 字段未做根节点归零。正常走路 5-30mm，剧烈运动可达 50-80mm。
