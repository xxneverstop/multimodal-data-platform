-- ============================================
-- 迁移: 注册 G1 可视化播放 Pipeline
-- 日期: 2026-08-17
-- 描述: 新增 G1_GENERATE_PLAYBACK，并关联 G1_TELEOP_V1 Profile
-- ============================================

INSERT INTO `pipeline_definition` (
    `pipeline_id`, `display_name`, `description`,
    `input_asset_types`, `output_asset_types`,
    `executor_type`, `enabled`, `created_at`, `updated_at`
) VALUES (
    'G1_GENERATE_PLAYBACK',
    'G1可视化播放资产生成',
    '从合并后的G1 HDF5中提取左相机图像，生成可在线播放的MP4',
    '["G1_MERGED_HDF5"]',
    '["RGB_VIDEO_MP4"]',
    'PYTHON_WORKER',
    1,
    NOW(),
    NOW()
) ON DUPLICATE KEY UPDATE
    `display_name` = 'G1可视化播放资产生成',
    `description` = '从合并后的G1 HDF5中提取左相机图像，生成可在线播放的MP4',
    `input_asset_types` = '["G1_MERGED_HDF5"]',
    `output_asset_types` = '["RGB_VIDEO_MP4"]',
    `executor_type` = 'PYTHON_WORKER',
    `enabled` = 1,
    `updated_at` = NOW();

INSERT INTO `profile_pipeline` (
    `profile_id`, `pipeline_id`, `enabled`, `created_at`
)
SELECT `id`, 'G1_GENERATE_PLAYBACK', 1, NOW()
FROM `collection_profile`
WHERE `profile_code` = 'G1_TELEOP_V1'
ON DUPLICATE KEY UPDATE `enabled` = 1;

SELECT `pipeline_id`, `display_name`, `enabled`
FROM `pipeline_definition`
WHERE `pipeline_id` = 'G1_GENERATE_PLAYBACK';

-- 回滚（需要时手动执行）:
-- DELETE FROM `profile_pipeline` WHERE `pipeline_id` = 'G1_GENERATE_PLAYBACK';
-- DELETE FROM `pipeline_definition` WHERE `pipeline_id` = 'G1_GENERATE_PLAYBACK';
