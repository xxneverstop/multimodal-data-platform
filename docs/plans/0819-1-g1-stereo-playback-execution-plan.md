# G1 完整双目回放执行计划

> 来源：将 `G1_GENERATE_PLAYBACK` 从仅生成左眼 MP4 升级为左右眼独立 MP4，并在回放页同步展示
> 计划日期：2026-08-19
> 预计工期：2.5～4 人日（包含自动化测试、真实数据验证和历史单目兼容）

---

## 一、背景与目标

### 1.1 背景

`G1_MERGE_CAMERA_ROBOT` 已经从 ZED SVO2 中读取左右眼，并在合并后的 HDF5 中写入 `observation_image_left` 和 `observation_image_right`。当前 `G1_GENERATE_PLAYBACK` 只读取左眼数据集，只生成一个 `g1_playback.mp4`，因此回放页只能显示单目视频。

参考数据 `data/g1-rebot-1/test-4-upload-session` 已完成只读实测：

- ZED-M SVO2 可正常打开，分辨率为 1280×720、30 FPS；
- 标称 448 帧，实际可成功解码 447 帧；
- 左右眼在首段、中段、末段均可解码，画面哈希不同；
- 机器人 HDF5 有 299 个单调递增时间戳，约 20.05 FPS；
- 按现有最近帧算法匹配时，平均误差 8.31 ms、最大误差 18.93 ms，无超过 20 ms 的告警帧；
- 合并后预期得到左右眼各 299 帧，可分别生成约 14.95 秒、20 FPS 的 MP4。

现有平台已经具备本次改造的大部分基础能力：Worker 可以让一个 Job 返回多个产物，后端会为每个产物创建独立的 `DataFile`、`DataAsset` 和血缘，前端 `PlaybackView` 也会遍历多个视频 source 并以网格展示。改造重点是双目编码、双目回放映射、同步控制和历史兼容。

### 1.2 目标

- `G1_GENERATE_PLAYBACK` 一次生成左眼和右眼两个独立 MP4。
- 左右眼产物具有稳定且互不冲突的文件名与派生 `sourceKey`。
- 双目产物作为一个原子结果：缺失任意眼、帧数不一致或任一 FFmpeg 失败时，整个 Job 失败，不上报半套产物。
- 回放 API 为 `G1_TELEOP_V1` 返回两个明确标注的 video source。
- 回放页默认两列显示左右眼，播放、暂停、跳转、逐帧操作保持同步，并对长期播放漂移进行校正。
- 旧版单目 `g1_playback.mp4` 继续可播放；历史 Session 可通过清理旧播放 Job 后重跑升级为双目。
- 不重新处理原始 SVO2，不修改 `G1_MERGE_CAMERA_ROBOT`，不改变现有 API 和数据库表结构。

### 1.3 数据流与模块边界

```text
原始 Session
├─ robot_hdf5/episode_N.hdf5
└─ camera_svo2/episode_N.svo2
                │
                ▼
G1_MERGE_CAMERA_ROBOT（保持不变）
                │
                ▼
G1_MERGED_HDF5
├─ observation_image_left[N]
└─ observation_image_right[N]
                │
                ▼
G1_GENERATE_PLAYBACK v2
├─ g1_playback_left.mp4
│    sourceKey = camera_svo2_left
└─ g1_playback_right.mp4
     sourceKey = camera_svo2_right
                │
                ▼
Worker success 上报（现有协议，不改）
                │
                ▼
DataFile + DataAsset + AssetLineage（每只眼各一份）
                │
                ▼
G1PlaybackRuleResolver
├─ camera_svo2_left  → “ZED 左眼”
└─ camera_svo2_right → “ZED 右眼”
                │
                ▼
PlaybackView 两列同步播放
```

模块边界：

- Worker 负责从 HDF5 读取、校验并编码两个 MP4，不负责决定前端布局。
- 后端回放规则负责把派生产物转换为回放 source，不把派生产物伪装成采集 Profile Source。
- 前端只消费回放 API，不根据文件名猜测左右眼。
- 数据库仅更新 Pipeline 展示描述，不增加表、字段或 Profile Source。

### 1.4 改动前后对比（必须）

#### 1.4.1 产物数据流

#### 改动前

```text
G1_MERGED_HDF5
├─ observation_image_left  ──► FFmpeg ──► g1_playback.mp4
└─ observation_image_right ──► 未消费

Worker outputs = [RGB_VIDEO_MP4 × 1]
```

