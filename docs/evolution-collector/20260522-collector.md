# 数据采集侧 Web App 方案 Overview

本方案定位为多模态数据平台的“采集侧工作台（Collector）”，目标不是替代实验室现有算法系统，而是统一多设备采集入口，降低采集复杂度，并将采集结果天然纳入后续数据平台管理链路。系统核心目标是实现：一个任务、一个面板、统一时间基准、统一数据登记。

整体架构采用：

```text
本地采集 Agent + 浏览器 Web 工作台
```

而不是纯网页直接连接硬件设备。原因在于浏览器对蓝牙、串口、USB、专业设备 SDK 的访问能力有限，稳定性与兼容性不足，难以支撑实验室级长期数据采集。因此，本方案将“设备接入能力”下沉到本地 Agent，由浏览器负责任务管理与实时可视化。

整体链路如下：

```text
Video / HMD / IMU 设备
            ↓
      Local Collector Agent
            ↓
   WebSocket / HTTP
            ↓
      Browser Web Panel
            ↓
      数据平台（管理侧）
```

采集端界面围绕“采集任务”组织。用户创建或选择采集任务后，可在统一 Web 面板中同时查看三类数据状态：

```text
上：Video 视频采集数据
中：HMD 头显数据
下：IMU Sensor 数据
```

每类数据均显示实时状态，包括：

- 当前连接状态

- 采样频率 / FPS

- 最新时间戳

- 数据量

- 丢包或异常状态

- 实时预览（如视频帧）

系统的核心设计目标并非“网页展示”，而是：

```text
统一时间基准（Unified Timestamp）
```

所有设备数据在进入本地 Agent 时统一记录：

```text
hostReceiveTimestamp
```

从而避免不同设备内部时间体系不一致的问题，为后续：

- 多模态时间对齐

- 动作同步分析

- 数据融合

- SMPL / IMU / 视频联合处理

- QC 检查

- 数据血缘追踪

提供统一基础。

本地 Agent 是采集侧的核心组件，负责：

- 设备发现与连接

- 多设备并发采集

- 实时数据转发

- 本地文件落盘

- Session Manifest 生成

- 与平台 API 对接

推荐 MVP 技术栈如下：

```text
Frontend:
Vue3 + TypeScript + WebSocket

Local Agent:
Python FastAPI + Uvicorn

Video:
OpenCV / ffmpeg

IMU:
pyserial / bleak / SDK

HMD:
OpenXR / UDP / SDK / adb

Realtime:
WebSocket

Storage:
本地文件 + JSON Manifest

Platform:
Spring Boot + MySQL + MinIO
```

采集过程中，系统优先本地落盘，而非边采边上传。原因是视频与多模态数据量较大，实时上传会影响采集稳定性。因此流程设计为：

```text
采集 → 本地 Session 文件 → 采集结束 → 上传 / 登记平台
```

每次采集结束后，系统自动生成：

```text
session_manifest.json
```

用于描述：

- 当前采集任务

- 参与设备

- 数据文件路径

- 采样率

- 时间范围

- 数据来源

后续平台管理侧将基于该 Manifest 自动生成：

- acquisition session

- asset

- derived asset

- processing lineage

- QC report

从而实现：

```text
采集 → 数据资产化 → 处理 → 血缘追踪
```

的一体化链路。

本方案最终目标不是单纯做一个“采集网页”，而是建设实验室长期可复用的：

```text
多模态数据采集基础设施
```

使实验室后续无论接入：

- RGB 视频

- HMD

- IMU

- 动捕服

- 足底压力

- 双目眼镜

- EEG / fNIRS

- 机器人状态数据

都可以统一纳入同一采集任务体系与数据管理体系中。
