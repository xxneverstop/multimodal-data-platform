> 保存于 2026-07-09

# 将 motion_vis 和 MotionDB 的能力整合到 MMDP 平台

## 我们已经有了一条可行的路

在讨论还需要做什么之前，先看看已经完成的部分。这很重要，因为它验证了整合的技术路线是通的，后面的工作可以复用同样的模式。

motion_vis 的核心能力之一是 3D 人体动作可视化——给定一个 SMPL NPZ 文件，在浏览器里渲染出一个可以旋转缩放、逐帧播放的 3D 人体模型。要把这个能力接入 MMDP，我们面临一个选择：是把它作为一个独立服务跑在边上，还是融入平台的现有架构？

最终采用的是后者，而且这个决策被证明是对的。具体做法是把 motion_vis 的处理逻辑拆成两块，分别塞进 MMDP 已有的两个槽位里：

**处理端 → Worker Pipeline。** 新建了一个 `BUILD_MOTION_VIEWER_DATA` Pipeline（源码：`mmdp-worker/pipelines/build_motion_viewer_data.py`），做的事情很纯粹：读取 SMPL NPZ 文件，解析出每一帧的 poses、trans、betas 等数据，按 motion_vis 前端期望的 JSON 格式重新组装，产出 `MOTION_VIEWER_JSON` 资产。这个 Pipeline 与 MMDP 的图片转 MP4、双目对齐等 Pipeline 处于同等地位，被 Worker 以同样的轮询-领取-执行-上报循环驱动。它对平台的其他部分完全透明——后端不知道也不关心这个 Pipeline 内部做了什么，它只知道有一个处理作业被创建了、执行完了、产物上传到 OSS 了。

**展示端 → 前端全屏路由。** 新建了一个 `MotionViewer.vue`（源码：`mmdp-frontend/src/views/motion/MotionViewer.vue`，路由 `/motion-view/:sessionId`），用 Three.js 实现完整的 3D 查看器。它从 Session 的文件列表中找出所有 `MOTION_VIEWER_JSON` 类型的资产，下载后在浏览器中用 SkinnedMesh + 骨骼动画渲染。这个组件约 430 行，已经支持 SMPL/SMPLH/SMPLX 三种模型、多模型同屏对比、播放控制、倍速、循环、显示切换等完整功能。

除此之外，SMPL 系列模型的二进制资产文件（`v_template.bin`、`faces.bin`、`skinWeights.bin` 等）已经部署在前端的 `public/assets/` 目录下，覆盖了 SMPL/SMPLH/SMPLX 三个模型变体及其男女变体。这些文件是 Three.js SkinnedMesh 渲染的前置条件——浏览器需要它们来构建人体网格并绑定骨骼。

另外还有一个 `MOTION_PHYSICS_METRICS` Pipeline（源码：`mmdp-worker/pipelines/motion_physics_metrics.py`）已经上线，它对 SMPL NPZ 计算基础的物理质量指标——抖动（jitter）、地面穿透深度、浮空帧占比、位移范围、平均速度。但这是纯 numpy 的版本，只用到 NPZ 里已有的 poses 和 trans 数据，不涉及人体模型前向计算，因此指标覆盖面有限。这个 Pipeline 的存在恰好引出了下一个话题：完整版物理指标需要什么。

## 主框架完全不动，功能插入现有槽位

理解 MMDP 的架构骨架是理解整合方案的前提。平台的五个核心抽象是：**Profile**（定义数据源长什么样、怎么解析、怎么播放）→ **Session**（一次采集的物理数据集合）→ **Pipeline**（对数据做加工处理）→ **Asset**（加工后的产物，可被前端消费）→ **前端视图**（消费 Asset 呈现给用户）。

motion_vis 和 MotionDB 的功能，本质上都可以分解为这五个抽象中的某一个或某几个。不存在"把 motion_vis 整个嵌进来"这种需求——那意味着要同时嵌入一个 Flask 服务、一套 Jinja2 模板、一个文件系统扫描器，这既不必要也不合理。我们要做的是提取出它们的核心能力，分别填入 MMDP 的对应槽位。

以下是一个按槽位组织的对照表，把两个源项目的每一项能力都标明了它应该落入 MMDP 的哪个位置。这里不追求穷举，只列出有实际价值的项。

**落入 Pipeline 槽位的能力：**