#### 改动后

```text
G1_MERGED_HDF5
├─ observation_image_left  ──► FFmpeg-L ──► g1_playback_left.mp4
└─ observation_image_right ──► FFmpeg-R ──► g1_playback_right.mp4
                                      ↑ 改动：两只眼均编码

Worker outputs = [RGB_VIDEO_MP4 × 2]
```

#### 1.4.2 回放映射

#### 改动前

```text
DefaultPlaybackRuleResolver
└─ camera_svo2
   └─ g1_playback.mp4 ──► 单个 PlaybackSource
```

#### 改动后

```text
ProfileRuleRegistry
├─ G1_TELEOP_V1 ──► G1PlaybackRuleResolver       ← 新增
│  ├─ camera_svo2_left  ──► ZED 左眼
│  ├─ camera_svo2_right ──► ZED 右眼
│  └─ 旧 g1_playback.mp4 ──► ZED 左眼（旧版回退）
└─ 其他 Profile ──► DefaultPlaybackRuleResolver  ← 保持不变
```

#### 1.4.3 前端播放控制

#### 改动前

```text
点击播放
├─ video-left.play()
└─ video-right.play()

第一路视频更新 masterTime
其他视频独立解码，长时间播放可能漂移
每个 <video> 自带 controls，可被单独操作而失去同步
```

#### 改动后

```text
等待全部视频 canplay
        │
        ▼
统一设置 currentTime / playbackRate
        │
        ▼
Promise.all(全部 video.play())
        │
        ▼
左眼作为主时钟
        │
        ├─ 时间差 <= 阈值：继续播放
        └─ 时间差 > 约半帧：校正右眼 currentTime  ← 新增

所有操作统一走底部控制栏，禁止单独改变某一路时间轴
```

---

## 二、改动清单

### 2.1 Worker：升级 G1 双目编码

**文件**：`mmdp-worker/pipelines/g1_generate_playback.py`

计划修改：

1. 将版本从 `1.0.0` 升级为 `2.0.0`，描述改为生成左右眼独立 MP4。
2. 保持 `pipeline_id = "G1_GENERATE_PLAYBACK"`、输入类型 `G1_MERGED_HDF5` 和输出类型 `RGB_VIDEO_MP4` 不变。
3. 定义稳定的双目产物契约：

```python
EYE_OUTPUTS = {
    "left": {
        "dataset": "observation_image_left",
        "source_key": "camera_svo2_left",
        "file_name": "g1_playback_left.mp4",
    },
    "right": {
        "dataset": "observation_image_right",
        "source_key": "camera_svo2_right",
        "file_name": "g1_playback_right.mp4",
    },
}
```

4. 抽取 FFmpeg 进程创建、结束检查和异常清理方法，避免左右逻辑复制。
5. 按自然顺序遍历所有 `G1_MERGED_HDF5`，每个文件先校验左右数据集均存在且长度相等，再成对写入两个 FFmpeg stdin。
6. 累计左右帧数并断言最终相等且大于零。
7. 任一写入或 FFmpeg 失败时终止两个进程；由于 Worker 只在 `execute()` 成功返回后上传，失败时不会向 OSS 上报半套产物。
8. 两个编码器都成功并且两个文件都存在后，返回两个 `RGB_VIDEO_MP4` 产物。

预期核心结构：

```python
encoders = self._start_encoders(ffmpeg, output_dir)
try:
    for item in merged_files:
        with h5py.File(local_path, "r") as hdf5_file:
            left = hdf5_file["observation_image_left"]
            right = hdf5_file["observation_image_right"]
            if len(left) != len(right):
                raise RuntimeError("左右眼帧数不一致")
            for left_frame, right_frame in zip(left, right):
                encoders["left"].stdin.write(left_frame.tobytes())
                encoders["right"].stdin.write(right_frame.tobytes())
finally:
    self._finish_or_abort(encoders)
```

### 2.2 Worker 测试：覆盖双目、边界和失败原子性

**文件**：`mmdp-worker/test_g1_generate_playback.py`

计划修改：

