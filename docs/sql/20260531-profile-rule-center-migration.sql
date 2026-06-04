CREATE TABLE IF NOT EXISTS subject (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '被试主键ID',
    subject_code VARCHAR(64) NOT NULL COMMENT '平台被试编号',
    subject_name VARCHAR(128) NULL COMMENT '被试名称',
    gender VARCHAR(16) NULL COMMENT '性别',
    age INT NULL COMMENT '年龄',
    note VARCHAR(512) NULL COMMENT '备注',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE INDEX uk_subject_code (subject_code)
) COMMENT='被试主表';

CREATE TABLE IF NOT EXISTS collector_client (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '采集端主键ID',
    client_code VARCHAR(64) NOT NULL COMMENT '采集端编号',
    client_name VARCHAR(128) NOT NULL COMMENT '采集端名称',
    location VARCHAR(255) NULL COMMENT '采集端位置',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    remark VARCHAR(512) NULL COMMENT '备注',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE INDEX uk_collector_client_code (client_code)
) COMMENT='采集端主表';

CREATE TABLE IF NOT EXISTS collection_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '采集Profile主键ID',
    profile_code VARCHAR(64) NOT NULL COMMENT 'Profile编码',
    profile_name VARCHAR(128) NOT NULL COMMENT 'Profile名称',
    task_type_code VARCHAR(64) NOT NULL COMMENT '任务类型编码',
    modality_group_code VARCHAR(64) NOT NULL COMMENT '模态组合编码',
    device_group_code VARCHAR(64) NOT NULL COMMENT '设备组合编码',
    package_rule_code VARCHAR(64) NOT NULL COMMENT '打包规则编码',
    parser_rule_code VARCHAR(64) NOT NULL COMMENT '解析规则编码',
    archive_rule_code VARCHAR(64) NOT NULL COMMENT '归档规则编码',
    playback_rule_code VARCHAR(64) NOT NULL COMMENT '回放规则编码',
    version VARCHAR(32) NOT NULL DEFAULT 'v1' COMMENT '版本号',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark VARCHAR(512) NULL COMMENT '备注',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    UNIQUE INDEX uk_collection_profile_code (profile_code)
) COMMENT='采集Profile主表';

CREATE TABLE IF NOT EXISTS collection_profile_source (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Profile source主键ID',
    profile_id BIGINT NOT NULL COMMENT '关联Profile ID',
    source_key VARCHAR(64) NOT NULL COMMENT 'source键',
    source_name VARCHAR(128) NOT NULL COMMENT 'source名称',
    source_type VARCHAR(32) NOT NULL COMMENT 'source类型',
    device_role_code VARCHAR(64) NULL COMMENT '设备角色编码',
    required_flag TINYINT NOT NULL DEFAULT 1 COMMENT '是否必需',
    file_pattern VARCHAR(255) NULL COMMENT '文件匹配模式',
    parsed_asset_type VARCHAR(64) NOT NULL COMMENT '解析后资产类型',
    playback_kind VARCHAR(32) NULL COMMENT '回放类型',
    expected_fps DECIMAL(10, 2) NULL COMMENT '期望帧率',
    expected_sample_rate DECIMAL(10, 2) NULL COMMENT '期望采样率',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    CONSTRAINT fk_profile_source_profile FOREIGN KEY (profile_id) REFERENCES collection_profile(id),
    UNIQUE INDEX uk_profile_source_key (profile_id, source_key)
) COMMENT='采集Profile source定义表';

ALTER TABLE acquisition_task
    ADD COLUMN task_code VARCHAR(64) NULL COMMENT '平台任务编号' AFTER id,
    ADD COLUMN subject_id BIGINT NULL COMMENT '关联被试ID' AFTER task_name,
    ADD COLUMN subject_code_snapshot VARCHAR(64) NULL COMMENT '被试编号快照' AFTER subject_code,
    ADD COLUMN profile_id BIGINT NULL COMMENT '关联采集Profile ID' AFTER capture_location,
    ADD COLUMN task_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL' COMMENT '任务来源' AFTER profile_id,
    ADD COLUMN collector_client_id BIGINT NULL COMMENT '关联采集端ID' AFTER task_source;

ALTER TABLE acquisition_task
    ADD CONSTRAINT fk_task_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    ADD CONSTRAINT fk_task_profile FOREIGN KEY (profile_id) REFERENCES collection_profile(id),
    ADD CONSTRAINT fk_task_collector_client FOREIGN KEY (collector_client_id) REFERENCES collector_client(id);

ALTER TABLE acquisition_task
    ADD UNIQUE INDEX uk_task_code (task_code),
    ADD INDEX idx_task_subject_id (subject_id),
    ADD INDEX idx_task_profile_id (profile_id),
    ADD INDEX idx_task_collector_client_id (collector_client_id);

ALTER TABLE collection_session
    ADD COLUMN session_code VARCHAR(64) NULL COMMENT '平台Session编号' AFTER id,
    ADD COLUMN subject_id BIGINT NULL COMMENT '关联被试ID' AFTER task_id,
    ADD COLUMN local_task_id VARCHAR(64) NULL COMMENT '采集端本地任务ID' AFTER session_id,
    ADD COLUMN subject_code_snapshot VARCHAR(64) NULL COMMENT '被试编号快照' AFTER subject_code,
    ADD COLUMN action_name_snapshot VARCHAR(128) NULL COMMENT '动作名快照' AFTER action_name,
    ADD COLUMN profile_id BIGINT NULL COMMENT '关联采集Profile ID' AFTER action_name_snapshot,
    ADD COLUMN collector_client_id BIGINT NULL COMMENT '关联采集端ID' AFTER profile_id,
    ADD COLUMN timestamp_policy VARCHAR(64) NULL COMMENT '时间戳策略' AFTER duration_ms,
    ADD COLUMN session_status VARCHAR(32) NOT NULL DEFAULT 'IMPORTED' COMMENT 'Session状态' AFTER upload_status,
    ADD COLUMN updated_at DATETIME NULL COMMENT '更新时间' AFTER created_at;

