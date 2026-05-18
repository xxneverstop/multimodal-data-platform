CREATE TABLE IF NOT EXISTS acquisition_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_name VARCHAR(128) NOT NULL,
    subject_code VARCHAR(64) NOT NULL,
    action_name VARCHAR(128) NOT NULL,
    device_type VARCHAR(64) NOT NULL,
    modality VARCHAR(32) NOT NULL,
    collect_date DATE NOT NULL,
    scene VARCHAR(128) NULL,
    operator_name VARCHAR(128) NULL,
    capture_location VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    remark VARCHAR(512) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS data_file (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_ext VARCHAR(32) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    bucket_name VARCHAR(128) NOT NULL,
    object_key VARCHAR(255) NOT NULL,
    storage_url VARCHAR(512) NOT NULL,
    upload_status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_data_file_task FOREIGN KEY (task_id) REFERENCES acquisition_task(id)
);

CREATE TABLE IF NOT EXISTS qc_report (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    qc_status VARCHAR(32) NOT NULL,
    summary VARCHAR(512) NOT NULL,
    report_json TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_qc_report_task FOREIGN KEY (task_id) REFERENCES acquisition_task(id),
    CONSTRAINT fk_qc_report_file FOREIGN KEY (file_id) REFERENCES data_file(id)
);

CREATE TABLE IF NOT EXISTS data_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    asset_type VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    file_id BIGINT NULL,
    external_path VARCHAR(512) NULL,
    file_format VARCHAR(64) NULL,
    size_remark VARCHAR(128) NULL,
    description VARCHAR(512) NULL,
    operator_remark VARCHAR(512) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_data_asset_task FOREIGN KEY (task_id) REFERENCES acquisition_task(id),
    CONSTRAINT fk_data_asset_file FOREIGN KEY (file_id) REFERENCES data_file(id)
);

CREATE TABLE IF NOT EXISTS processing_job (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    pipeline_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    parameters_json TEXT NULL,
    result_json TEXT NULL,
    error_message VARCHAR(512) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT fk_processing_job_task FOREIGN KEY (task_id) REFERENCES acquisition_task(id)
);

-- Incremental migration SQL for existing databases:
-- ALTER TABLE acquisition_task ADD COLUMN scene VARCHAR(128) NULL;
-- ALTER TABLE acquisition_task ADD COLUMN operator_name VARCHAR(128) NULL;
-- ALTER TABLE acquisition_task ADD COLUMN capture_location VARCHAR(255) NULL;
-- CREATE TABLE data_asset (
--     id BIGINT PRIMARY KEY AUTO_INCREMENT,
--     task_id BIGINT NOT NULL,
--     source_type VARCHAR(32) NOT NULL,
--     asset_type VARCHAR(64) NOT NULL,
--     display_name VARCHAR(255) NOT NULL,
--     file_id BIGINT NULL,
--     external_path VARCHAR(512) NULL,
--     file_format VARCHAR(64) NULL,
--     size_remark VARCHAR(128) NULL,
--     description VARCHAR(512) NULL,
--     operator_remark VARCHAR(512) NULL,
--     created_at DATETIME NOT NULL,
--     CONSTRAINT fk_data_asset_task FOREIGN KEY (task_id) REFERENCES acquisition_task(id),
--     CONSTRAINT fk_data_asset_file FOREIGN KEY (file_id) REFERENCES data_file(id)
-- );
-- CREATE TABLE processing_job (
--     id BIGINT PRIMARY KEY AUTO_INCREMENT,
--     task_id BIGINT NOT NULL,
--     pipeline_id VARCHAR(64) NOT NULL,
--     status VARCHAR(32) NOT NULL,
--     parameters_json TEXT NULL,
--     result_json TEXT NULL,
--     error_message VARCHAR(512) NULL,
--     created_at DATETIME NOT NULL,
--     updated_at DATETIME NOT NULL,
--     CONSTRAINT fk_processing_job_task FOREIGN KEY (task_id) REFERENCES acquisition_task(id)
-- );