- 将单目测试改为创建颜色明显不同的左右 JPEG 帧。
- 断言返回两个产物，文件名、`sourceKey`、`assetType` 和 MIME 类型准确。
- 使用 OpenCV 或 ffprobe 验证两个视频均为预期帧数、分辨率和 20 FPS。
- 解码首帧并验证左右画面不同，防止错误地把左眼复制成右眼。
- 新增缺失右眼数据集时失败的测试。
- 新增左右帧数不一致时失败的测试。
- 新增多 episode 自然排序与左右帧数累计一致的测试。
- 保留无 FFmpeg 环境时的 skip 行为。

### 2.3 后端：新增 G1 专用回放规则

**新增文件**：`mmdp-backend/src/main/java/com/honortech/dataplatform/profile/rule/G1PlaybackRuleResolver.java`

计划实现：

- 使用 `@Component` 和 `@Order(0)`，只支持 `G1_TELEOP_V1`。
- 以派生 `sourceKey` 为主要识别依据，以 `.mp4` 扩展名作为媒体格式校验。
- 双目模式必须同时找到 `camera_svo2_left` 与 `camera_svo2_right` 才判定可播放。
- 当完整双目不存在时，识别旧版 `sourceKey=camera_svo2`、文件名 `g1_playback.mp4`，返回单个“ZED 左眼（旧版）”source，保证历史数据不失效。
- 当双目与旧版单目同时存在时，优先返回双目，不混入旧产物。
- 回放 source 的顺序固定为左眼、右眼，使前端第一路主时钟稳定为左眼。

预期核心结构：

```java
@Component
@Order(0)
public class G1PlaybackRuleResolver implements PlaybackRuleResolver {
    private static final String RULE_CODE = "G1_TELEOP_V1";
    private static final String LEFT_KEY = "camera_svo2_left";
    private static final String RIGHT_KEY = "camera_svo2_right";

    @Override
    public boolean supports(String ruleCode) {
        return RULE_CODE.equalsIgnoreCase(ruleCode);
    }

    @Override
    public boolean canPlay(List<CollectionProfileSource> profileSources,
                           List<DataFile> sessionFiles) {
        return hasCompleteStereo(sessionFiles) || findLegacyVideo(sessionFiles).isPresent();
    }
}
```

不把 `camera_svo2_left/right` 写入 `collection_profile_source`。它们是处理产物的逻辑通道，不是上传时必须提供的原始采集源；这样不会让上传整理工具额外要求或展示两个不存在的输入目录。

### 2.4 后端测试：验证规则选择和历史兼容

**新增文件**：`mmdp-backend/src/test/java/com/honortech/dataplatform/profile/rule/G1PlaybackRuleResolverTest.java`

计划覆盖：

- `supports()` 只匹配 `G1_TELEOP_V1`。
- 完整左右产物返回两个 source，顺序、标签、URL、FPS 正确。
- 只有左眼或只有右眼时不判定完整双目可播放。
- 旧版 `g1_playback.mp4` 返回单个兼容 source。
- 双目与旧版同时存在时只返回双目。
- 指定 `jobId` 时，现有 `buildPlaybackFileList()` 只传入该 Job 产物，规则不会串用其他 Job 文件。

**文件**：`mmdp-backend/src/test/java/com/honortech/dataplatform/profile/rule/DefaultPlaybackRuleResolverTest.java`

计划修改：

- 将当前带 G1 语义的测试改为真正的通用 Profile 测试。
- G1 双目和旧版兼容断言全部迁移到 `G1PlaybackRuleResolverTest`，避免两个 Resolver 的职责重叠。

### 2.5 前端：完善双视频同步

**文件**：`mmdp-frontend/src/views/playback/PlaybackView.vue`

现有多视频网格、统一播放、暂停、跳转和逐帧能力继续复用。计划补充：

1. 记录每路视频的 `duration` 和加载状态；所有视频可播放后才允许统一启动。
2. 固定第一个 video source（后端保证为左眼）作为主时钟。
3. 主视频 `timeupdate` 时检查从视频漂移；超过约半帧阈值时校正右眼 `currentTime`。
4. 以所有视频可用时长的最小值作为时间轴上限，避免一只眼结束后另一只仍继续。
5. `playAll()` 使用 `Promise.all()`；任一路播放失败时回滚 `playing` 状态并暂停全部视频。
6. 跳转和逐帧操作后等待两路完成 seek，再恢复播放状态。
7. 移除每个 `<video>` 的独立原生时间轴控制，所有操作统一走页面底部控制栏，防止用户单独拖动一只眼造成失步。
8. 在视频标签中显示左眼/右眼及同步状态，漂移校正时不弹出干扰提示，仅在调试日志记录。

