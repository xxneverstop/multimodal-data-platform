# MMDP 文档索引

多模态数据平台（MMDP）文档导航。按主题分类，方便快速定位。

---

## 架构设计

| 文档 | 说明 |
|------|------|
| [overview](architecture/overview.md) | 整体架构总览：项目结构、三层架构、数据库设计、部署拓扑、端到端数据流 |
| [system-flow](architecture/system-flow.md) | 数据采集→数据平台流程说明，以双目+IMU 为例描述端到端数据流 |
| [worker](architecture/worker.md) | Python Worker 架构：轮询-领取-执行-上报循环、Pipeline 自动发现机制 |

## 部署运维

| 文档 | 说明 |
|------|------|
| [deploy](deployment/deploy.md) | 项目部署文档（v0.4.0）：Docker Compose 部署、Nginx 反向代理、Worker 容器 |
| [aliyun-handover](deployment/aliyun-handover.md) | 阿里云资源交接：账号、权限、OSS 存储体系、部署流程 |

## 规范协议

| 文档 | 说明 |
|------|------|
| [session-directory-v1](specs/session-directory-v1.md) | 标准 Session 目录结构规范 v1 |
| [ZED 上传包说明](specs/zed-imu/upload-package.md) | ZED + IMU 数据上传包结构与字段说明 |
| [ZED Session 导入](specs/zed-imu/session-import.md) | ZED IMU Session 导入流程 |
| [ZED 上传检查清单](specs/zed-imu/checklist.md) | ZED IMU 上传前自检项 |

## 处理链路

| 文档 | 说明 |
|------|------|
| [profile](processing/profile.md) | Profile 机制说明：manifest.json 字段、sourceKey 映射、playback 规则 |
| [manual-lineage](processing/manual-lineage.md) | 手动处理登记与数据血缘链路 |
| [motion-integration-plan](processing/motion-integration-plan.md) | motion_vis + MotionDB → MMDP 3D 动作能力整合方案 |
| [motion-v2-verify-deploy](processing/motion-v2-verify-deploy.md) | MOTION_PHYSICS_METRICS_V2 Pipeline 验证与部署指南 |

## 阶段总结

| 文档 | 说明 |
|------|------|
| [2026-06-24 OKR](okr/20260624.md) | 2026-06-24 工作阶段总结：Profile CRUD、Pipeline 全链路、Worker 闭环、播放页重构等 |

## 迁移脚本

| 文档 | 说明 |
|------|------|
| [profile-rule-center](sql/20260531-profile-rule-center-migration.sql) | Profile 规则中心迁移 |
| [direct-upload-mvp](sql/20260603-direct-upload-mvp.sql) | OSS 直传 MVP |
| [zed-stereo-imu](sql/20260611-zed-stereo-imu-profile.sql) | ZED 双目+IMU Profile 配置 |

---

## 其他入口

- 项目根目录 [README.md](../README.md)：项目概述与快速启动
- [CLAUDE.md](../CLAUDE.md)：AI 编码助手指引
- [AGENTS.md](../AGENTS.md)：AI 代理工作指引
- 后端 [README.md](../mmdp-backend/README.md)：后端 API 与配置详解
