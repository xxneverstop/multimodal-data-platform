# MMDP GPU Worker 引入方案

> 设计日期：2026-08-01
> 状态：待实施
> 前提文档：[MMDP 开发部署环境与硬件说明](MMDP_Development_Deployment_Hardware_Environment.md)

## 1. 目标

将当前统一部署的 Python Worker 按计算资源拆分为 **CPU Worker** 和 **GPU Worker**：

- **CPU Worker**：处理纯 CPU Pipeline（ffmpeg 合成、numpy 计算、IMU 对齐等），仍部署在 CPU ECS
- **GPU Worker**：处理需要 CUDA / ZED SDK 的 Pipeline（MOTION_PHYSICS_METRICS_V2、G1_MERGE_CAMERA_ROBOT），通过 Docker 部署在 GPU ECS
- 两个 Worker 共享同一个 Backend、MySQL、OSS，通过 VPC 内网通信
- 后端自动判定 Job 归属，只下发给对应类型的 Worker

### 不做什么

- ❌ 不改变前端
- ❌ 不改变 OSS 路径规则
- ❌ 不改变 Pipeline 执行逻辑本身
- ❌ 不引入消息队列（Worker 继续 HTTP 轮询）
- ❌ 不引入服务发现/注册中心

---

## 2. 关键决策（已与用户确认）

| # | 决策点 | 选择 | 理由 |
|---|--------|------|------|
| 1 | Worker 类型命名 | `CPU` / `GPU` | 简洁直观 |
| 2 | GPU Pipeline 范围 | MOTION_PHYSICS_METRICS_V2、G1_MERGE_CAMERA_ROBOT（2 个），G1_CONVERT_TO_LEROBOT 保持 CPU | 后者不依赖 CUDA/ZED |
| 3 | Docker 基础镜像 | `nvidia/cuda:13.0.0-runtime-ubuntu22.04` | 预编译依赖无需 nvcc；runtime 体积更小 |
| 4 | ZED SDK | ZED SDK 5.4 for Ubuntu 22 / CUDA 13 | GPU ECS 驱动已支持 CUDA 13 |
| 5 | Backend 分发策略 | Worker 领取时声明 `workerType`，Backend 按 `pipeline_definition.worker_type` 过滤 | DB 作为唯一真实来源，Worker 不自行决定 |
| 6 | GPU 实例生命周期 | 按量付费，手动启动；Worker 容器 `restart: unless-stopped` 自动拉起 | 节省成本，启动后自动接入 |

---

## 3. 架构拓扑

```text
阿里云 VPC（华东 1 杭州可用区 K）

CPU ECS: 172.31.133.185（包年包月，7×24 运行）
├── mmdp-nginx          (80/443 → Backend:19021)
├── mmdp-backend        (19021/tcp, 仅容器网络)
├── mmdp-mysql          (3306/tcp → 宿主机 13306)
├── mmdp-worker (CPU)   ← MMDP_WORKER_TYPE=CPU
│   └── 只处理: BUILD_PLAYBACK, BUILD_LOOPED_PLAYBACK,
│              BUILD_STEREO_MP4, BUILD_PLAYBACK_BUNDLE,
│              STEREO_IMU_ALIGN, BUILD_MOTION_VIEWER_DATA,
│              MOTION_PHYSICS_METRICS, G1_CONVERT_TO_LEROBOT
├── hbbs / hbbr         (RustDesk)
└── OSS 访问 (公网 HTTPS)

GPU ECS: 172.31.133.186（按量付费，手动启动）
└── mmdp-worker-gpu     ← MMDP_WORKER_TYPE=GPU
    ├── nvidia/cuda:13.0.0-runtime-ubuntu22.04
    ├── ZED SDK 5.4 + pyzed
    ├── PyTorch (CUDA 版)
    ├── SMPL-H 模型
    └── 只处理: MOTION_PHYSICS_METRICS_V2,
               G1_MERGE_CAMERA_ROBOT

共享资源（同 VPC 内网 + 公网 OSS）
├── MySQL:      CPU ECS 172.31.133.185:13306 (或内网 3306)
├── Backend:    CPU ECS 172.31.133.185:19021 (容器网络)
└── OSS:        oss-cn-hangzhou / mmdp-test (公网 HTTPS)
```

