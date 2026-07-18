-- ============================================
-- G1 机器人遥操作数据 Profile + Pipeline 配置
-- 执行环境: mmdp 数据库
-- 用法: 在 Navicat 中打开此文件，选中全部 SQL 执行
-- ============================================

-- ============================================
-- 清理旧数据（如已存在则先删除，避免重复插入报错）
-- ============================================
DELETE FROM profile_pipeline WHERE pipeline_id IN ('G1_MERGE_CAMERA_ROBOT', 'G1_CONVERT_TO_LEROBOT');
DELETE FROM profile_pipeline WHERE profile_id = (SELECT id FROM (SELECT id FROM collection_profile WHERE profile_code = 'G1_TELEOP_V1') AS tmp);
DELETE FROM pipeline_definition WHERE pipeline_id IN ('G1_MERGE_CAMERA_ROBOT', 'G1_CONVERT_TO_LEROBOT');
DELETE FROM collection_profile_source WHERE profile_id = (SELECT id FROM (SELECT id FROM collection_profile WHERE profile_code = 'G1_TELEOP_V1') AS tmp);
DELETE FROM collection_profile WHERE profile_code = 'G1_TELEOP_V1';

-- ============================================
-- 1. 新增 Pipeline 定义
-- ============================================
INSERT INTO pipeline_definition (pipeline_id, display_name, description, input_asset_types, output_asset_types, executor_type, enabled, created_at, updated_at)
VALUES
('G1_MERGE_CAMERA_ROBOT', 'G1相机-机器人数据合并',
 '将ZED双目相机SVO2数据与机器人HDF5遥操作数据合并：时间戳对齐、图像JPEG压缩、计算delta EEF/delta Height',
 '["G1_ROBOT_HDF5", "G1_CAMERA_SVO2"]', '["G1_MERGED_HDF5"]',
 'PYTHON_WORKER', 1, NOW(), NOW()),

('G1_CONVERT_TO_LEROBOT', 'G1数据→LeRobot训练格式',
 '将合并后的G1 HDF5数据转换为LeRobot VLA训练格式（Parquet+Video），支持批量流式转换',
 '["G1_MERGED_HDF5"]', '["G1_LEROBOT_PARQUET"]',
 'PYTHON_WORKER', 1, NOW(), NOW());

-- ============================================
-- 2. 新增 G1 遥操作采集 Profile
-- ============================================
INSERT INTO collection_profile (
    profile_code, profile_name, task_type_code, modality_group_code, device_group_code,
    package_rule_code, parser_rule_code, archive_rule_code, playback_rule_code,
    version, enabled, remark, created_at, updated_at
) VALUES (
    'G1_TELEOP_V1', 'G1遥操作采集', 'G1_TELEOP', 'ROBOT_CAMERA', 'G1_ZED',
    'G1_TELEOP_V1', 'G1_TELEOP_V1', 'G1_TELEOP_V1', 'G1_TELEOP_V1',
    'v1', 1, '宇树G1机器人遥操作数据采集：robot HDF5 + ZED SVO2', NOW(), NOW()
);

-- ============================================
-- 3. Profile Source 定义（两个数据源）
-- ============================================
-- 获取刚插入的 profile_id
SET @g1_profile_id = (SELECT id FROM collection_profile WHERE profile_code = 'G1_TELEOP_V1');

INSERT INTO collection_profile_source (
    profile_id, source_key, source_name, source_type, device_role_code,
    required_flag, file_pattern, parsed_asset_type, playback_kind,
    expected_fps, expected_sample_rate, sort_order, enabled, created_at, updated_at
) VALUES
(@g1_profile_id, 'robot_hdf5', '机器人遥操作数据', 'file', 'ROBOT_MAIN',
 1, '%episode_%.hdf5', 'G1_ROBOT_HDF5', NULL,
 NULL, NULL, 1, 1, NOW(), NOW()),

(@g1_profile_id, 'camera_svo2', 'ZED双目相机录制', 'file', 'CAM_ZED',
 1, '%episode_%.svo2', 'G1_CAMERA_SVO2', NULL,
 NULL, NULL, 2, 1, NOW(), NOW());

-- ============================================
-- 4. Profile-Pipeline 关联
-- ============================================
INSERT INTO profile_pipeline (profile_id, pipeline_id, enabled, created_at)
VALUES
(@g1_profile_id, 'G1_MERGE_CAMERA_ROBOT', 1, NOW()),
(@g1_profile_id, 'G1_CONVERT_TO_LEROBOT', 1, NOW());

-- ============================================
-- 验证：检查插入结果
-- ============================================
SELECT '=== Pipeline Definitions ===' AS '';
SELECT pipeline_id, display_name, enabled FROM pipeline_definition WHERE pipeline_id LIKE 'G1_%';

SELECT '=== Collection Profile ===' AS '';
SELECT id, profile_code, profile_name, enabled FROM collection_profile WHERE profile_code = 'G1_TELEOP_V1';

SELECT '=== Profile Sources ===' AS '';
SELECT ps.source_key, ps.source_name, ps.parsed_asset_type, ps.file_pattern
FROM collection_profile_source ps
JOIN collection_profile p ON ps.profile_id = p.id
WHERE p.profile_code = 'G1_TELEOP_V1';

SELECT '=== Profile-Pipeline Links ===' AS '';
SELECT p.profile_code, pp.pipeline_id, pp.enabled
FROM profile_pipeline pp
JOIN collection_profile p ON pp.profile_id = p.id
WHERE p.profile_code = 'G1_TELEOP_V1';
