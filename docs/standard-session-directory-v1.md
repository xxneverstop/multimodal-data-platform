# 标准 Session 目录录入规范 V1

## 1. 目的

本文面向采集侧人工整理数据时使用，定义“标准 Session 目录录入”的目录组织规则、`manifest.json` 规则，以及平台当前真实导入链路的行为边界。

当前平台对标准 Session 目录的处理方式是：

1. 前端选择一个本地目录。
2. 前端把目录中的每个文件上传到临时导入区：`imports/{taskId}/{importKey}/{relativePath}`。
3. 前端调用 `finalize`，把 `manifest.json` 和已上传文件列表一并提交给后端。
4. 后端按 `manifest.json` 收口，创建 Session、`data_file`、`data_asset`。

请注意：平台当前不是扫描 `sources/` 自动推断结构，而是以 `manifest.json` 中显式声明的路径为准。

## 2. 当前链路语义

以下语义来自当前实现，采集侧整理数据时应以此为准：

- 根目录必须包含 `manifest.json`。
- 前端按文件相对路径逐个上传，不上传空目录。
- 后端 `finalize` 只认 `manifest` 中声明的 `sources` 和 `artifacts`。
- `sources` 中每个条目会落一条 `data_file`，并进一步创建一条 `data_asset`。
- `artifacts` 中每个条目当前只落 `data_file`，不会自动创建 `data_asset`。
- `manifest.json` 本身也会落一条 `data_file`，文件角色为 `SESSION_MANIFEST`。
- Session 去重主键是 `localSessionId`，不是目录名。
- `sources` 的 key 必须存在于任务关联 profile 的 `collection_profile_source` 中。
- 如果 profile 中某个 source 被标记为 required，而 `manifest.sources` 中缺失该 source，则导入失败。
- 回放能力依赖 profile 中的 source 定义，以及 `manifest.sources[*]` 的元信息，不依赖目录扫描推断。

## 3. 目录组织规范

### 3.1 推荐目录结构

```text
session_dir/
├─ manifest.json
├─ sources/
│  ├─ cam01/
│  │  └─ video.mp4
│  ├─ cam02/
│  │  └─ video.mp4
│  ├─ hmd/
│  │  └─ ego.mp4
│  └─ imu/
│     └─ data.jsonl
└─ artifacts/
   ├─ readme.md
   ├─ calibration/
   │  └─ intrinsics.json
   └─ preview/
      └─ contact-sheet.jpg
```

### 3.2 目录规则

- `sources/` 下一级目录名必须等于平台 profile 中定义的 `sourceKey`，例如 `cam01`、`cam02`、`hmd`、`imu`。
- 不要在 `sources/` 下使用任意设备昵称、中文目录名或现场临时命名。
- V1 规范下，每个 `sourceKey` 目录只承载一个主采集文件。
- 该主采集文件必须被 `manifest.sources[sourceKey].path` 显式引用。
- `artifacts/` 用于放说明、校准文件、预览图、日志等非主采集资产。
- 目录名不是业务身份，业务身份一律以 `manifest.json` 为准。

## 4. manifest.json 规范

### 4.1 推荐写法

```json
{
  "schemaVersion": "session-directory-v1",
  "clientId": "collector-lab-a",
  "localRefs": {
    "localTaskId": "LT-20260610-001",
    "localSessionId": "LS-20260610-001"
  },
  "platformRefs": {
    "platformTaskId": 21
  },
  "task": {
    "name": "步态采集任务",
    "profileCode": "BINOCULAR_HMD_IMU_V1"
  },
  "subject": {
    "code": "S-001",
    "name": "Subject A"
  },
  "action": {
    "code": "walk",
    "name": "Walk"
  },
  "session": {
    "startedAt": "2026-06-10T10:20:30",
    "endedAt": "2026-06-10T10:21:05",
    "durationMs": 35000,
    "timestampPolicy": "device"
  },
  "sources": {
    "cam01": {
      "type": "video",
      "path": "sources/cam01/video.mp4",
      "fps": 30
    },
    "imu": {
      "type": "imu",
      "path": "sources/imu/data.jsonl",
      "sampleRate": 120
    }
  },
  "artifacts": [
    {
      "path": "artifacts/readme.md",
      "kind": "README"
    },
    {
      "path": "artifacts/calibration/intrinsics.json",
      "kind": "CALIBRATION"
    }
  ]
}
```

### 4.2 字段要求

必填字段：

- `localRefs.localSessionId`
- `task.name`
- `task.profileCode`
- `subject.code`
- `action.name`
- `session.startedAt`
- `sources`

强建议填写：

- `clientId`
- `platformRefs.platformTaskId`
- `session.durationMs` 或 `session.endedAt`
- 每个 source 的 `type`

### 4.3 path 规则

- `sources` 的 key 必须等于平台 profile 中定义的 `sourceKey`。
- `manifest.sources[*].path` 必须是根目录下的相对路径。
- `manifest.sources[*].path` 必须位于 `sources/{sourceKey}/` 下。
- `manifest.artifacts[*].path` 必须位于 `artifacts/` 下。
- 文档推荐 `artifacts` 统一使用对象数组写法；后端当前兼容字符串/对象混写，但不建议混用。

## 5. 校验策略

### 5.1 前端预检

前端目录导入前会做轻量预检：

- 检查是否存在根级 `manifest.json`
- 检查 `manifest.json` 是否是合法 JSON
- 检查目录中是否存在重复相对路径
- 检查 `manifest.sources[*].path` 是否都能在目录中找到
- 检查 `manifest.artifacts[*].path` 是否都能在目录中找到
- 检查 source path 是否位于 `sources/{sourceKey}/` 下
- 检查 artifact path 是否位于 `artifacts/` 下

### 5.2 后端准入校验

后端以权威规则做最终校验：

- task、profile、subject 一致性校验
- `sourceKey` 白名单校验
- required source 完整性校验
- 上传对象 `objectKey` 前缀校验
- OSS 对象大小一致性校验
- 目录路径安全校验，禁止 `../` 等危险路径

## 6. 常见错误示例

错误示例 1：`sourceKey` 不受平台 profile 支持

```json
"sources": {
  "left_camera": {
    "path": "sources/left_camera/video.mp4"
  }
}
```

原因：`left_camera` 不是 profile 中定义的 `sourceKey`。

错误示例 2：path 不在对应目录下

```json
"sources": {
  "cam01": {
    "path": "sources/cam02/video.mp4"
  }
}
```

原因：`cam01` 的 path 必须位于 `sources/cam01/` 下。

错误示例 3：artifact 写到 `sources/` 下

```json
"artifacts": [
  {
    "path": "sources/imu/readme.md"
  }
]
```

原因：artifact 必须位于 `artifacts/` 下。

错误示例 4：缺少 required source

原因：profile 中要求的 source 没有出现在 `manifest.sources` 中，后端会拒绝导入。

## 7. 版本边界

- V1 只覆盖“一个 `sourceKey` 对应一个主文件”的场景。
- V1 不覆盖多段分片、多文件同源聚合、一个 source 多输出文件。
- `artifacts` 当前只归档，不自动资产化。
- 如果后续需要支持更复杂的目录结构，应新增 V2 规范，不直接破坏 V1 约束。
