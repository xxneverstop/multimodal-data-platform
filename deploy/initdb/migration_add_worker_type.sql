-- ============================================================================
-- GPU Worker 引入 — 数据库迁移
-- 新增 pipeline_definition.worker_type 列，标记 Pipeline 由 CPU 还是 GPU Worker 处理
-- 执行时机：部署 GPU Worker 之前
-- 安全说明：可重复执行（IF NOT EXISTS），不影响现有数据
-- ============================================================================

-- 1. 新增 worker_type 列
ALTER TABLE pipeline_definition
  ADD COLUMN IF NOT EXISTS worker_type VARCHAR(16) NOT NULL DEFAULT 'CPU'
  COMMENT 'Worker类型: CPU/GPU，决定由哪个 Worker 领取处理'
  AFTER executor_type;

-- 2. 标记 GPU Pipeline（需要 CUDA / ZED SDK 的 Pipeline）
UPDATE pipeline_definition SET worker_type = 'GPU'
WHERE pipeline_id IN ('MOTION_PHYSICS_METRICS_V2', 'G1_MERGE_CAMERA_ROBOT');

-- 3. 验证结果
SELECT pipeline_id, display_name, executor_type, worker_type, enabled
FROM pipeline_definition
ORDER BY worker_type, pipeline_id;