### 通信链路

```
GPU Worker 启动
  │
  ├─(1)─> POST http://172.31.133.185:19021/api/worker/pipelines/register
  │      携带 GPU 类 Pipeline 清单（含 workerType: "GPU"）
  │
  ├─(2)─> POST http://172.31.133.185:19021/api/worker/jobs/claim
  │      请求体 {"workerType": "GPU"}
  │      Backend 只下发 worker_type=GPU 的 Pipeline Job
  │
  ├─(3)─> OSS 下载输入文件（公网 HTTPS）
  ├─(4)─> 本地 GPU 执行 Pipeline
  ├─(5)─> OSS 上传产物（公网 HTTPS）
  │
  └─(6)─> POST /api/worker/jobs/{jobId}/success
         上报产物列表
```

> **注意**：GPU Worker 通过 **CPU ECS 内网 IP** (`172.31.133.185`) 访问 Backend。GPU ECS 公网 IP 变化不影响通信。

---

## 4. 改动清单

### 4.1 数据库

**表 `pipeline_definition` — 新增列：**

```sql
ALTER TABLE pipeline_definition
  ADD COLUMN worker_type VARCHAR(16) NOT NULL DEFAULT 'CPU'
  COMMENT 'Worker类型: CPU/GPU' AFTER executor_type;
```

**数据迁移：**

```sql
UPDATE pipeline_definition SET worker_type = 'GPU'
WHERE pipeline_id IN ('MOTION_PHYSICS_METRICS_V2', 'G1_MERGE_CAMERA_ROBOT');
-- 其余保持默认 'CPU'
```

---

### 4.2 后端

#### 4.2.1 `PipelineDefinition.java` — 新增字段

```java
private String workerType;  // CPU / GPU

// + getter / setter
```

#### 4.2.2 `WorkerPipelineInfo.java` — 新增字段

```java
public record WorkerPipelineInfo(
    String pipelineId,
    String displayName,
    String description,
    String version,
    List<String> inputAssetTypes,
    List<String> outputAssetTypes,
    List<String> runtimeDependencies,
    String workerType          // ← 新增
) {}
```

#### 4.2.3 `WorkerClaimRequest.java` — 新增 DTO

```java
package com.honortech.dataplatform.processing.dto;

public record WorkerClaimRequest(String workerType) {}
```

#### 4.2.4 `WorkerPipelineRegistry.java` — 按 workerType 分区

改动要点：

- 内部存储改为 `Map<String, Map<String, WorkerPipelineInfo>>`（外层 key = workerType）
- `replaceAll(workerType, pipelines)` — 只替换对应 workerType 的分区
- `isRegistered(workerType, pipelineId)` — 查对应分区
- `getRegisteredIds(workerType)` — 返回对应分区的 ID 集合
- `isWorkerOnline(workerType)` — 检查对应 workerType 的最后注册时间
- `getLastRegisteredAt(workerType)` — 返回对应 workerType 的最后注册时间
- 保留 `getAll()` — 返回所有分区的合并列表（供调试接口用）
- 保留 `isEmpty()` — 所有分区均为空时返回 true

