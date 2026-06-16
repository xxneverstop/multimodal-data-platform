# ZED+IMU Session 目录导入方案 V1

## 1. 目标

本文用于指导 `D:\workspace\multimodal-data-platform\data\zed+imu_1` 这类 ZED 双目 + IMU 原始数据，以标准 Session 目录形式进入平台。

本方案只关注最小稳定可用目标：

- 按标准 Session 目录上传
- 成功写入 `collection_session`
- 成功写入 `data_file`
- 成功写入 `data_asset`
- 先不要求平台侧播放

## 2. 目录设计

当前平台 V1 目录规范约束为“一 sourceKey 只对应一个主文件并资产化一次”。因此 ZED 相关伴随文件需要区分为：

- 主 source 文件
- 附属 artifact 文件

推荐目录如下：

```text
session_dir/
├─ manifest.json
├─ sources/
│  ├─ zed/
│  │  └─ zed.svo2
│  └─ imu/
│     └─ position.csv
└─ artifacts/
   ├─ zed/
   │  ├─ depth.npz
   │  ├─ frame_timestamps.csv
   │  └─ frame_timestamps.npz
   └─ imu/
      └─ position.npz
```

## 3. 为什么这样拆分

- `sources/zed/zed.svo2`
  - 作为 ZED 侧最核心的原始主文件
  - 当前已确认其内容是 ZED 采集主容器，应作为正式 source 进入资产体系

- `sources/imu/position.csv`
  - 文件内容包含时间戳、平移、四元数、tracking 状态
  - 虽然严格来说更接近 pose/trajectory 导出，而不是最终意义上的 IMU 原始流
  - 但在这轮“先跑通导入链路”的目标下，它是最适合作为 `imu` 主 source 的文本结构化文件

- `artifacts/zed/*`
  - `depth.npz`
  - `frame_timestamps.csv`
  - `frame_timestamps.npz`
  - 这些文件保留原始上下文，但本轮不单独资产化

- `artifacts/imu/position.npz`
  - 作为 `position.csv` 的伴随缓存格式保留

## 4. manifest 草案

上传时任务由平台页面先选择，`manifest.task.profileCode` 与 Task 绑定的 profile 必须一致。

```json
{
  "schemaVersion": "session-directory-v1",
  "clientId": "collector-zed-lab",
  "localRefs": {
    "localTaskId": "ZED-IMU-TASK-001",
    "localSessionId": "zed-imu-1"
  },
  "task": {
    "name": "ZED+IMU 原始采集导入",
    "profileCode": "ZED_STEREO_IMU_V1"
  },
  "subject": {
    "code": "S-001",
    "name": "Subject A"
  },
  "action": {
    "code": "zed_imu_capture",
    "name": "ZED+IMU Capture"
  },
  "session": {
    "startedAt": "2026-06-02T10:36:00",
    "timestampPolicy": "device"
  },
  "sources": {
    "zed": {
      "type": "zed_mcap",
      "path": "sources/zed/zed.svo2"
    },
    "imu": {
      "type": "pose_csv",
      "path": "sources/imu/position.csv"
    }
  },
  "artifacts": [
    {
      "path": "artifacts/zed/depth.npz",
      "kind": "DEPTH_RAW"
    },
    {
      "path": "artifacts/zed/frame_timestamps.csv",
      "kind": "FRAME_TIMESTAMPS_CSV"
    },
    {
      "path": "artifacts/zed/frame_timestamps.npz",
      "kind": "FRAME_TIMESTAMPS_CACHE"
    },
    {
      "path": "artifacts/imu/position.npz",
      "kind": "POSE_CACHE"
    }
  ]
}
```

## 5. 入库结果预期

导入成功后，平台应形成：

- 1 条 `collection_session`
- 1 条 `SESSION_MANIFEST` 类型 `data_file`
- 2 条 source `data_file`
  - `source_key = zed`
  - `source_key = imu`
- 2 条 `data_asset`
  - `source_key = zed`
  - `source_key = imu`
- 4 条 artifact `data_file`
  - `artifacts/zed/depth.npz`
  - `artifacts/zed/frame_timestamps.csv`
  - `artifacts/zed/frame_timestamps.npz`
  - `artifacts/imu/position.npz`

## 6. 约束与边界

- 本方案明确遵循“一 sourceKey 一主文件”。
- `depth.npz`、`frame_timestamps.csv` 本轮降级为 artifact，不影响后续升级为独立 source。
- 这轮不引入 Python worker。
- 这轮不追求播放链路，只追求 Session 导入、入库、建资产链路跑通。
