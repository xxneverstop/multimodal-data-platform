你的理解 **基本正确**。可以明确一句：

> Python Worker 不是被前端直接调用的算法接口，而是后端调度体系里的“异步处理执行器”。

你这条链路应该是：

```
本地整理 Session 目录
        ↓
前端目录上传
        ↓
文件直传 OSS
        ↓
后端 finalize
        ↓
创建 Session / 原始资产 / data_file / import_record
        ↓
用户在平台点击“生成回放资产 / 预处理”
        ↓
前端请求后端：创建 processing_job
        ↓
processing_job 进入 PENDING
        ↓
Python Worker 从后端领取任务
        ↓
Worker 从 OSS 拉取输入数据
        ↓
Worker 本地处理
        ↓
Worker 上传结果到 OSS
        ↓
Worker 通知后端处理完成
        ↓
后端创建输出资产、处理记录、血缘关系
        ↓
前端看到可播放资产
```

你理解中需要微调的点主要有几个。

------

## 1. 不是“消息发到 Python Worker”，而是“后端创建工单，Worker 来领”

你说：

> 点击处理之后，这个消息发到 Python Worker。

更准确应该是：

```
前端点击处理
        ↓
前端调用后端接口
        ↓
后端创建 processing_job
        ↓
Worker 轮询/监听后端
        ↓
Worker claim 这个 job
```

第一版建议不要让后端主动推给 Worker，也不要前端直连 Worker。

因为 Worker 可能：

```
没启动
正在忙
部署在内网
有多个 Worker
任务失败需要重试
任务需要排队
```

所以更稳的是：

```
后端是任务中心
Worker 是来领活的工人
```

这样状态更好管理。

------

## 2. 前端只负责“发起处理”，不负责“处理过程”

前端应该只做：

```
点击按钮：创建处理任务
查看状态：PENDING / RUNNING / SUCCESS / FAILED
查看产物：输出资产列表
```

前端不应该知道：

```
Worker 地址
Worker 端口
Worker 如何下载 OSS
Worker 如何上传 OSS
Worker 运行在哪台机器
```

否则后面 Worker 扩容、迁移、改成 GPU Worker、改成队列都会很麻烦。

正确边界是：

```
前端 → 后端：我要处理这个 Session
后端 → Worker：这里有一个处理工单
Worker → 后端：我处理完了，产物在这里
```

------

## 3. Worker 上传结果是否一定要通过后端获取临时凭证？

你的理解是：

> Worker 也要通过后端获取上传凭证，跟前端上传一样。

这个思路是安全的，但可以分阶段看。

### 第一种：Worker 自己持有 OSS AK/SK

适合早期 MVP。

```
Worker 直接用服务器环境变量里的 OSS AK/SK 上传结果
```

优点：

```
实现简单
链路短
不用额外设计签名接口
```

缺点：

```
Worker 权限较大
安全边界没那么细
```

### 第二种：Worker 通过后端申请临时上传凭证

适合更成熟阶段。

```
Worker 请求后端
后端生成限定路径、限定时间的上传凭证
Worker 使用凭证上传到 OSS
```

优点：

```
权限更细
路径更可控
更符合平台统一存储治理
```

缺点：

```
开发复杂一点
需要额外接口
```

你当前阶段可以先用：

```
Worker 持有 OSS 写权限，但只允许写 processed/ 路径
```

后面再收敛成临时凭证。

------

## 4. 最关键的是：Worker 不应该直接改数据库

这一点你一定要注意。

不要让 Python Worker 直接连 MySQL 修改表。

错误方式：

```
Worker 处理完
        ↓
Worker 直接 insert data_asset
        ↓
Worker 直接 update processing_job
```

推荐方式：

```
Worker 处理完
        ↓
调用后端 success 接口
        ↓
后端统一创建资产、血缘、处理记录
```

原因是：

```
资产规则在后端统一维护
权限和校验在后端统一做
避免 Python 和 Java 两边都写业务规则
数据库结构变化时 Worker 不容易崩
```

所以 Worker 只负责：

```
处理数据
上传文件
汇报结果
```

后端负责：

```
生成资产记录
生成血缘关系
更新 job 状态
记录 QC 指标
```

------

## 5. 你还没重点考虑的关键点

### 关键点一：输入资产和输出资产要有明确类型

比如原始输入：

```
RAW_ZED_SVO2
RAW_IMU_CSV
RAW_TIMESTAMP_CSV
```

处理输出：

```
PLAYBACK_MANIFEST
LEFT_RGB_VIDEO
RIGHT_RGB_VIDEO
DEPTH_NPZ
IMU_ALIGNED_CSV
QC_SUMMARY_JSON
```

不能只记录“生成了几个文件”。

否则后面前端不知道哪个文件用来播放，哪个文件用来质检，哪个文件用来标注。

------

### 关键点二：处理任务必须和 Profile 绑定

例如：

```
profileCode = zed_imu_v1
jobType = BUILD_PLAYBACK
```

后端要校验：

```
这个 Session 是否符合 zed_imu_v1
这个 Profile 是否支持 BUILD_PLAYBACK
这个任务需要哪些输入资产
当前 Session 是否都有
```