```java
@Service
public class WorkerPipelineRegistry {

    // 外层 key = workerType (CPU/GPU)，内层 key = normalized pipelineId
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, WorkerPipelineInfo>> registry 
        = new ConcurrentHashMap<>();

    // workerType → 最后注册时间
    private final ConcurrentHashMap<String, LocalDateTime> lastRegisteredMap 
        = new ConcurrentHashMap<>();

    public void replaceAll(String workerType, List<WorkerPipelineInfo> pipelines) {
        ConcurrentHashMap<String, WorkerPipelineInfo> partition = 
            registry.computeIfAbsent(workerType, k -> new ConcurrentHashMap<>());
        partition.clear();
        for (WorkerPipelineInfo info : pipelines) {
            partition.put(PipelineIdNormalizer.normalize(info.pipelineId()), info);
        }
        lastRegisteredMap.put(workerType, LocalDateTime.now());
    }

    public boolean isRegistered(String workerType, String pipelineId) {
        ConcurrentHashMap<String, WorkerPipelineInfo> partition = registry.get(workerType);
        return partition != null && partition.containsKey(PipelineIdNormalizer.normalize(pipelineId));
    }

    public boolean isWorkerOnline(String workerType) {
        LocalDateTime last = lastRegisteredMap.get(workerType);
        return last != null && Duration.between(last, LocalDateTime.now()).getSeconds() <= 120;
    }
    
    // ... 其余方法类似
}
```

#### 4.2.5 `ProcessingJobService.java` — 接口签名

```java
WorkerClaimResponse claimJob(String workerType);  // 原无参 → 新增参数
```

#### 4.2.6 `ProcessingJobServiceImpl.java` — 核心改动

**claimJob() 改动：**

在现有 `claimJob()` 开头加入 workerType 过滤逻辑：

```java
@Override
@Transactional
public WorkerClaimResponse claimJob(String workerType) {
    String wt = (workerType == null || workerType.isBlank()) ? "CPU" : workerType.strip().toUpperCase();
    
    // 获取所有 CREATED+PYTHON_WORKER 任务（按时间排序）
    List<ProcessingJob> candidates = processingJobMapper.selectList(
            new LambdaQueryWrapper<ProcessingJob>()
                    .eq(ProcessingJob::getStatus, ProcessingJobStatus.CREATED.name())
                    .eq(ProcessingJob::getExecutorType, ProcessingExecutorType.PYTHON_WORKER.name())
                    .orderByAsc(ProcessingJob::getCreatedAt));
    
    // 遍历找到第一个属于该 workerType 的 Pipeline Job
    ProcessingJob job = null;
    for (ProcessingJob candidate : candidates) {
        PipelineDefinition def = pipelineDefMapper.selectOne(
                new LambdaQueryWrapper<PipelineDefinition>()
                        .eq(PipelineDefinition::getPipelineId, candidate.getPipelineId()));
        if (def != null && wt.equalsIgnoreCase(def.getWorkerType())) {
            job = candidate;
            break;
        }
    }
    
    if (job == null) {
        return null; // 无可领取的兼容 Job
    }
    
    // ... 后续保持不变（状态改 CLAIMED、过滤文件、构建响应）
}
```

> **性能说明**：当前 Job 总量很小（< 100）。如果未来 Job 量增长，可改为 SQL JOIN 过滤，避免遍历。

**createSessionJob() 校验改动：**

```java
// 校验 Worker 在线时指定 workerType
PipelineDefinition def = pipelineDefMapper.selectOne(...);
String workerType = def != null ? def.getWorkerType() : "CPU";
if (!workerPipelineRegistry.isWorkerOnline(workerType)) {
    throw new BizException("Worker 离线（类型：" + workerType + "），无法创建处理任务");
}
```

#### 4.2.7 `WorkerJobController.java` — 接口改动