预期同步逻辑：

```ts
const syncTolerance = computed(() => Math.max(frameStep.value / 2, 0.025));

function syncFollowers(masterKey: string, master: HTMLVideoElement) {
  for (const [key, follower] of Object.entries(videoRefs)) {
    if (!follower || key === masterKey) continue;
    if (Math.abs(follower.currentTime - master.currentTime) > syncTolerance.value) {
      follower.currentTime = master.currentTime;
    }
  }
}
```

### 2.6 Pipeline 元数据迁移

**新增文件**：`database-migrations/2026-08-19-update-g1-stereo-playback.sql`

计划内容：

- 更新 `G1_GENERATE_PLAYBACK` 的显示名称和描述，明确输出左右眼两个 MP4。
- 保持输入类型 `["G1_MERGED_HDF5"]` 不变。
- 保持输出类型 `["RGB_VIDEO_MP4"]` 不变；该字段表达类型集合，不表达产物数量。
- 不修改 Profile-Pipeline 关联和 `collection_profile_source`。
- 提供查询验证和人工回滚 SQL；不在代码执行阶段直接连接数据库修改数据。

示意：

```sql
UPDATE pipeline_definition
SET display_name = 'G1 双目可视化回放资产生成',
    description = '从合并后的 G1 HDF5 提取左右眼图像，分别生成同步 MP4',
    updated_at = NOW()
WHERE pipeline_id = 'G1_GENERATE_PLAYBACK';
```

### 2.7 Worker Manifest

**文件**：`mmdp-worker/pipeline-manifest.json`

实现完成后通过 Worker 自带的 manifest 生成流程重新生成，不手工维护 JSON。验收以下字段：

- `pipelineId` 仍为 `G1_GENERATE_PLAYBACK`；
- `version` 为 `2.0.0`；
- 描述明确左右眼双目输出；
- `inputAssetTypes`、`outputAssetTypes` 与数据库保持一致。

---

## 三、文件变更总览

| 操作 | 文件 | 说明 |
| ---- | ---- | ---- |
| 修改 | `mmdp-worker/pipelines/g1_generate_playback.py` | 双 FFmpeg 编码、双目校验、原子失败、两个产物 |
| 修改 | `mmdp-worker/test_g1_generate_playback.py` | 双目正常、缺失、帧数不一致、多 episode 测试 |
| 新增 | `mmdp-backend/src/main/java/com/honortech/dataplatform/profile/rule/G1PlaybackRuleResolver.java` | G1 双目映射及旧版单目回退 |
| 新增 | `mmdp-backend/src/test/java/com/honortech/dataplatform/profile/rule/G1PlaybackRuleResolverTest.java` | 双目、部分产物、旧版兼容测试 |
| 修改 | `mmdp-backend/src/test/java/com/honortech/dataplatform/profile/rule/DefaultPlaybackRuleResolverTest.java` | 移除 G1 专属职责，保留通用规则测试 |
| 修改 | `mmdp-frontend/src/views/playback/PlaybackView.vue` | 双目加载屏障、漂移校正、统一控制、最小时长 |
| 新增 | `database-migrations/2026-08-19-update-g1-stereo-playback.sql` | 更新 Pipeline 展示元数据，不改 schema |
| 生成 | `mmdp-worker/pipeline-manifest.json` | 更新版本和描述 |
| 新增 | `docs/development/2026-08-19-1-g1-stereo-playback-build-log.md` | 实施完成后的构建记录，不在计划阶段创建 |

预计实施阶段涉及 8 个代码/配置文件，约净增 350～550 行；完成后另生成 1 份 build log。

明确不修改：

- 不修改数据库 schema，不新增表或字段，因为现有 `DataFile.sourceKey` 已可区分派生产物。
- 不新增 `LEFT_RGB_VIDEO_MP4`、`RIGHT_RGB_VIDEO_MP4` 等 AssetType；两只眼均为 `RGB_VIDEO_MP4`，由 `sourceKey` 表达通道。
- 不修改 `G1_MERGE_CAMERA_ROBOT`；它已经正确写入左右眼数据集。
- 不修改 Worker success 上报 DTO、上传逻辑、DataFile/DataAsset/AssetLineage 创建逻辑；现有逻辑已支持多个 output。
- 不新增或修改回放 API；继续使用 `/api/sessions/{sessionId}/playback` 和 `/play/{sessionId}`。
- 不新增 `collection_profile_source`；左右眼是派生回放通道，不是采集输入源。
- 不修改原始参考数据和已有合并 HDF5。

