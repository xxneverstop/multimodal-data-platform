-- Execution Graph / DAG 升级 migration
-- 为 processing_job 新增 depends_on_job_ids 字段，支持 Job 之间的显式依赖

ALTER TABLE processing_job
    ADD COLUMN IF NOT EXISTS depends_on_job_ids VARCHAR(512) NULL
    COMMENT '依赖的前置Job ID列表，JSON数组如[1,2]。为空表示无前置依赖，可直接执行';

-- 创建复合索引，加速按 task + status 的查询
CREATE INDEX IF NOT EXISTS idx_processing_job_task_status
    ON processing_job (task_id, status);

-- 创建复合索引，加速按 session + status 的查询
CREATE INDEX IF NOT EXISTS idx_processing_job_session_status
    ON processing_job (session_id, status);
