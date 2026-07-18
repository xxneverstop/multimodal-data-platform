-- ============================================================
-- 迁移脚本：motion_annotation 表扩展
-- 新增 MotionDB 风格缺陷字段 + 文本描述字段
-- 数据库：multimodal-data-platform
-- 日期：2026-07-10
-- 执行方式：在 Navicat 中打开此文件，逐条执行
-- 前提：motion_annotation 表已存在（来自 migration_add_motion_annotation.sql）
-- ============================================================

-- 1. 新增 MotionDB 风格缺陷评估列
ALTER TABLE motion_annotation
    ADD COLUMN motiondb_defects JSON NULL
    COMMENT 'MotionDB风格缺陷评估: {testSet,flatScene,jointJump,jointDeformity,sliding,floatPenetrate,displacementMissing,temporalConsistency}';

-- 2. 新增文本描述列
ALTER TABLE motion_annotation
    ADD COLUMN text_descriptions JSON NULL
    COMMENT '多条文本描述JSON数组';