ALTER TABLE collection_session
    ADD CONSTRAINT fk_session_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    ADD CONSTRAINT fk_session_profile FOREIGN KEY (profile_id) REFERENCES collection_profile(id),
    ADD CONSTRAINT fk_session_collector_client FOREIGN KEY (collector_client_id) REFERENCES collector_client(id);

ALTER TABLE collection_session
    ADD UNIQUE INDEX uk_session_code (session_code),
    ADD INDEX idx_session_profile_id (profile_id),
    ADD INDEX idx_session_collector_client_id (collector_client_id);

ALTER TABLE session_import_record
    ADD COLUMN collector_client_id BIGINT NULL COMMENT '关联采集端ID' AFTER session_record_id,
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT '请求追踪ID' AFTER source_endpoint;

ALTER TABLE session_import_record
    ADD CONSTRAINT fk_session_import_record_collector_client FOREIGN KEY (collector_client_id) REFERENCES collector_client(id);

ALTER TABLE session_import_record
    ADD INDEX idx_import_collector_client_id (collector_client_id);

ALTER TABLE data_file
    ADD COLUMN session_id BIGINT NULL COMMENT '关联Session记录ID' AFTER task_id,
    ADD COLUMN file_role VARCHAR(64) NULL COMMENT '文件角色' AFTER session_id,
    ADD COLUMN source_key VARCHAR(64) NULL COMMENT 'source键' AFTER file_role,
    ADD COLUMN relative_path VARCHAR(255) NULL COMMENT '包内相对路径' AFTER original_filename,
    ADD COLUMN sha256 VARCHAR(64) NULL COMMENT '文件sha256' AFTER file_size;

ALTER TABLE data_file
    ADD CONSTRAINT fk_data_file_session FOREIGN KEY (session_id) REFERENCES collection_session(id);

ALTER TABLE data_file
    ADD INDEX idx_data_file_session_id (session_id),
    ADD INDEX idx_data_file_source_key (source_key),
    ADD INDEX idx_data_file_sha256 (sha256);

ALTER TABLE data_asset
    ADD COLUMN session_id BIGINT NULL COMMENT '关联Session记录ID' AFTER task_id,
    ADD COLUMN source_key VARCHAR(64) NULL COMMENT 'source键' AFTER source_type;

ALTER TABLE data_asset
    ADD CONSTRAINT fk_data_asset_session FOREIGN KEY (session_id) REFERENCES collection_session(id);

ALTER TABLE data_asset
    ADD INDEX idx_data_asset_session_id (session_id),
    ADD INDEX idx_data_asset_source_key (source_key),
    ADD INDEX idx_data_asset_asset_type (asset_type);

ALTER TABLE qc_report
    ADD COLUMN session_id BIGINT NULL COMMENT '关联Session记录ID' AFTER task_id;

ALTER TABLE qc_report
    ADD CONSTRAINT fk_qc_report_session FOREIGN KEY (session_id) REFERENCES collection_session(id);

ALTER TABLE qc_report
    ADD INDEX idx_qc_report_session_id (session_id);

ALTER TABLE processing_job
    ADD COLUMN session_id BIGINT NULL COMMENT '关联Session记录ID' AFTER task_id;

ALTER TABLE processing_job
    ADD CONSTRAINT fk_processing_job_session FOREIGN KEY (session_id) REFERENCES collection_session(id);

ALTER TABLE processing_job
    ADD INDEX idx_processing_job_session_id (session_id);

ALTER TABLE asset_lineage
    ADD COLUMN session_id BIGINT NULL COMMENT '关联Session记录ID' AFTER task_id;

ALTER TABLE asset_lineage
    ADD CONSTRAINT fk_asset_lineage_session FOREIGN KEY (session_id) REFERENCES collection_session(id);

ALTER TABLE asset_lineage
    ADD INDEX idx_asset_lineage_session_id (session_id);

UPDATE acquisition_task
SET task_code = CONCAT('TASK-', id)
WHERE task_code IS NULL;

UPDATE acquisition_task
SET subject_code_snapshot = subject_code
WHERE subject_code_snapshot IS NULL;

UPDATE collection_session
SET session_code = CONCAT('SES-', id)
WHERE session_code IS NULL;

UPDATE collection_session
SET subject_code_snapshot = subject_code
WHERE subject_code_snapshot IS NULL;

UPDATE collection_session
SET action_name_snapshot = action_name
WHERE action_name_snapshot IS NULL;

UPDATE data_file df
JOIN collection_session cs
  ON df.object_key LIKE CONCAT('%/sessions/', cs.session_id, '/%')
SET df.session_id = cs.id
WHERE df.session_id IS NULL;

UPDATE data_asset da
JOIN data_file df ON da.file_id = df.id
SET da.session_id = df.session_id
WHERE da.session_id IS NULL;