| 源能力 | 来源 | 新 Pipeline ID | 说明 |
|--------|------|----------------|------|
| SMPL NPZ → 查看器 JSON | motion_vis | `BUILD_MOTION_VIEWER_DATA` | ✅ 已完成 |
| 基础物理指标（numpy） | motion_vis | `MOTION_PHYSICS_METRICS` | ✅ 已完成 |
| 完整物理指标（torch） | motion_vis | `MOTION_PHYSICS_METRICS_V2` | 待实现 |
| SOMA NPZ → 查看器 JSON | motion_vis | `BUILD_SOMA_VIEWER_DATA` | 待实现 |
| SOMA NPZ → FBX 转换 | motion_vis | `SOMA_TO_FBX` | 待实现 |
| BVH → 骨架数据 JSON | motion_vis | `PARSE_BVH` | 待实现 |
| T2M NPZ → 标准 NPZ | motion_vis | `CONVERT_T2M_NPZ` | 按需 |

**落入前端视图槽位的能力：**

| 源能力 | 来源 | MMDP 目标 | 说明 |
|--------|------|-----------|------|
| 3D 动作查看器（SMPL） | motion_vis | `MotionViewer.vue` | ✅ 已完成 |
| SOMA 渲染路径 | motion_vis | 扩展 MotionViewer | 待实现 |
| FBX 直接渲染 | motion_vis | 扩展 MotionViewer | 待实现 |
| BVH 骨架渲染 | motion_vis | 扩展 MotionViewer | 待实现 |
| 跨 Session 对比模式 | motion_vis | 扩展 MotionViewer | 待实现 |
| Session 详情页文件分组视图 | MotionDB | Session 详情页增强 | 待实现 |
| 文件排序（按物理指标/标注） | MotionDB | Session 详情页增强 | 待实现 |
| 上/下一个/随机跳转 | MotionDB | MotionViewer 内导航 | 待实现 |

**落入后端 API + 数据库槽位的能力：**

| 源能力 | 来源 | MMDP 目标 | 说明 |
|--------|------|-----------|------|
| 动作标注系统 | MotionDB | 新表 `motion_annotation` + API | 待实现 |
| 标注进度统计 | MotionDB | 后端统计接口 | 待实现 |
| NPZ 文件信息查询 | MotionDB | Asset 详情 API 扩展 | 按需 |

**不迁移的能力：**

MotionDB 的根路径管理器和路径缓存系统不迁移。原因很直接：MotionDB 是一个文件系统浏览器，它的核心假设是"数据都在本机磁盘上，用户自己选目录"。MMDP 的数据管理模型完全不同——数据上传到 OSS，元数据存在 MySQL，文件发现通过 `sessionimport` 模块完成。把 MotionDB 的目录切换功能搬到 MMDP 相当于在两个不兼容的假设之间强行搭桥，产出远小于投入。

motion_vis 的 Flask 后端也不迁移。它的 API（`/api/motion_list`、`/api/motion/<filename>`、`/api/analyze_bvh/<filename>`）功能已经被 Worker Pipeline + Spring Boot Controller 的组合覆盖。例如原来 Flask 读 NPZ 返回 JSON 的操作，现在由 `BUILD_MOTION_VIEWER_DATA` Pipeline 离线完成，JSON 作为 Asset 存在 OSS 上，前端直接通过 `/api/files/{id}/download` 下载，比每次请求时实时解析更高效。

## torch 依赖的真相：只影响 Worker 端，且算法已有现成实现

这是整个方案中最容易被误解的一个点，值得单独展开讲清楚。

### 为什么需要 torch？

现有的 `MOTION_PHYSICS_METRICS` Pipeline 只做纯 numpy 层面的计算——读取 NPZ 中的 `poses`（关节旋转角度）和 `trans`（根节点位移），直接对这些数值做统计。这样做能算出"相邻帧之间的角度变化有多大"（jitter），但算不出"脚底有没有在地面上滑动"（skate），也算不出"手腕有没有不自然地扭曲"（wrist twist）。

原因在于：要知道脚底是否滑动，需要知道每一帧**脚底顶点的世界坐标**。而 `poses` 里存的是关节的**局部旋转角度**，不是顶点的世界位置。从关节角度算出顶点位置，需要经过一个叫**线性混合蒙皮**（Linear Blend Skinning, LBS）的计算过程——把 22 个关节的旋转矩阵作用于 6890 个顶点，加权混合得到每个顶点的最终位置。这个计算需要矩阵乘法、批量旋转转换、前向运动学（FK），用 numpy 不是不能做，但 motion_vis 里已经有一套写好的、经过验证的 torch 实现（`phys_metrics.py` 中的 `BodyModel` + `compute_verts_joints`），直接复用是最明智的选择。

torch 在 `phys_metrics.py` 中的实际用途，按调用链展开是这样的：

第一步，加载 NPZ 文件，提取 poses（关节角度）、betas（体型参数）、trans（根位移）→ 这些都是 numpy 操作，不需要 torch（`phys_metrics.py:155-262`）。

