---

## 03-api-design.md

```md
# API 设计

## 1. 创建采集任务

POST /api/tasks

请求体：

```json
{
  "taskName": "太极云手采集任务001",
  "subjectCode": "S001",
  "actionName": "云手",
  "deviceType": "IMU_CLOTH",
  "modality": "IMU",
  "collectDate": "2026-05-14",
  "remark": "第一阶段测试数据"
}
```
