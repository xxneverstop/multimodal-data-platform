-- ============================================================
-- 迁移脚本：添加动作标注系统
-- 数据库：multimodal-data-platform
-- 日期：2026-07-10
-- 执行方式：在 Navicat 中打开此文件，选中全部 SQL 执行
-- ============================================================

CREATE TABLE IF NOT EXISTS motion_annotation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '标注记录主键ID',
    asset_id BIGINT NOT NULL COMMENT '关联数据资产ID',
    status VARCHAR(32) NOT NULL DEFAULT 'UNANNOTATED' COMMENT '标注状态: UNANNOTATED/IN_PROGRESS/ANNOTATED',
    quality_rating VARCHAR(8) NULL COMMENT '质量评级: A/B/C/D',
    motion_tags JSON NULL COMMENT '动作标签JSON数组',
    frame_issues JSON NULL COMMENT '帧级问题标注JSON数组',
    overall_comment VARCHAR(1024) NULL COMMENT '综合评语',
    annotator_id BIGINT NULL COMMENT '标注人用户ID',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    CONSTRAINT fk_annotation_asset FOREIGN KEY (asset_id) REFERENCES data_asset(id) ON DELETE CASCADE,
    CONSTRAINT fk_annotation_annotator FOREIGN KEY (annotator_id) REFERENCES sys_user(id),
    UNIQUE INDEX uk_annotation_asset_id (asset_id),
    INDEX idx_annotation_status (status),
    INDEX idx_annotation_quality_rating (quality_rating)
) COMMENT='动作标注表,与data_asset一对一';