---

## 四、执行步骤

> 🔧 = 需要人工操作（数据库迁移、服务重启、历史产物清理等）

### Step 1：先补充 Worker 双目测试（代码自动）

1. 将现有单目测试改为左右眼不同画面的双目测试。
2. 增加缺失右眼、帧数不一致和多 episode 用例。
3. 先运行测试确认旧实现不能满足双目断言，形成明确的回归保护。

### Step 2：实现 Worker 双目编码（代码自动）

1. 定义两个眼睛的产物契约和 Pipeline 2.0 元数据。
2. 抽取 FFmpeg 生命周期管理方法。
3. 成对读取左右 JPEG 帧并分别写入编码器。
4. 实现任一眼失败则整体失败的资源清理。
5. 返回两个具有不同 `sourceKey` 的产物。
6. 运行 Worker 单元测试并使用 ffprobe 检查输出。

### Step 3：新增后端 G1 回放规则（代码自动）

1. 新增 `G1PlaybackRuleResolver`，使用 `@Order(0)` 覆盖 G1，其他 Profile 继续走 Default Resolver。
2. 实现完整双目优先、旧版单目回退、部分双目不可播放的规则。
3. 新增 Resolver 单元测试。
4. 调整 Default Resolver 测试，清晰分离通用与 G1 职责。
5. 运行定向 Maven 测试。

### Step 4：完善前端双目同步（代码自动）

1. 增加视频加载屏障、时长集合和左眼主时钟。
2. 增加漂移阈值和右眼校正。
3. 统一播放、暂停、跳转和逐帧控制。
4. 防止独立原生 controls 打破同步。
5. 执行 TypeScript 检查和生产构建。

### Step 5：生成并审查数据库迁移（代码自动，执行需人工）🔧

1. 新增只更新 Pipeline 展示元数据的 SQL 文件。
2. 检查 SQL 包含验证查询和回滚语句。
3. 由有权限人员在目标数据库执行，不由编码代理直连数据库。

### Step 6：重新生成 Worker Manifest（代码自动）

1. 通过 Worker 自带命令重新生成 `pipeline-manifest.json`。
2. 检查 Pipeline ID、版本、描述和输入输出类型。
3. 确认 Backend DB 定义与 Worker manifest 的资产类型一致。

### Step 7：部署 Backend 与 Worker 🔧

1. 先部署并重启 Backend，使 G1 专用 Resolver 生效。
2. 再部署并重启 Worker，使其注册 `G1_GENERATE_PLAYBACK` 2.0。
3. 检查 Worker 注册列表和心跳日志。

### Step 8：升级历史参考 Session 🔧

1. 在 Session 详情页找到旧的 `G1_GENERATE_PLAYBACK` 成功 Job。
2. 使用管理员“清除产物”功能仅清理该播放 Job；不得删除 `G1_MERGE_CAMERA_ROBOT` 产物。
3. 重新提交 `G1_GENERATE_PLAYBACK`。
4. 等待 Worker 生成两个 MP4 并上报成功。
5. 打开 `/play/test-4-upload-session?jobId=<新JobId>` 验证左右眼。

### Step 9：真实数据验收与回归（代码自动 + 人工）🔧

1. 下载左右 MP4，用 ffprobe 验证两边均为 299 帧、20 FPS、约 14.95 秒。
2. 检查 DataFile、DataAsset 和血缘均各有两份且属于同一 Job。
3. 检查回放 API 返回左眼、右眼两个 source，顺序固定。
4. 在浏览器持续播放并执行暂停、跳转、逐帧、倍速，观察同步状态。
5. 用旧版单目 Job 验证兼容回退。

---

## 五、手动操作汇总

> 汇总第四章所有 🔧 步骤。命令中的尖括号值由目标环境负责人替换；API 请求需要管理员登录 Cookie。