```java
@PostMapping("/api/worker/jobs/claim")
public ApiResponse<WorkerClaimResponse> claimJob(
        @RequestBody(required = false) WorkerClaimRequest request) {
    String workerType = (request != null && request.workerType() != null) 
        ? request.workerType() : "CPU";
    WorkerClaimResponse response = processingJobService.claimJob(workerType);
    if (response == null) {
        return ApiResponse.success("No pending job for workerType=" + workerType, null);
    }
    return ApiResponse.success("Job claimed", response);
}

@PostMapping("/api/worker/pipelines/register")
public ApiResponse<Void> registerPipelines(@RequestBody List<WorkerPipelineInfo> pipelines) {
    // 按 workerType 分组注册
    Map<String, List<WorkerPipelineInfo>> grouped = pipelines.stream()
            .collect(Collectors.groupingBy(p -> 
                p.workerType() != null ? p.workerType().toUpperCase() : "CPU"));
    for (var entry : grouped.entrySet()) {
        List<WorkerPipelineInfo> normalized = entry.getValue().stream()
                .map(p -> new WorkerPipelineInfo(
                        PipelineIdNormalizer.normalize(p.pipelineId()),
                        p.displayName(), p.description(), p.version(),
                        p.inputAssetTypes(), p.outputAssetTypes(), 
                        p.runtimeDependencies(), p.workerType()))
                .toList();
        pipelineRegistry.replaceAll(entry.getKey(), normalized);
    }
    return ApiResponse.success("registered", null);
}
```

#### 4.2.8 超时回收（无需改动）

现有 `reclaimTimedOutJobs()` 所有 `PYTHON_WORKER` 类型的 CLAIMED 超时 Job 统一回收为 CREATED，无论 CPU/GPU。回收后会被对应类型的 Worker 重新领取。✅ 无需改动。

---

### 4.3 Worker

#### 4.3.1 `config.py` — 新增配置

```python
# Worker 类型：CPU 或 GPU，决定注册哪些 Pipeline
WORKER_TYPE = os.getenv("MMDP_WORKER_TYPE", "CPU").upper()
```

#### 4.3.2 `pipelines/base.py` — 新增类属性

```python
class BasePipeline(ABC):
    # ... 现有属性 ...
    worker_type: str = "CPU"  # "CPU" 或 "GPU"
```

#### 4.3.3 GPU Pipeline 标记

**`motion_physics_metrics_v2.py`：**
```python
class MotionPhysicsMetricsV2Pipeline(BasePipeline):
    worker_type = "GPU"
    # ...
```

**`g1_merge_camera_robot.py`：**
```python
class G1MergeCameraRobotPipeline(BasePipeline):
    worker_type = "GPU"
    # ...
```

其余 Pipeline 不设置，继承默认值 `"CPU"`。

#### 4.3.4 `pipelines/__init__.py` — 按 worker_type 过滤发现

```python
def _discover_pipelines() -> Dict[str, BasePipeline]:
    # 读取当前 Worker 类型（环境变量）
    worker_type_filter = os.getenv("MMDP_WORKER_TYPE", "CPU").upper()
    
    discovered: Dict[str, BasePipeline] = {}
    
    for _, module_name, _ in pkgutil.iter_modules([package_dir]):
        # ... 现有导入逻辑 ...
        for name, obj in inspect.getmembers(module, inspect.isclass):
            if not issubclass(obj, BasePipeline) or obj is BasePipeline:
                continue
            if inspect.isabstract(obj):
                continue
            
            # ── 按 worker_type 过滤 ──
            pipeline_worker_type = getattr(obj, "worker_type", "CPU").upper()
            if pipeline_worker_type != worker_type_filter:
                continue
            
            # ... 现有实例化 + 注册逻辑 ...
```

> 这样 CPU Worker 的 `PIPELINES` 字典只包含 CPU 类 Pipeline，GPU Worker 只包含 GPU 类。`get_manifest()` 也自然只包含匹配的类型。

#### 4.3.5 `main.py` — claim/register 携带 workerType

**claim_job()：**
```python
def claim_job() -> dict | None:
    def _do():
        resp = requests.post(
            f"{BACKEND_URL}/api/worker/jobs/claim",
            json={"workerType": WORKER_TYPE},
            timeout=10
        )
        # ... 现有解析逻辑 ...
    # ...
```

**register_with_backend()：**
manifest 中每条记录已包含 `workerType`（来自 `base.py` 的 `manifest()` 方法，见下一节），无需额外处理。

