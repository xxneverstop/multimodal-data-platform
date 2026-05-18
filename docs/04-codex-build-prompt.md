---

## 04-codex-build-prompt.md

```md

# Codex 构建指令

请基于当前文档，为我搭建一个 Spring Boot 3 项目骨架，项目目标是实现实验室多模态数据基础设施平台的第一阶段 MVP。不要过度设计，不要引入复杂权限系统、工作流引擎、消息队列或完整前端。当前只需要完成后端接口和基础数据闭环。

## 技术要求

使用 Spring Boot 3、MyBatis-Plus、MySQL、MinIO SDK。项目包名使用：

com.honortech.dataplatform

请实现统一返回结果、统一异常处理、基础参数校验、数据库表结构 SQL、MinIO 配置类、任务管理接口、文件上传接口、基础质检服务、质检报告查询接口。

## 需要创建的核心模块

1. acquisition task 模块：负责采集任务创建、查询、状态更新。
2. data file 模块：负责文件元数据记录和 MinIO 上传。
3. qc report 模块：负责基础质检和报告保存。
4. common 模块：统一返回、异常处理、枚举、工具类。

## 需要创建的表

请创建以下表：

- acquisition_task
- data_file
- qc_report

字段按 docs/02-domain-model.md 实现即可，可以适当补充 created_at、updated_at、deleted 这类基础字段。

## 质检逻辑要求

第一阶段只做基础质检，不做复杂算法。上传文件后自动执行质检，质检项包括：

1. 文件是否为空
2. 文件大小是否合理
3. 扩展名是否支持
4. 如果是 csv/txt，尝试读取前几行
5. 如果是 csv，检查是否存在 timestamp 字段
6. 如果是 csv，检查是否存在 acc 或 gyro 相关字段
7. 输出结构化 JSON 报告

质检结果保存到 qc_report.report_json 字段中。

## 接口要求

请实现以下接口：

- POST /api/tasks
- GET /api/tasks
- GET /api/tasks/{taskId}
- POST /api/tasks/{taskId}/files
- GET /api/files/{fileId}
- GET /api/tasks/{taskId}/qc-report

## 实现风格

代码要简洁、清晰、适合后续扩展。不要写复杂架构，不要提前引入 RabbitMQ、Redis、Python worker、数据集版本管理等。当前重点是跑通闭环。请优先保证项目能启动、接口能测试、代码结构清楚、后续容易扩展。

## 交付要求

请输出：

1. 完整项目目录结构
2. 数据库 schema.sql
3. application.yml 示例配置
4. 核心 Entity、Mapper、Service、Controller
5. MinIO 上传工具类
6. 基础质检服务
7. 简单 README，说明如何启动、如何测试接口
