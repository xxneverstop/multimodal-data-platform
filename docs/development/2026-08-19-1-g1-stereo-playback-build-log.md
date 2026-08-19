# G1 完整双目回放构建记录

> 对应计划：`docs/plans/0819-1-g1-stereo-playback-execution-plan.md`
> 实施日期：2026-08-19
> 当前状态：代码实现与本地自动化验证完成；数据库迁移、服务部署和真实 Job 验收待人工执行

## 一、概述

原 `G1_GENERATE_PLAYBACK` 只读取 `observation_image_left`，因此只生成左眼 `g1_playback.mp4`。本次将其升级为 2.0，一次生成左右眼两个 MP4，并补充 G1 专用后端映射和前端同步控制；Pipeline ID、输入/输出 AssetType、API 和数据库结构均未改变。

参考数据 `data/g1-rebot-1/test-4-upload-session` 已只读验证可支持该方案：

- SVO2 为 ZED-M、1280×720、30 FPS，标称 448 帧，实际可解码 447 帧；首、中、末位置左右眼均可解码且画面哈希不同。
- Robot HDF5 有 299 个单调递增时间戳，时长 14.863736 秒，约 20.05 FPS。
- 按现有最近帧匹配逻辑，平均误差 8.31 ms、最大误差 18.93 ms，没有超过 20 ms 的帧。
- 现有 `G1_MERGE_CAMERA_ROBOT` 会为这 299 个 Robot 时间点分别写入左右眼 JPEG，因此预期合并产物包含左右眼各 299 帧。

本次没有修改参考数据、没有重新执行合并 Pipeline，也没有连接线上服务提交或清理 Job。

## 二、Worker 双目生成

修改文件：

- `mmdp-worker/pipelines/g1_generate_playback.py`
- `mmdp-worker/test_g1_generate_playback.py`
- `mmdp-worker/pipeline-manifest.json`

最终产物契约：

| 眼别 | HDF5 数据集 | sourceKey | 文件名 | AssetType |
| --- | --- | --- | --- | --- |
| 左眼 | `observation_image_left` | `camera_svo2_left` | `g1_playback_left.mp4` | `RGB_VIDEO_MP4` |
| 右眼 | `observation_image_right` | `camera_svo2_right` | `g1_playback_right.mp4` | `RGB_VIDEO_MP4` |

实现行为：

- 输入文件按自然顺序拼接，例如 `episode_2` 在 `episode_10` 之前。
- 编码前检查每个 HDF5 同时存在左右数据集，并要求左右帧数相等。
- 两个 FFmpeg 进程以 20 FPS、H.264、`yuv420p`、`faststart` 参数成对接收 JPEG 帧。
- 输出先写入临时 MP4；任一路启动、写入或结束失败都会终止全部编码器并删除临时文件。
- 只有左右编码均成功、帧数相等且文件非空时，才返回两个产物供现有 Worker 上传逻辑处理。
- Pipeline manifest 已通过 Worker 自带命令重新生成，版本为 `2.0.0`，输入输出 AssetType 保持不变。

## 三、后端回放映射

新增 `G1PlaybackRuleResolver`，仅处理 `G1_TELEOP_V1`：

- 完整找到 `camera_svo2_left` 和 `camera_svo2_right` 两个 MP4 时，固定按左眼、右眼顺序返回。
- 只有其中一路时不把它判定为完整双目，也不返回半套双目 source。
- 没有完整双目时，兼容历史 `camera_svo2/g1_playback.mp4`，以“ZED 左眼（旧版）”单路返回。
- 双目和旧版单目同时存在时只返回双目。
- FPS 优先使用 Profile 中 `camera_svo2` 的 `expectedFps`，缺失时回退到 20 FPS。
- 通用 `DefaultPlaybackRuleResolver` 保持原职责，其测试已改成非 G1 的通用相机场景。

## 四、前端同步播放

`PlaybackView.vue` 的同步控制已调整为：

- 记录每一路视频的可播放状态和实际时长，全部就绪后才允许统一播放。
- 使用后端返回的第一路（G1 固定为左眼）作为主时钟。
- `play()` 通过 `Promise.all()` 同时启动；任一路失败会暂停全部视频并显示错误。
- 右眼与左眼偏差超过 `max(半帧, 25 ms)` 时，将右眼校正到左眼时间。
- 时间轴上限取已加载视频时长的最小值，任一路结束即暂停全部视频。
- 播放、暂停、拖动、±5 秒、逐帧和倍速统一作用于所有视频。
- 移除每个 `<video>` 的原生 controls，避免用户单独拖动某一路破坏同步。