第二步，把 numpy 数组搬到 torch tensor 上，调用 `BodyModel.forward()`——这是 SMPL 人体模型的 PyTorch 实现，输入关节角度和体型参数，输出 6890 个顶点和 52 个关节的世界坐标（`phys_metrics.py:297-367`）。这个 `BodyModel` 类来自 motion_vis 的 `body_model/body_model.py`，内部依赖 torch 的矩阵运算和自动求导机制（虽然推理时不需要梯度）。

第三步，从顶点和关节坐标出发，计算具体的物理指标：用 KNN 找脚底顶点 → 按高度百分位筛选 → 逐帧检测穿透/浮空/滑动 → 统计关节角度跳变率 → 检测手腕扭曲（`phys_metrics.py:522-652`）。这里面有一些操作（如关节跳变检测中的轴角转旋转矩阵、旋转矩阵间角度计算）也需要 torch，但都是基础张量运算，不涉及训练或梯度。

### 改动范围：仅 Worker 端

这个结论可以用一个简单的"影响半径"来分析：

**Worker 端 —— 需要改。** 新的 `MOTION_PHYSICS_METRICS_V2` Pipeline 需要 import torch 和 BodyModel。同时，Worker 的 Docker 镜像需要安装 torch（`pip install torch`），这会让镜像体积增加约 2GB。现有的 7 个 Pipeline 完全不受影响——它们只用 numpy 和 ffmpeg，torch 的引入不会改变它们的行为。

**后端（Spring Boot）—— 零改动。** 后端只负责创建 ProcessingJob、记录 Asset 和 AssetLineage。它对 Pipeline 内部用了什么库、算了什么指标一无所知。新增一个 `PHYSICS_REPORT_V2` AssetType 只需要在 `AssetType.java` 枚举中加一行，以及在前端类型定义中加一个字符串字面量。

**前端 —— 零改动（除非要做报告展示）。** 如果只是产出 `PHYSICS_REPORT_V2` JSON 文件并存为 Asset，前端不需要任何改动。如果后续想在 Session 详情页或 MotionViewer 中展示物理指标报告，那才需要加一个报告面板组件。但这与 torch 无关——前端只是读 JSON 然后渲染。

**数据库 —— 零改动。** Pipeline 产出的 JSON 文件上传到 OSS 后，元数据（文件名、大小、类型）存在现有的 `data_asset` 表中，不需要新表或字段变更。

### 算法问题：是搬运，不是研发

这是一个关键判断。motion_vis 的 `phys_metrics.py`（743 行）已经是一个完整、可独立运行的物理指标计算模块。它有清晰的输入（NPZ 文件路径 + SMPL 模型路径）、清晰的输出（指标字典）、内置缓存机制、完善的 NaN/Inf 处理、以及分块计算防止显存溢出。

把它变成 MMDP 的 Pipeline，本质工作是**适配接口**，而不是**重写算法**。具体来说需要做的：

1. 把 `compute_phys_metrics()` 的调用包装成 `BasePipeline.execute()` 的签名（`input_dir, output_dir, input_files → List[Dict]`）
2. 把文件路径从本地绝对路径改为 Pipeline 的 `input_dir/sourceKey/filename` 约定
3. 把 SMPL 模型文件路径从硬编码改为环境变量或 Pipeline 参数
4. 把输出从返回 dict 改为写入 JSON 文件并上传 OSS

这些工作在之前做 `MOTION_PHYSICS_METRICS` V1 和 `BUILD_MOTION_VIEWER_DATA` 时已经走过两遍了，模式完全一致。算法逻辑本身——`load_motion_data()` → `compute_verts_joints()` → `_compute_metrics_from_verts_joints()`——一行都不需要改。

唯一需要额外处理的是 `BodyModel` 的依赖关系。它内部 `import` 了同目录下的 `lbs.py`（LBS 工具函数），从 motion_vis 搬到 mmdp-worker 时需要保持相对导入路径一致。这是纯工程问题，与算法无关。

### 镜像体积的权衡

torch 的 CPU 版本（`torch --index-url https://download.pytorch.org/whl/cpu`）约 200MB 压缩包，安装后约 800MB。加上 BodyModel 等其他依赖，Worker 镜像会增加约 1GB。这对于生产部署是需要考虑的，但并非不可接受——处理动作数据的 Worker 本来就不是轻量级服务，SMPL 模型文件本身就有几百 MB。

如果需要极致控制体积，可以考虑制作两个 Worker 镜像：一个基础镜像（不含 torch，跑图像/IMU Pipeline），一个完整镜像（含 torch，跑动作物理指标 Pipeline）。但当前阶段建议先统一用一个镜像——在 Worker 数量不多的情况下，维护两套镜像的复杂度远超 1GB 磁盘空间的成本。

## 剩下的工作按优先级排

按业务价值和实现难度综合排序：

### 第一优先：完整物理指标 + 动作标注