| # | 操作 | 命令 | 预期结果 |
| --- | --- | --- | --- |
| 1 | 执行 Pipeline 元数据迁移 | `mysql -h <DB_HOST> -P <DB_PORT> -u <DB_USER> -p <DB_NAME> < database-migrations/2026-08-19-update-g1-stereo-playback.sql` | `G1_GENERATE_PLAYBACK` 描述更新为双目输出；schema 和 Profile Source 不变 |
| 2 | 重启 Backend | 按目标环境现有部署方式重启 `mmdp-backend` | Spring 注册 `G1PlaybackRuleResolver`，健康检查正常 |
| 3 | 重启 Worker | 按目标环境现有部署方式重启对应 CPU Worker | Worker 注册 `G1_GENERATE_PLAYBACK` 2.0，轮询正常 |
| 4 | 清理旧单目播放 Job | `curl -X DELETE -b "<SESSION_COOKIE>" "http://localhost:19021/api/admin/processing-jobs/<OLD_JOB_ID>/outputs"` | 只删除旧播放 Job 的 MP4、DataFile、DataAsset、血缘和 Job；合并 HDF5 保留 |
| 5 | 重新提交播放 Job | `curl -X POST -b "<SESSION_COOKIE>" -H "Content-Type: application/json" -d '{"pipelineId":"G1_GENERATE_PLAYBACK"}' "http://localhost:19021/api/sessions/<SESSION_DB_ID>/processing-jobs"` | 新 Job 进入 CREATED，随后被 Worker 领取并 SUCCESS |
| 6 | 查询双目回放 | `curl -b "<SESSION_COOKIE>" "http://localhost:19021/api/sessions/test-4-upload-session/playback?jobId=<NEW_JOB_ID>"` | `sources` 同时包含 `camera_svo2_left` 和 `camera_svo2_right` |
| 7 | 浏览器验收 | 打开 `http://localhost:5173/play/test-4-upload-session?jobId=<NEW_JOB_ID>` | 两列显示左右眼，统一播放、跳转、逐帧和倍速保持同步 |

生产环境必须将上述 `localhost` 替换为实际服务地址；不要把数据库密码或 Session Cookie 写入仓库、命令历史或 build log。

---

## 六、验证清单

### 6.1 自动化测试

- [ ] Worker 正常双目输入返回且只返回两个 MP4。
- [ ] 左右输出文件名分别为 `g1_playback_left.mp4`、`g1_playback_right.mp4`。
- [ ] 左右 `sourceKey` 分别为 `camera_svo2_left`、`camera_svo2_right`。
- [ ] 左右视频帧数、分辨率、FPS、时长一致。
- [ ] 左右首帧内容不同，排除复制左眼的错误。
- [ ] 缺少任一 HDF5 数据集时 Job 失败。
- [ ] 左右帧数不一致时 Job 失败。
- [ ] 多 episode 按自然顺序拼接，左右累计帧数一致。
- [ ] 后端完整双目返回两个 source，只有一只眼时不判定完整双目可播放。
- [ ] 后端旧版单目回退正常。
- [ ] 双目与旧版同时存在时优先双目。
- [ ] 非 G1 Profile 继续使用 Default Resolver。
- [ ] 前端 TypeScript 检查和生产构建通过。

### 6.2 自动化命令

```powershell
# Worker 定向测试
Set-Location D:\workspace\multimodal-data-platform\mmdp-worker
& 'D:\software\Anaconda3\envs\pose_env\python.exe' -m unittest test_g1_generate_playback.py

# Backend 定向测试
Set-Location D:\workspace\multimodal-data-platform\mmdp-backend
mvn -Dtest=G1PlaybackRuleResolverTest,DefaultPlaybackRuleResolverTest test

# Frontend 类型检查与生产构建
Set-Location D:\workspace\multimodal-data-platform\mmdp-frontend
npm run build

# 重新生成并展示 Worker Manifest
Set-Location D:\workspace\multimodal-data-platform\mmdp-worker
& 'D:\software\Anaconda3\envs\pose_env\python.exe' main.py --list-pipelines
```

### 6.3 真实参考数据验收

- [ ] `test-4-upload-session` 不重新执行 `G1_MERGE_CAMERA_ROBOT`。
- [ ] 新播放 Job 只消费现有 `G1_MERGED_HDF5`。
- [ ] Job 产出两个 DataFile 和两个 DataAsset，`producedByJobId` 相同。
- [ ] 左右 MP4 均为 299 帧、20 FPS、约 14.95 秒。
- [ ] 回放 API 返回两个带下载 URL 的 video source。
- [ ] 页面默认两列，左眼在前、右眼在后。
- [ ] 播放 1 分钟或循环压力测试时，左右眼时间差不超过一个视频帧。
- [ ] 暂停、拖动、±5 秒、逐帧、0.5～2 倍速后仍同步。
- [ ] 任一路加载失败时页面明确显示错误，不把部分双目标记为完整可播放。

