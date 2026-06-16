# ZED+IMU 上传前检查单

适用场景：

- 按 `ZED_STEREO_IMU_V1` profile 导入 ZED+IMU 原始 Session
- 目标是先跑通标准 Session 目录上传、入库、建资产链路

## 1. 目录结构检查

- [ ] 根目录名称已确认，例如 `zed-imu-1-session/`
- [ ] 根目录下存在 `manifest.json`
- [ ] 根目录下存在 `sources/`
- [ ] 根目录下存在 `artifacts/`
- [ ] `sources/zed/zed.svo2` 存在
- [ ] `sources/imu/position.csv` 存在
- [ ] `artifacts/zed/depth.npz` 存在
- [ ] `artifacts/zed/frame_timestamps.csv` 存在
- [ ] `artifacts/zed/frame_timestamps.npz` 存在
- [ ] `artifacts/imu/position.npz` 存在
- [ ] 目录中没有重复文件名导致的重复相对路径

## 2. manifest 检查

- [ ] `manifest.json` 能被正常解析为合法 JSON
- [ ] `schemaVersion` 已填写
- [ ] `clientId` 已填写
- [ ] `localRefs.localTaskId` 已填写
- [ ] `localRefs.localSessionId` 已填写，建议本批次使用 `zed-imu-1`
- [ ] `task.name` 已填写
- [ ] `task.profileCode = ZED_STEREO_IMU_V1`
- [ ] `subject.code` 已填写
- [ ] `subject.name` 已填写
- [ ] `action.code` 已填写
- [ ] `action.name` 已填写
- [ ] `session.startedAt` 已填写
- [ ] `session.timestampPolicy` 已填写，建议 `device`

## 3. sources 路径检查

- [ ] `manifest.sources.zed.path = sources/zed/zed.svo2`
- [ ] `manifest.sources.imu.path = sources/imu/position.csv`
- [ ] `sources` 中只保留 `zed` 和 `imu` 两个 key
- [ ] `manifest.sources.zed.path` 实际能在目录中找到对应文件
- [ ] `manifest.sources.imu.path` 实际能在目录中找到对应文件
- [ ] `sources[*].path` 都位于 `sources/{sourceKey}/` 下

## 4. artifacts 路径检查

- [ ] `artifacts/zed/depth.npz` 已写入 `manifest.artifacts`
- [ ] `artifacts/zed/frame_timestamps.csv` 已写入 `manifest.artifacts`
- [ ] `artifacts/zed/frame_timestamps.npz` 已写入 `manifest.artifacts`
- [ ] `artifacts/imu/position.npz` 已写入 `manifest.artifacts`
- [ ] 所有 `manifest.artifacts[*].path` 实际都能在目录中找到
- [ ] 所有 artifact 路径都位于 `artifacts/` 下

## 5. 任务绑定检查

- [ ] 平台中已经存在目标 Task
- [ ] 目标 Task 的 `profileCode` 已配置为 `ZED_STEREO_IMU_V1`
- [ ] `manifest.subject.code` 与目标 Task 的 `subjectCode` 一致
- [ ] 本次上传使用的 Session 没有重复用旧的 `localSessionId`

## 6. 文件语义检查

- [ ] `zed.svo2` 作为 `zed` source 主文件使用
- [ ] `position.csv` 作为 `imu` source 主文件使用
- [ ] `depth.npz` 不放到 source，先作为 artifact
- [ ] `frame_timestamps.csv` 不放到 source，先作为 artifact
- [ ] `position.npz` 不放到 source，先作为 artifact

## 7. 上传成功后的预期结果

上传完成后，应在平台中看到：

- [ ] 新增 1 条 `collection_session`
- [ ] 新增 1 条 `SESSION_MANIFEST` 文件记录
- [ ] 新增 2 条 source 文件记录
  - [ ] `source_key = zed`
  - [ ] `source_key = imu`
- [ ] 新增 2 条数据资产
  - [ ] `source_key = zed`
  - [ ] `source_key = imu`
- [ ] 新增 4 条 artifact 文件记录

## 8. 常见错误提醒

- [ ] 不要把 `frame_timestamps.csv` 放到 `sources/frame_timestamps/`
- [ ] 不要把 `depth.npz` 放到 `sources/depth/`
- [ ] 不要把 `position.npz` 作为 `imu` 主 source
- [ ] 不要让 `task.profileCode` 和目标 Task 的 profile 不一致
- [ ] 不要遗漏 `manifest.json`
- [ ] 不要出现 `../` 这类不安全路径