否则用户上传一堆不完整文件，也能点处理，Worker 就会频繁失败。

------

### 关键点三：处理任务要幂等

用户可能重复点击“生成回放资产”。

你要避免重复生成一堆资产。

可以设计成：

```
同一个 sessionId + jobType + profileCode
如果已经有 RUNNING，则不允许重复创建
如果已经 SUCCESS，可以提示“已生成，可重新处理”
如果用户选择重新处理，则创建新版本
```

简单规则：

```
默认不重复处理
需要重新处理时，显式点击“重新生成”
```

------

### 关键点四：输出资产要有版本

比如第一次生成：

```
PLAYBACK_MANIFEST v1
```

后面算法升级后重新生成：

```
PLAYBACK_MANIFEST v2
```

不要直接覆盖原来的资产记录。

OSS 路径也建议带 jobId：

```
processed/sessions/{sessionId}/jobs/{jobId}/playback_manifest.json
```

这样天然可追溯。

------

### 关键点五：状态不能只有成功和失败

至少要有：

```
PENDING     等待处理
CLAIMED     已被 Worker 领取
RUNNING     处理中
SUCCESS     成功
FAILED      失败
CANCELLED   取消
```

MVP 可以先做：

```
PENDING / RUNNING / SUCCESS / FAILED
```

但是数据库字段最好预留。

------

### 关键点六：Worker 要有 heartbeat

因为 Worker 可能处理到一半崩了。

所以最好有：

```
lastHeartbeatAt
workerId
startedAt
finishedAt
errorMessage
retryCount
```

Worker 每隔一段时间告诉后端：

```
我还活着，这个 job 还在跑。
```

如果长时间没有 heartbeat，后端可以把任务标记为：

```
FAILED 或 TIMEOUT
```

------

### 关键点七：大文件处理不要全量加载到内存

ZED、视频、depth 文件可能很大。

Worker 处理时要注意：

```
下载到本地临时目录
流式处理
处理完清理临时文件
不要全部读进内存
```

临时目录可以类似：

```
/tmp/mmdp-worker/job-{jobId}/
```

处理完成后删除。

------

### 关键点八：可播放格式要标准化

你说“转化成能播放的格式”，这个需要定义清楚。

例如第一版可以规定：

```
playback_manifest.json 是播放器入口文件
视频统一 mp4
时间戳统一 frame_timestamps.csv
深度数据统一 depth.npz
IMU 数据统一 imu_aligned.csv
```

播放器不要自己去猜文件，而是只读：

```
PLAYBACK_MANIFEST
```

这个 manifest 告诉前端：

```
{
  "streams": [
    {
      "type": "video",
      "name": "left_rgb",
      "assetType": "LEFT_RGB_VIDEO"
    },
    {
      "type": "imu",
      "name": "imu",
      "assetType": "IMU_ALIGNED_CSV"
    }
  ]
}
```

这样后面换设备，前端也不用大改。

------

## 6. 你这条链路最终应该长这样

更准确的工程版是：

```
1. 本地整理 session 目录
2. 前端上传目录到 OSS 临时区
3. 前端调用后端 finalize
4. 后端创建：
   - collection_session
   - data_file
   - data_asset 原始资产
   - session_import_record

5. 用户点击“生成回放资产”
6. 前端调用：
   POST /api/sessions/{sessionId}/processing-jobs

7. 后端创建：
   - processing_job
   - status = PENDING
   - jobType = BUILD_PLAYBACK
   - inputAssetIds = [...]

8. Python Worker 轮询：
   POST /api/worker/jobs/claim

9. 后端返回一个 job

10. Worker 下载输入文件

11. Worker 处理生成：
   - playback_manifest.json
   - left.mp4
   - right.mp4
   - imu_aligned.csv
   - qc_summary.json

12. Worker 上传输出到 OSS

13. Worker 调用：
   POST /api/worker/jobs/{jobId}/success

14. 后端创建：
   - 输出 data_file
   - 输出 data_asset
   - processing_job SUCCESS
   - lineage 输入资产 → job → 输出资产
   - qc_report 可选

15. 前端刷新后看到：
   - 处理成功
   - 新增可播放资产
   - 可以进入回放页
```

------

## 7. 最重要的结论

你现在的理解没有大问题，只需要把几个边界定清楚：

```
前端不直接调用 Worker
Worker 不直接改数据库
后端是唯一任务调度和资产登记中心
Worker 只负责处理文件和汇报结果
OSS 存原始文件和处理产物
processing_job 是连接后端和 Worker 的工单
data_asset 是处理前后的资产表达
lineage 记录输入资产如何生成输出资产
```

你当前阶段最推荐做的 MVP 是：

> 前端点击“生成回放资产” → 后端创建 processing_job → Python Worker 领取任务 → 生成一个 playback_manifest.json 和简单 qc_summary.json → 上传 OSS → 后端创建输出资产和血缘。

先不要急着真正做复杂 ZED 解码。

先跑通：

```
上传 → 入库 → 点处理 → Worker 执行 → 产物回写 → 资产血缘 → 前端展示
```

这条闭环一旦跑通，你的平台就从“上传管理系统”升级成真正的“多模态数据处理平台”。