### 6.4 回归验证

- [ ] 旧版 `g1_playback.mp4` 仍能作为单目历史数据播放。
- [ ] Fake Stereo、普通视频和 IMU Profile 回放不受影响。
- [ ] Session 详情页的 `playback/check` 在完整双目和旧版单目情况下均符合兼容策略。
- [ ] Job 清理只删除目标 Job 的两个播放产物，不删除合并 HDF5。
- [ ] Pipeline 可用性检查仍以 `G1_MERGED_HDF5` 为输入，不要求新增资产类型。

### 6.5 curl 验证命令

```bash
# 创建双目播放 Job
curl -X POST \
  -b "<SESSION_COOKIE>" \
  -H "Content-Type: application/json" \
  -d '{"pipelineId":"G1_GENERATE_PLAYBACK"}' \
  "http://localhost:19021/api/sessions/<SESSION_DB_ID>/processing-jobs"

# Job 成功后检查回放数据
curl -b "<SESSION_COOKIE>" \
  "http://localhost:19021/api/sessions/test-4-upload-session/playback?jobId=<NEW_JOB_ID>"

# 检查是否可播放
curl -b "<SESSION_COOKIE>" \
  "http://localhost:19021/api/sessions/test-4-upload-session/playback/check?jobId=<NEW_JOB_ID>"
```

回放响应至少满足：

```json
{
  "sources": {
    "camera_svo2_left": {
      "type": "video",
      "label": "ZED 左眼",
      "videoUrl": "/api/files/<LEFT_FILE_ID>/download",
      "fps": 20.0
    },
    "camera_svo2_right": {
      "type": "video",
      "label": "ZED 右眼",
      "videoUrl": "/api/files/<RIGHT_FILE_ID>/download",
      "fps": 20.0
    }
  }
}
```

---

## 七、文档记录

> 所有实现与验证完成后，按本章约定生成与本计划一一对应的 build log。计划阶段不提前填写实际结果。

### 7.1 文档命名

遵循已有命名格式：`YYYY-MM-DD-N-description-build-log.md`。

对应文档：`docs/development/2026-08-19-1-g1-stereo-playback-build-log.md`

若 `docs/development/` 尚不存在，实施完成时创建该目录；不得改用其他目录或序号，确保与 `0819-1-g1-stereo-playback-execution-plan.md` 一一对应。

### 7.2 文档内容要求

构建记录必须包含：

1. **概述**：说明单目问题、双目目标、参考数据验证结论和最终范围。
2. **Worker 双目生成**：列出修改文件、最终产物契约、FFmpeg 生命周期与失败原子性。
3. **后端回放映射**：记录 G1 Resolver、双目优先和旧版回退的最终实现。
4. **前端同步**：记录主时钟、漂移阈值、加载屏障、最小时长和统一控制行为。
5. **测试与真实数据验证**：填写自动化命令、真实返回、左右 MP4 的 ffprobe 结果及浏览器验收结果。
6. **手动操作清单（必须）**：逐项记录数据库迁移、Backend/Worker 重启、旧 Job 清理和新 Job 提交，包含命令、期望结果、执行状态（⬜/✅/❌）和备注。
7. **兼容性与问题记录**：说明旧单目数据如何回退，以及实施中遇到的问题和解决方案。
8. **附录：文件变更清单**：汇总所有新增、修改、生成的文件。

不得在 build log 中记录数据库密码、OSS 密钥、Session Cookie 或其他敏感配置。

### 7.3 文档生成时机

- Worker、后端、前端和迁移文件全部实现后，先创建 build log 的结构。
- 自动化测试执行后填入准确的命令、耗时和结果，不写预估结果。
- 对 `test-4-upload-session` 完成真实 Job 验证后，填入新 Job ID、两个 DataFile ID、帧数、FPS、时长和同步验收结论。
- 所有手动操作完成后更新状态；仍未执行的操作保持 ⬜，不得提前标记完成。
- 最终交付前核对 build log 与本计划的文件清单、验收项和实际实现一致。