## 五、数据库迁移

已生成 `database-migrations/2026-08-19-update-g1-stereo-playback.sql`，只更新 `pipeline_definition` 的显示名称和描述。文件包含验证查询和注释形式的回滚 SQL。

以下内容保持不变：

- `pipeline_id = G1_GENERATE_PLAYBACK`
- `input_asset_types = ["G1_MERGED_HDF5"]`
- `output_asset_types = ["RGB_VIDEO_MP4"]`
- Profile-Pipeline 关联、Profile Source 和数据库 schema

该 SQL 未执行。

## 六、自动化验证结果

### 6.1 Worker

命令：

```powershell
Set-Location D:\workspace\multimodal-data-platform\mmdp-worker
& 'D:\software\Anaconda3\envs\pose_env\python.exe' -m unittest test_g1_generate_playback.py
```

结果：5 个测试全部通过，最终复验用时 0.212 秒。覆盖：

- 两个 MP4 的文件名、sourceKey、AssetType、MIME 和非空文件。
- 两边均为 3 帧、32×24、20 FPS，且首帧画面内容不同。
- 缺少右眼数据集失败。
- 左右帧数不一致失败且不发布最终 MP4。
- 多 episode 自然排序、双路累计和输出顺序。

### 6.2 Backend

命令：

```powershell
Set-Location D:\workspace\multimodal-data-platform\mmdp-backend
mvn "-Dtest=G1PlaybackRuleResolverTest,DefaultPlaybackRuleResolverTest" test
```

结果：6 个测试全部通过，Maven `BUILD SUCCESS`，最终复验总用时 12.080 秒。覆盖 G1 规则匹配、完整双目、左右任一单路、旧版回退、双目优先和非 G1 通用规则。

### 6.3 Frontend

命令：

```powershell
Set-Location D:\workspace\multimodal-data-platform\mmdp-frontend
npm run build
```

结果：`vue-tsc -b` 与 Vite 生产构建通过，Vite 编译 499 个模块，最终复验用时 3.73 秒。仅有仓库既有的大 chunk 警告，无类型或构建错误。首次在受限沙箱中因 esbuild 子进程 `spawn EPERM` 失败，允许子进程执行后构建成功；这不是代码错误。

### 6.4 Worker Manifest

命令：

```powershell
Set-Location D:\workspace\multimodal-data-platform\mmdp-worker
& 'D:\software\Anaconda3\envs\pose_env\python.exe' main.py --list-pipelines
```

结果：11 个 Pipeline 正常注册并生成 manifest；`G1_GENERATE_PLAYBACK` 显示为 2.0.0 双目版本。

## 七、真实数据与人工验收状态

当前完成的是参考原始数据的只读可行性验证，不等同于部署后的真实 Job 验收。由于本次没有启动 Backend/Worker、没有执行数据库迁移，也没有清理旧 Job，以下 ID 和线上产物指标尚不存在，不能提前标记完成。

| 状态 | 操作 | 期望结果 | 备注 |
| --- | --- | --- | --- |
| ⬜ | 在目标数据库执行迁移 SQL | Pipeline 展示信息更新，schema 不变 | 未执行；需有权限人员操作 |
| ⬜ | 部署并重启 Backend | `G1PlaybackRuleResolver` 生效，健康检查正常 | 未执行 |
| ⬜ | 部署并重启 CPU Worker | Worker 注册 `G1_GENERATE_PLAYBACK` 2.0 | 未执行 |
| ⬜ | 仅清理参考 Session 的旧播放 Job | 旧单目播放产物被清理，合并 HDF5 保留 | 未执行；属于破坏性操作 |
| ⬜ | 基于现有合并 HDF5 提交新播放 Job | 同一 Job 生成两个 DataFile/DataAsset | 未执行 |
| ⬜ | 用 ffprobe 检查真实左右 MP4 | 各 299 帧、20 FPS、约 14.95 秒 | 未生成真实新产物 |
| ⬜ | 查询指定新 Job 的回放 API | 左、右两个 source 和下载 URL，顺序固定 | 待新 Job ID |
| ⬜ | 浏览器同步验收 | 两列展示；播放、seek、逐帧、倍速后仍同步 | 待部署 |
| ⬜ | 旧单目 Job 回归 | 返回“ZED 左眼（旧版）” | 自动化已覆盖，真实环境待验收 |

