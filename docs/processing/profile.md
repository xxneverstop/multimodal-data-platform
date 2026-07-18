你的这份 `manifest.json` **不需要推倒重改**，已经能作为 `ZED_STEREO_IMU_V1` 的第一版标准 Session 清单使用。它已经包含了 schemaVersion、clientId、本地任务/Session 引用、profileCode、subject、action、session 时间、sources 和 artifacts，结构是合理的。

但我建议做 **小修正**：

```
"sources": {
  "zed": {
    "type": "zed_svo2",
    "path": "sources/zed/zed.svo2"
  },
  "imu": {
    "type": "pose_csv",
    "path": "sources/imu/position.csv"
  }
}
```

你现在写的是：

```
"type": "zed_mcap"
```

但文件实际是 `zed.svo2`，所以这里最好改成 `zed_svo2`，否则后面 Profile 校验和处理器匹配会混乱。

------

## 1. 这份 manifest 现在怎么理解

你的 Session 可以定义为：

```
ProfileCode: ZED_STEREO_IMU_V1

原始 Source:
- zed.svo2
- position.csv

派生 Artifact:
- depth.npz
- frame_timestamps.csv
- frame_timestamps.npz
- position.npz
```

其中：

```
zed.svo2 = ZED 原始录制文件
position.csv = 位姿/IMU 轨迹源文件
depth.npz = ZED 深度结果
frame_timestamps = ZED 帧时间轴
position.npz = position.csv 的算法缓存
```

所以后端入库时不要把这 6 个文件都当成普通文件，而应该变成：

```
1 个 Session
  ├── 2 个 source asset
  └── 4 个 artifact asset
```

------

## 2. 现在没有 Profile 管理功能，怎么办？

不用一上来做复杂的“Profile 管理后台”。你可以先做一个 **后端内置 Profile Registry**。

也就是在后端先写死一份配置：

```
ZED_STEREO_IMU_V1:
  requiredSources:
    - zed
    - imu

  allowedSourceTypes:
    zed: zed_svo2
    imu: pose_csv

  allowedArtifacts:
    - DEPTH_RAW
    - FRAME_TIMESTAMPS_CSV
    - FRAME_TIMESTAMPS_CACHE
    - POSE_CACHE

  processingTemplates:
    - ZED_METADATA_EXTRACT
    - ZED_PREVIEW_GENERATE
    - TIMELINE_NORMALIZE
    - PLAYBACK_BUNDLE_BUILD
```

这一步先不用做 UI。你只需要做到：

```
manifest.profileCode 能被后端识别
sources 必需项能校验
artifact.kind 能识别
上传后能自动创建 asset
后续能按 profileCode 自动挂处理流程
```

等这条链路跑通以后，再把这个 Registry 搬到数据库和管理页面。

------

## 3. 这份 Profile 应该怎么定义

你现在的 Profile 可以这样设计：

```
ProfileCode: ZED_STEREO_IMU_V1
ProfileName: ZED 双目相机 + IMU/位姿采集

必需 Sources:
1. zed
   - type: zed_svo2
   - path: sources/zed/zed.svo2
   - modality: stereo_rgbd
   - role: raw_recording

2. imu
   - type: pose_csv
   - path: sources/imu/position.csv
   - modality: pose
   - role: pose_trajectory

可选 Artifacts:
1. DEPTH_RAW
   - modality: depth
   - format: npz

2. FRAME_TIMESTAMPS_CSV
   - modality: timestamp
   - format: csv

3. FRAME_TIMESTAMPS_CACHE
   - modality: timestamp
   - format: npz

4. POSE_CACHE
   - modality: pose
   - format: npz
```

这就是你后面 Profile 管理功能的雏形。

------

## 4. 上传 finalize 后应该做什么

上传完成后，`finalize` 不只是登记文件，而是应该执行：

```
1. 读取 manifest.json
2. 校验 profileCode = ZED_STEREO_IMU_V1
3. 创建 collection_session
4. 创建 data_file
5. 创建 data_asset
6. 建立 source/artifact 分类
7. 生成 processing_job 待处理任务
```

