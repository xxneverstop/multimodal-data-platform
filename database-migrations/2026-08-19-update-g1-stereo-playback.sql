-- ============================================
-- 迁移: 更新 G1 双目播放 Pipeline 元数据
-- 日期: 2026-08-19
-- 说明: 仅更新展示信息；输入/输出 AssetType 与 Pipeline ID 均保持不变
-- 警告: 请先在测试环境验证，生产环境需按发布流程人工执行
-- ============================================

UPDATE `pipeline_definition`
SET `display_name` = 'G1 双目可视化播放资产生成',
    `description` = '从合并后的 G1 HDF5 中提取左右眼图像，生成同步的双目 MP4',
    `updated_at` = NOW()
WHERE `pipeline_id` = 'G1_GENERATE_PLAYBACK';

-- 验证：应返回一行，且输入/输出类型仍分别为 G1_MERGED_HDF5、RGB_VIDEO_MP4。
SELECT
    `pipeline_id`,
    `display_name`,
    `description`,
    `input_asset_types`,
    `output_asset_types`,
    `enabled`,
    `updated_at`
FROM `pipeline_definition`
WHERE `pipeline_id` = 'G1_GENERATE_PLAYBACK';

-- 回滚（需要时手动执行）：
-- UPDATE `pipeline_definition`
-- SET `display_name` = 'G1可视化播放资产生成',
--     `description` = '从合并后的G1 HDF5中提取左相机图像，生成可在线播放的MP4',
--     `updated_at` = NOW()
-- WHERE `pipeline_id` = 'G1_GENERATE_PLAYBACK';