**`base.py` 的 `manifest()` 方法：**
```python
@classmethod
def manifest(cls) -> Dict:
    return {
        "pipelineId": cls.pipeline_id,
        "displayName": cls.display_name,
        "description": cls.description,
        "version": cls.version,
        "inputAssetTypes": cls.input_asset_types,
        "outputAssetTypes": cls.output_asset_types,
        "runtimeDependencies": cls.runtime_dependencies,
        "workerType": cls.worker_type,  # ← 新增
    }
```

**启动日志：**
```python
print(f"  Worker 类型: {WORKER_TYPE}")
```

---

### 4.4 部署

#### 4.4.1 CPU ECS — `docker-compose.yml` 改动

在 `mmdp-worker` 服务的 `environment` 中新增一行：

```yaml
mmdp-worker:
  # ... 现有配置 ...
  environment:
    # ... 现有环境变量 ...
    MMDP_WORKER_TYPE: "CPU"   # ← 新增
```

#### 4.4.2 GPU ECS — 新增文件

**`deploy/worker/Dockerfile.gpu`：**

```dockerfile
FROM nvidia/cuda:13.0.0-runtime-ubuntu22.04

# 系统依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    python3.10 python3-pip \
    wget ffmpeg \
    libx11-6 libglib2.0-0 libgomp1 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# --- ZED SDK 5.4 for Ubuntu 22 + CUDA 13 ---
# 下载并静默安装
RUN wget -q -O /tmp/ZED_SDK.run \
    https://download.stereolabs.com/zedsdk/5.4/cu13/ubuntu22.04/ZED_SDK_Ubuntu22_cuda13.0_v5.4.0.zstd.run \
    && chmod +x /tmp/ZED_SDK.run \
    && /tmp/ZED_SDK.run -- silent skip_cuda=true \
    && rm /tmp/ZED_SDK.run

# 安装 pyzed（ZED SDK 自带 Python 绑定，从 SDK 安装路径安装）
RUN pip3 install --no-cache-dir \
    --index-url https://pypi.tuna.tsinghua.edu.cn/simple \
    /usr/local/zed/pyzed/

# --- PyTorch CUDA 版 ---
RUN pip3 install --no-cache-dir \
    --index-url https://pypi.tuna.tsinghua.edu.cn/simple \
    torch torchvision torchaudio

# --- 其他 Python 依赖 ---
COPY src/requirements.txt /app/requirements.txt
RUN pip3 install --no-cache-dir \
    --index-url https://pypi.tuna.tsinghua.edu.cn/simple \
    -r requirements.txt

# --- Worker 源码 ---
COPY src/ /app/

# --- SMPL-H 模型 ---
COPY smpl_models/ /app/smpl_models/

RUN mkdir -p /tmp/mmdp-worker

ENV MMDP_WORKER_TYPE=GPU
ENV MMDP_PHYSICS_DEVICE=cuda

ENTRYPOINT ["python3", "main.py"]
```

**`deploy/docker-compose.gpu.yml`（GPU ECS 上使用）：**

```yaml
services:
  mmdp-worker-gpu:
    build:
      context: ./worker
      dockerfile: Dockerfile.gpu
    image: mmdp-worker-gpu:latest
    container_name: mmdp-worker-gpu
    restart: unless-stopped
    environment:
      # Backend — 使用 CPU ECS 内网 IP
      MMDP_BACKEND_URL: ${MMDP_BACKEND_URL}
      # OSS 配置（与 CPU Worker 共享）
      MMDP_OSS_ENDPOINT: ${MMDP_OSS_ENDPOINT}
      MMDP_OSS_ACCESS_KEY_ID: ${MMDP_OSS_ACCESS_KEY_ID}
      MMDP_OSS_ACCESS_KEY_SECRET: ${MMDP_OSS_ACCESS_KEY_SECRET}
      MMDP_OSS_BUCKET: ${MMDP_OSS_BUCKET}
      # Worker 配置
      MMDP_WORKER_TYPE: "GPU"
      MMDP_PHYSICS_DEVICE: "cuda"
      MMDP_SMPL_MODEL_DIR: "/app/smpl_models"
      MMDP_WORKER_WORK_DIR: "/tmp/mmdp-worker"
      MMDP_WORKER_POLL_INTERVAL: "5"
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
```