资产建议这样落库：

```
Asset 1: zed_raw_svo2
- file: sources/zed/zed.svo2
- assetType: RAW_SOURCE
- modality: stereo_rgbd

Asset 2: imu_pose_csv
- file: sources/imu/position.csv
- assetType: RAW_SOURCE
- modality: pose

Asset 3: zed_depth_raw
- file: artifacts/zed/depth.npz
- assetType: DERIVED_ARTIFACT
- modality: depth

Asset 4: frame_timestamps
- file: artifacts/zed/frame_timestamps.csv
- assetType: DERIVED_ARTIFACT
- modality: timestamp

Asset 5: pose_cache
- file: artifacts/imu/position.npz
- assetType: DERIVED_ARTIFACT
- modality: pose
```

------

## 5. 预处理链路怎么做

建议你先做 4 个处理任务。

### 第一步：Session 元信息解析

```
输入：
- manifest.json
- zed.svo2
- position.csv

输出：
- session_metadata.json
```

里面记录：

```
frameCount
fps
resolution
duration
poseRowCount
startTime
timestampPolicy
```

作用是让平台知道这个 Session 的基本情况。

------

### 第二步：生成播放预览

浏览器不能直接播放 `.svo2`，所以必须转成：

```
preview/zed_left.mp4
preview/zed_right.mp4
preview/depth_preview.mp4
preview/thumbnail.jpg
```

这一步完成后，Session 状态可以变成：

```
PREVIEW_READY
```

------

### 第三步：时间轴标准化

输入：

```
frame_timestamps.csv
position.csv
```

输出：

```
timeline.json
sync_index.csv
```

作用是把视频帧和位姿轨迹统一到同一条时间轴。

------

### 第四步：生成 Playback Bundle

输出：

```
playback_bundle.json
```

内容类似：

```
{
  "video": {
    "left": "preview/zed_left.mp4",
    "right": "preview/zed_right.mp4",
    "depth": "preview/depth_preview.mp4"
  },
  "timeline": "timeline.json",
  "pose": "sources/imu/position.csv",
  "defaultView": ["left", "depth", "pose"]
}
```

前端播放页只读 `playback_bundle.json`，不要直接理解所有原始文件。

------

## 6. 播放页面怎么做

你的播放页可以分三阶段实现。

第一阶段，最小可播放：

```
左侧：ZED 左目 MP4
右侧：位姿 CSV 曲线
底部：时间轴 slider
```

第二阶段，多模态同步播放：

```
左目视频
右目视频
深度预览视频
position x/y/z 曲线
统一时间轴同步
```

第三阶段，标注能力：

```
播放暂停
选中时间段
打标签
保存 annotation.json
```

不要试图一开始直接播放 `.svo2`。平台里真正的播放对象应该是：

```
playback_bundle
```

------

## 7. 标注怎么接

标注应该绑定到：

```
sessionId + timeline
```

而不是绑定某一个文件。

标注结果可以是：

```
{
  "sessionId": 1,
  "profileCode": "ZED_STEREO_IMU_V1",
  "annotations": [
    {
      "startTime": 3.2,
      "endTime": 5.8,
      "label": "walking",
      "source": "manual"
    }
  ]
}
```

后面如果加自动标注，也只是新增：

```
AUTO_ANNOTATION_JOB
```

它的输出还是 annotation asset。

------

## 8. 你现在最应该做的下一步

不要马上做复杂 Profile 管理页面。建议顺序是：

```
第一步：修正 manifest 里的 zed_mcap → zed_svo2

第二步：后端增加内置 Profile Registry

第三步：finalize 时按 Profile 校验并创建资产

第四步：增加 processing_job 模板

第五步：先生成 preview mp4 + playback_bundle.json

第六步：前端做 Session 播放页

第七步：再做标注表和 annotation.json
```

一句话总结：
你这份 manifest 已经可以作为第一版标准，不需要大改；现在真正缺的不是 manifest，而是 **Profile Registry + 处理任务模板 + Playback Bundle** 这三层。