这两件事放在最前面，因为它们直接提升数据质量评估能力，而且技术路线已经验证。

完整物理指标就是上面详细分析的 `MOTION_PHYSICS_METRICS_V2` Pipeline。它产出的 `phys_err_mm`（综合物理误差，单位毫米）可以作为一个客观的质量评分，用在 Session 列表的排序过滤、质检规则触发、标注辅助等场景。

动作标注系统参考 MotionDB 的做法：每个动作文件可以有一个 JSON 标注记录，包含质量评级（A/B/C/D）、动作标签（走/跑/跳等）、问题标注（第几帧有什么问题）。在 MMDP 中，标注的主体是 DataAsset（一个 SMPL NPZ 文件就是一个 Asset），因此在 `data_asset` 表之外新增一个 `motion_annotation` 表（与 asset 一对一），是最自然的建模方式。MMDP 已有标注模块的路由（`/annotation`），标注数据和 UI 可以纳入这个体系。

前端的标注入口可以放在两个位置：一是 Session 详情页的 Asset 列表（批量标注、查看标注进度），二是 MotionViewer 内部（边看动作边标注，帧级精度）。后者更有价值，因为动作质量问题（如第 47 帧脚底滑动、第 120 帧关节跳变）只有在逐帧观看时才能发现。

### 第二优先：Session 详情页增强 + MotionViewer 导航

Session 详情页当前列出了所有 Asset，但缺少 MotionDB 式的分组浏览体验。需要做三件事：

首先，把 Asset 按 `sourceKey` 分组展示，每组显示文件数量和类型统计。这对应 MotionDB 中"子数据集 → 文件列表"的浏览模式。

其次，支持按物理指标排序。如果 Session 下有 `PHYSICS_REPORT` 或 `PHYSICS_REPORT_V2` 类型的 Asset，读取其中的 `phys_err_mm` 字段，让用户可以按"质量最好/最差"排序文件列表。这对数据筛选非常有价值——采集回来的 100 个动作里，先看质量最差的 10 个，快速定位问题。

第三，在 MotionViewer 内增加"上一个/下一个/随机"导航按钮。当前 MotionViewer 只能看到侧边栏列出同 Session 下所有动作并手动切换，加上快捷导航后，浏览效率大幅提升。

### 第三优先：多格式支持（SOMA、FBX、BVH）

SOMA 是 motion_vis 独有的高级能力——一个统一的骨架框架，兼容 30/77/78 关节变体，支持三种渲染模式（纯骨架/后端蒙皮/前端蒙皮）。整合 SOMA 的工作量较大，因为涉及新的模型资产部署、新的 Pipeline、前端新的渲染路径。但它对某些数据源（如 Kimodo 输出的 SOMA 格式动作）是不可替代的。

FBX 和 BVH 是动作捕捉领域的通用格式。FBX 的支持相对简单——Three.js 有现成的 `FBXLoader`，Worker 端只需要把 FBX 文件原样上传到 OSS，前端按需加载即可。BVH 稍微复杂一些，因为需要解析文本格式的骨架层级和运动数据，但 motion_vis 的 `utils_bvh.py` 已经实现了完整的解析逻辑，搬到 Worker Pipeline 即可。

### 第四优先（按需启动）：格式转换、对比模式

SOMA → FBX 转换（`soma2fbx.py`）、T2M NPZ 格式标准化（`_convert_t2m_npz.py`）、跨 Session 多路对比——这些功能在特定场景下很有价值，但不构成日常使用的主流程，按实际需求触发即可。

## 可行性总结

这个整合方案的核心信心来自一个事实：**最难的那一步已经走通了**。`BUILD_MOTION_VIEWER_DATA` Pipeline + `MotionViewer.vue` 的组合证明了 motion_vis 的能力可以被拆解、封装、注入 MMDP 的架构槽位，而且运行良好。后面的工作本质上是在重复这个模式——取一个源项目的能力，分析它的输入输出，设计一个 Pipeline 或一个前端组件或一个 API，把它接入对应的槽位。

技术栈的一致性进一步降低了风险。三个项目都是 Python（处理端）+ JavaScript/TypeScript（展示端），SMPL NPZ 是共同的数据格式，Three.js 是共同的 3D 引擎。没有跨语言桥接、没有格式转换损耗、没有架构理念冲突。

真正需要投入精力的不是技术难题，而是工程细节：Worker 镜像的依赖管理、MotionViewer 的代码拆分（当前 430 行塞在单文件里，加入 SOMA/FBX/BVH 后会失控）、标注系统的数据模型设计、以及 motion_vis CLAUDE.md 中记录的那 7 个 Three.js 动画陷阱（FBX 骨骼坐标系、暂停/恢复的正确姿势、全局偏移的实现方式等），每一项处理不当都会引入隐蔽的 bug。