具体人工命令及安全注意事项见对应执行计划第五章。命令中的数据库口令、管理员 Cookie、OSS 密钥不得写入本记录。

## 八、兼容性与问题记录

- 旧版单目数据不需要立即重跑；后端仍识别旧 `g1_playback.mp4`。
- 新 Worker 不再接受缺失任一眼的合并 HDF5 作为成功结果，这是完整双目契约的预期变化。
- 派生左右 `sourceKey` 不加入 `collection_profile_source`，不会改变上传目录要求。
- Worker 原有多产物上传和 Backend 多产物落库逻辑可直接复用，本次未修改协议。
- 真实参考数据验收仍依赖部署、数据库权限和新 Job，已作为人工步骤保留。

## 九、文件变更清单

| 操作 | 文件 |
| --- | --- |
| 修改 | `mmdp-worker/pipelines/g1_generate_playback.py` |
| 修改 | `mmdp-worker/test_g1_generate_playback.py` |
| 生成 | `mmdp-worker/pipeline-manifest.json` |
| 新增 | `mmdp-backend/src/main/java/com/honortech/dataplatform/profile/rule/G1PlaybackRuleResolver.java` |
| 新增 | `mmdp-backend/src/test/java/com/honortech/dataplatform/profile/rule/G1PlaybackRuleResolverTest.java` |
| 修改 | `mmdp-backend/src/test/java/com/honortech/dataplatform/profile/rule/DefaultPlaybackRuleResolverTest.java` |
| 修改 | `mmdp-frontend/src/views/playback/PlaybackView.vue` |
| 新增 | `database-migrations/2026-08-19-update-g1-stereo-playback.sql` |
| 新增 | `docs/development/2026-08-19-1-g1-stereo-playback-build-log.md` |

## 十、专业双目预览增强

用户提供了上传及两次处理后的完整资产目录：`data/data-save/session-75-all-assets`。本次对 Job 110 的真实双目产物进行了只读验证：

| 文件 | 编码 | 分辨率 | FPS | 帧数 | 时长 |
| --- | --- | --- | --- | --- | --- |
| `g1_playback_left.mp4` | H.264 | 1280×720 | 20 | 299 | 14.95 秒 |
| `g1_playback_right.mp4` | H.264 | 1280×720 | 20 | 299 | 14.95 秒 |

第 0、149、298 帧的左右解码图像哈希均不同；逐像素完全相同比例分别约为 0.57%、0.84%、4.53%。Job 106 合并 HDF5 中 `observation_image_left/right` 均为 299 帧，对应抽样 JPEG 也均不相同。因此真实处理链路没有把左眼复制到右眼，“看起来相似”来自校正双目的高重叠视野和原双路平铺的展示方式。

`PlaybackView.vue` 在保留两个原始 MP4 和原双路质检模式的基础上，新增专业双目工作台：

- **扫描对比**：左右眼在同一视口叠加，拖动分割线直接观察水平视差。
- **闪烁对比**：左右眼按 1～8 Hz 可调频率交替显示，使视差表现为物体横向运动。
- **红青 3D**：使用 Canvas 在浏览器内实时提取左眼红通道和右眼青通道，并以 Screen 模式合成；支持交换眼位。
- **仪器 HUD**：显示双目锁定状态、分辨率、FPS、同步时间差和当前帧。
- **工业视觉样式**：采用石墨黑、校准青和警示琥珀配色，移除开发调试条，重构模式栏、视频舞台和响应式布局。
- **深度图入口**：当前禁用并明确提示“需深度资产”。现有 HDF5/MP4 不包含 ZED depth/disparity，未用左右差分伪装真实深度。

增强后的最终前端验证命令仍为 `npm run build`，`vue-tsc -b` 和 Vite 均通过；Vite 转换 499 个模块，最终复验用时 3.55 秒，仅保留仓库既有的大 chunk 警告。由于 Backend 当前因 MySQL 连接超时无法启动，真实 API 页面交互验收仍待数据库恢复后执行。
