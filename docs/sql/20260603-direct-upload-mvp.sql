ALTER TABLE data_file
    ADD COLUMN asset_type VARCHAR(64) NULL COMMENT '资产类型提示' AFTER sha256;