**`deploy/.env.gpu`（GPU ECS 上的环境变量文件）：**

```ini
# Backend 地址 — CPU ECS 内网 IP，端口为 Nginx 代理的 Backend
MMDP_BACKEND_URL=http://172.31.133.185

# OSS 配置（与 CPU ECS 一致）
MMDP_OSS_ENDPOINT=https://oss-cn-hangzhou.aliyuncs.com
MMDP_OSS_ACCESS_KEY_ID=<与 CPU ECS 一致>
MMDP_OSS_ACCESS_KEY_SECRET=<与 CPU ECS 一致>
MMDP_OSS_BUCKET=mmdp-test
```

> `MMDP_BACKEND_URL=http://172.31.133.185` 走 Nginx 80 端口（Nginx 反向代理到 Backend 19021），避免硬编码 Backend 容器端口。

---

## 5. 向后兼容

| 场景 | 行为 |
|------|------|
| 旧 Worker（无 `workerType`）claim | 后端默认 `workerType=CPU` |
| 旧 Pipeline（无 `worker_type` 列） | DB 默认值 `CPU` |
| 旧 `worker_type` 为空的 DB 记录 | ALTER TABLE DEFAULT 'CPU' |
| 旧 `docker-compose.yml` 未设 `MMDP_WORKER_TYPE` | Worker `config.py` 默认 `CPU` |

所有旧部署无需改动即可继续工作。

---

## 6. 边界场景处理

| 场景 | 预期行为 |
|------|---------|
| GPU Worker 离线时有 GPU Job 提交 | Job 保持 CREATED，CPU Worker 不会误领；GPU Worker 上线后自动领取 |
| GPU Worker 领取后崩溃 | 5 分钟超时回收 → CREATED → GPU Worker 重新领取 |
| GPU Worker 注册时 CPU Worker 已注册 | 各写各的分区，互不覆盖 |
| GPU 实例关机/节省停机 | Worker 容器停止；CLAIMED Job 超时回收；用户下次手动启动后自动恢复 |
| GPU Job 数量为 0 时 GPU Worker 轮询 | 返回 `null`，Worker 继续轮询，与现有行为完全一致 |
| 同时有 CPU Job 和 GPU Job | CPU Worker 领取 CPU Job，GPU Worker 领取 GPU Job，互不干扰 |

---

## 7. ZED SDK 说明

### 7.1 runtime vs devel 镜像选择

| 镜像 | 包含内容 | 适用场景 |
|------|---------|---------|
| `nvidia/cuda:13.0.0-runtime-ubuntu22.04` | CUDA 运行时库（libcudart, cuBLAS, cuDNN） | **运行** CUDA 程序 |
| `nvidia/cuda:13.0.0-devel-ubuntu22.04` | runtime + nvcc 编译器 + 头文件 + 静态库 | **编译** C++/CUDA 源码 |

我们选择 `runtime`，因为：
- PyTorch 通过 pip 安装预编译 wheel，不需要 nvcc
- ZED SDK `.run` 安装包内含预编译的 `.so` 动态库，不需要编译
- runtime 镜像体积明显小于 devel

### 7.2 ZED SDK 安装注意事项

