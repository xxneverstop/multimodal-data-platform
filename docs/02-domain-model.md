# 核心业务模型设计

## 1. acquisition_task 采集任务表

用于记录一次动作数据采集任务。

字段建议：

- id：主键
- task_name：任务名称
- subject_code：被试编号，例如 S001
- action_name：动作名称，例如 起势、云手、步行、下蹲
- device_type：设备类型，例如 IMU_CLOTH、IMU_GLOVE、GLASSES_IMU
- modality：数据模态，第一阶段默认 IMU
- collect_date：采集日期
- status：任务状态，CREATED、UPLOADED、QC_PASSED、QC_WARNING、QC_FAILED
- remark：备注
- created_at：创建时间
- updated_at：更新时间

## 2. data_file 数据文件表

用于记录上传文件的元数据，真实文件存储在 MinIO。

字段建议：

- id：主键
- task_id：关联采集任务 ID
- original_filename：原始文件名
- file_ext：文件扩展名
- content_type：文件 MIME 类型
- file_size：文件大小，单位 byte
- bucket_name：MinIO bucket
- object_key：MinIO 对象路径
- storage_url：内部访问路径或对象标识
- upload_status：上传状态，SUCCESS、FAILED
- created_at：创建时间

## 3. qc_report 质检报告表

用于记录每个文件的基础质检结果。

字段建议：

- id：主键
- task_id：采集任务 ID
- file_id：数据文件 ID
- qc_status：质检状态，PASSED、WARNING、FAILED
- summary：简要结论
- report_json：完整质检结果 JSON
- created_at：创建时间

## IMU 数据第一阶段假设

第一阶段不强制统一所有 IMU 文件格式，只做大致约定。优先支持 csv/json/txt 文件。对于 csv 类型，可以假设包含时间戳和若干传感器通道，例如：

```csv
timestamp,sensor_id,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z
0.000,imu_01,0.01,0.02,9.81,0.01,0.01,0.02
0.010,imu_01,0.02,0.01,9.80,0.01,0.02,0.01
