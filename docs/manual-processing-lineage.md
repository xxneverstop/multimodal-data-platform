# 手动处理登记与数据链路

## 为什么当前阶段采用手动处理 + 平台登记

当前阶段的目标不是接入真实 Python 脚本，而是先让平台可以稳定演示一份数据从进入平台到后续处理输出的完整链路。为此，系统保留了现有上传、QC、资产、available-pipelines 和 mock processing job 能力，同时新增“手动处理完成后再回平台登记”的路径。

这样做有三个直接收益：

- 不阻塞前期演示，可以先把流程串起来。
- 每一轮处理都能记录输入资产、输出资产、工具版本、参数和备注。
- derived asset 可以被追踪，任务详情页能完整展示链路。

## 与现有 mock executor 的关系

- `POST /api/tasks/{taskId}/processing-jobs`
  - 仍然保留，继续作为 mock executor 路径。
  - 创建的 job 会标记为 `executorType = MOCK`。
- `POST /api/tasks/{taskId}/processing-jobs/manual`
  - 新增，作为当前默认推荐的处理登记路径。
  - 创建的 job 会标记为 `executorType = MANUAL`，并直接登记为 `SUCCESS`。

这两条路径可以并存，便于后续逐步过渡到真实 worker。

## 使用流程

1. 创建采集任务。
2. 上传平台内资产，或登记外部资产。
3. 在任务详情页的“登记处理流程”区域选择输入资产。
4. 填写输出资产、操作人、工具名、工具版本、参数 JSON、日志路径和备注。
5. 提交后系统会：
   - 创建一条 `processing_job`
   - 创建输出 `data_asset`
   - 建立 `input asset -> job -> output asset` 链路
6. 在任务详情页的“数据链路”区域查看完整链路。

## 推荐演示链路

可以用下面这条最小闭环来演示：

`创建任务`
-> `上传或登记 RGB_VIDEO_MP4`
-> `上传或登记 MOCAP_CSV`
-> `登记 SMPL_RESULT`
-> `手动登记 RGB_MOCAP_ALIGNMENT`
-> `生成 ALIGNED_RESULT`

最终在“数据链路”区域可以看到：

- `task`
- `RGB_VIDEO_MP4 / MOCAP_CSV / SMPL_RESULT`
- `RGB_MOCAP_ALIGNMENT job`
- `ALIGNED_RESULT`
