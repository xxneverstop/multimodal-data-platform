# zed+imu_1 可上传 Session 目录清单

## 1. 适用对象

本文基于以下原始目录生成：

- `D:\workspace\multimodal-data-platform\data\zed+imu_1`

目标是把这组数据整理成一份**符合当前平台标准 Session 目录导入规则**的上传包，先保证：

- Session 能成功导入
- `data_file` 能成功入库
- `data_asset` 能成功建立

本轮不要求平台直接播放。

## 2. 原始文件清单

源目录当前包含：

| 文件名 | 大小 |
|------|------|
| `zed.svo2` | `27,501,328` bytes |
| `depth.npz` | `13,819,049` bytes |
| `frame_timestamps.csv` | `20,026` bytes |
| `frame_timestamps.npz` | `4,875` bytes |
| `position.csv` | `63,687` bytes |
| `position.npz` | `6,053` bytes |

从文件内容看：

- `frame_timestamps.csv` 包含：
  - `frame_idx`
  - `frame_system_ts`
  - `frame_zed_ts_ns`
- `position.csv` 包含：
  - `frame_idx`
  - `frame_system_ts`
  - `frame_zed_ts_ns`
  - `tx ty tz`
  - `qx qy qz qw`
  - `tracking_state`
  - `tracking_state_name`

## 3. 目标上传目录

建议采集侧最终整理出的目录如下：

```text
zed-imu-1-session/
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

## 4. 文件映射关系

### 4.1 source 文件

| 平台 sourceKey | 上传路径 | 对应原始文件 | 说明 |
|------|------|------|------|
| `zed` | `sources/zed/zed.svo2` | `zed.svo2` | ZED 主原始文件 |
| `imu` | `sources/imu/position.csv` | `position.csv` | 当前最适合作为 IMU/位姿侧主结构化文件 |

### 4.2 artifact 文件

| 上传路径 | 对应原始文件 | 说明 |
|------|------|------|
| `artifacts/zed/depth.npz` | `depth.npz` | 深度原始数据 |
| `artifacts/zed/frame_timestamps.csv` | `frame_timestamps.csv` | 帧时间戳 CSV |
| `artifacts/zed/frame_timestamps.npz` | `frame_timestamps.npz` | 帧时间戳缓存 |
| `artifacts/imu/position.npz` | `position.npz` | 位姿缓存 |

## 5. manifest.json 草案

下面这份 `manifest.json` 已经按这组 `zed+imu_1` 的文件结构写好，采集侧只需要替换业务字段即可。

需要人工确认或替换的字段：

- `task.name`
- `subject.code`
- `subject.name`
- `action.code`
- `action.name`
- `localRefs.localTaskId`
- `clientId`

可以直接使用的字段：

- `localRefs.localSessionId = zed-imu-1`
- `task.profileCode = ZED_STEREO_IMU_V1`
- `session.startedAt = 2026-06-02T10:36:00`
- `session.timestampPolicy = device`
- 所有 `sources[*].path`
- 所有 `artifacts[*].path`

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

## 6. 上传前最后确认

最终上传前，应保证：

- 目录根下只有一个 `manifest.json`
- `manifest.sources.zed.path` 指向 `sources/zed/zed.svo2`
- `manifest.sources.imu.path` 指向 `sources/imu/position.csv`
- 所有 artifact 路径都位于 `artifacts/` 下
- 目标 Task 的 profile 已经配置为 `ZED_STEREO_IMU_V1`
- `subject.code` 与目标 Task 的被试编号一致

## 7. 预期导入结果

按当前平台逻辑，导入成功后应形成：

- 1 条 `collection_session`
- 1 条 `SESSION_MANIFEST` 类型 `data_file`
- 2 条 source `data_file`
  - `zed`
  - `imu`
- 2 条 `data_asset`
  - `source_key = zed`
  - `source_key = imu`
- 4 条 artifact `data_file`