- ZED SDK 5.4 for Ubuntu 22 + CUDA 13 的下载 URL 需到 [Stereolabs 官网](https://www.stereolabs.com/developers/release/) 获取最新确切链接
- `.run` 安装包约 200-400 MB，建议构建时使用国内网络或提前下载到本地
- 安装参数 `-- silent skip_cuda=true` 跳过 SDK 自带 CUDA 安装（容器已有 CUDA runtime）
- pyzed 从 ZED SDK 安装路径直接 pip 安装，无需额外下载

---

## 8. 实施步骤

### 阶段 1：数据库迁移

- [ ] 1.1 在 `schema.sql` 中添加 `worker_type` 列
- [ ] 1.2 编写迁移 SQL（已部署环境的 ALTER TABLE）
- [ ] 1.3 更新 CPU ECS 上 MySQL 的 `pipeline_definition` 表
- [ ] 1.4 验证：`SELECT pipeline_id, worker_type FROM pipeline_definition ORDER BY worker_type, pipeline_id`

### 阶段 2：后端改动

- [ ] 2.1 `PipelineDefinition.java`：新增 `workerType` 字段
- [ ] 2.2 `WorkerPipelineInfo.java`：新增 `workerType` 字段
- [ ] 2.3 `WorkerClaimRequest.java`：新建 DTO
- [ ] 2.4 `WorkerPipelineRegistry.java`：重构为按 workerType 分区
- [ ] 2.5 `ProcessingJobService.java`：`claimJob()` 签名新增 `workerType`
- [ ] 2.6 `ProcessingJobServiceImpl.java`：实现 workerType 过滤 + 校验
- [ ] 2.7 `WorkerJobController.java`：claim 接收 workerType，register 按类型分组
- [ ] 2.8 编译验证：`mvn clean compile`

### 阶段 3：Worker 改动

- [ ] 3.1 `config.py`：新增 `WORKER_TYPE`
- [ ] 3.2 `pipelines/base.py`：`BasePipeline` 新增 `worker_type` 属性 + manifest 包含 workerType
- [ ] 3.3 `pipelines/__init__.py`：按 `MMDP_WORKER_TYPE` 过滤 Pipeline 发现
- [ ] 3.4 `motion_physics_metrics_v2.py`：设置 `worker_type = "GPU"`
- [ ] 3.5 `g1_merge_camera_robot.py`：设置 `worker_type = "GPU"`
- [ ] 3.6 `main.py`：claim 请求体携带 `workerType`

### 阶段 4：部署

- [ ] 4.1 更新 `deploy/docker-compose.yml`：`mmdp-worker` 增加 `MMDP_WORKER_TYPE=CPU`
- [ ] 4.2 新建 `deploy/worker/Dockerfile.gpu`
- [ ] 4.3 新建 `deploy/docker-compose.gpu.yml`
- [ ] 4.4 新建 `deploy/.env.gpu`
- [ ] 4.5 确认 GPU ECS 上 NVIDIA Container Toolkit 已就绪
- [ ] 4.6 构建 GPU Worker 镜像
- [ ] 4.7 启动 GPU Worker，验证注册 → 轮询 → 领取 → 执行 → 上报全链路

### 阶段 5：验证

- [ ] 5.1 CPU Worker 仍正常运行，不受 GPU Worker 上线影响
- [ ] 5.2 提交 MOTION_PHYSICS_METRICS_V2 Job → GPU Worker 领取并处理成功
- [ ] 5.3 提交 G1_MERGE_CAMERA_ROBOT Job → GPU Worker 领取并处理成功
- [ ] 5.4 GPU Worker 离线时提交 GPU Job → 保持 CREATED，CPU Worker 不误领
- [ ] 5.5 GPU Worker 重新上线 → 自动领取积压的 GPU Job
- [ ] 5.6 提交 CPU Job（如 BUILD_PLAYBACK）→ CPU Worker 正常领取处理

---

## 9. 风险与注意事项

| 风险 | 缓解措施 |
|------|---------|
| ZED SDK 下载 URL 变动 | 构建前到 Stereolabs 官网确认最新 URL |
| PyTorch CUDA 13 wheel 不可用 | PyTorch 2.7+ 已支持 CUDA 13（compute capability sm_75 for T4） |
| GPU Worker 镜像体积大 | 多阶段构建 + `.dockerignore` 排除不需要的文件 |
| GPU 实例公网 IP 变化 | Worker 通过内网 IP 连接 Backend，不受影响 |
| CPU ECS 8GB 内存不足 | GPU Worker 在独立 GPU ECS 上运行，不占用 CPU ECS 资源 |
| 两个 Worker 同时注册冲突 | 分区注册表，互不影响 |

---

## 10. 文件改动汇总

| # | 文件路径 | 操作 | 说明 |
|---|---------|------|------|
| **数据库** ||||
| 1 | `schema.sql` | 改 | pipeline_definition 新增 worker_type 列 |
| 2 | 迁移 SQL | 新 | ALTER TABLE + 数据迁移 |
| **后端** ||||
| 3 | `.../pipeline/entity/PipelineDefinition.java` | 改 | + workerType 字段 |
| 4 | `.../pipeline/dto/WorkerPipelineInfo.java` | 改 | + workerType 字段 |
| 5 | `.../processing/dto/WorkerClaimRequest.java` | **新** | 新 DTO |
| 6 | `.../processing/service/WorkerPipelineRegistry.java` | 改 | 按 workerType 分区 |
| 7 | `.../processing/service/ProcessingJobService.java` | 改 | claimJob 签名 |
| 8 | `.../processing/service/ProcessingJobServiceImpl.java` | 改 | workerType 过滤 + 校验 |
| 9 | `.../processing/controller/WorkerJobController.java` | 改 | claim/register 接口参数 |
| **Worker** ||||
| 10 | `mmdp-worker/config.py` | 改 | + WORKER_TYPE |
| 11 | `mmdp-worker/pipelines/base.py` | 改 | + worker_type 属性 |
| 12 | `mmdp-worker/pipelines/__init__.py` | 改 | 按 worker_type 过滤 |
| 13 | `mmdp-worker/pipelines/motion_physics_metrics_v2.py` | 改 | worker_type="GPU" |
| 14 | `mmdp-worker/pipelines/g1_merge_camera_robot.py` | 改 | worker_type="GPU" |
| 15 | `mmdp-worker/main.py` | 改 | claim 携带 workerType |
| **部署** ||||
| 16 | `deploy/docker-compose.yml` | 改 | CPU Worker 加 WORKER_TYPE env |
| 17 | `deploy/worker/Dockerfile.gpu` | **新** | GPU Worker 镜像 |
| 18 | `deploy/docker-compose.gpu.yml` | **新** | GPU ECS 编排 |
| 19 | `deploy/.env.gpu` | **新** | GPU ECS 环境变量 |

---

## 附录 A：关于 devel 镜像

**什么时候需要 `nvidia/cuda:XX-devel`？**

当你需要在容器内**编译** CUDA 代码时，例如：

- `pip install` 某个包时它需要从 C++ 源码编译 CUDA 扩展（如 `nvcc` 编译 `.cu` 文件）
- 自行编译 PyTorch / TensorFlow 源码
- 编译自定义 CUDA kernel（如 `flash-attention` 等库从源码安装）

ZED SDK 的 `.run` 安装程序是**预编译**的二进制分发，只解压 `.so` 文件到系统路径，**不调用 nvcc**。PyTorch 通过 pip 安装的是**预编译 wheel**。二者均不需要 devel 镜像。

如果你的 requirements 中未来加入了需要从源码编译 CUDA 扩展的包，再切换到 devel 镜像即可。

---

## 附录 B：后续优化方向（非本期范围）

1. **GPU Job 批量领取**：单次 claim 返回多个 Job，减少 HTTP 往返
2. **GPU Worker 健康上报**：上报 GPU 利用率/显存到 Backend，前端展示
3. **自动扩缩**：基于 Job 队列深度 + 定时策略自动启动 GPU 实例（通过阿里云 API）
4. **优先级队列**：GPU Job 按优先级排序领取
5. **PyTorch 模型预热**：Worker 启动时预加载模型到 GPU 显存，减少首个 Job 延迟
