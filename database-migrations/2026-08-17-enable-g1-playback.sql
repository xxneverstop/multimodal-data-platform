-- ============================================
-- 迁移: 启用 G1 Session 视频回放
-- 日期: 2026-08-17
-- 描述: 为 G1_TELEOP_V1 绑定播放规则，并将 camera_svo2 配置为视频播放源
-- ============================================

UPDATE `collection_profile`
SET `playback_rule_code` = 'G1_TELEOP_V1',
    `updated_at` = NOW()
WHERE `profile_code` = 'G1_TELEOP_V1';

UPDATE `collection_profile_source` AS `source`
JOIN `collection_profile` AS `profile` ON `profile`.`id` = `source`.`profile_id`
SET `source`.`playback_kind` = 'video',
    `source`.`expected_fps` = 20,
    `source`.`updated_at` = NOW()
WHERE `profile`.`profile_code` = 'G1_TELEOP_V1'
  AND `source`.`source_key` = 'camera_svo2';

SELECT
    `profile`.`profile_code`,
    `profile`.`playback_rule_code`,
    `source`.`source_key`,
    `source`.`playback_kind`,
    `source`.`expected_fps`
FROM `collection_profile` AS `profile`
JOIN `collection_profile_source` AS `source` ON `source`.`profile_id` = `profile`.`id`
WHERE `profile`.`profile_code` = 'G1_TELEOP_V1'
  AND `source`.`source_key` = 'camera_svo2';

-- 回滚（需要时手动执行）：
-- UPDATE `collection_profile_source` AS `source`
-- JOIN `collection_profile` AS `profile` ON `profile`.`id` = `source`.`profile_id`
-- SET `source`.`playback_kind` = NULL, `source`.`expected_fps` = NULL, `source`.`updated_at` = NOW()
-- WHERE `profile`.`profile_code` = 'G1_TELEOP_V1' AND `source`.`source_key` = 'camera_svo2';
