INSERT INTO collection_profile (
    profile_code,
    profile_name,
    task_type_code,
    modality_group_code,
    device_group_code,
    package_rule_code,
    parser_rule_code,
    archive_rule_code,
    playback_rule_code,
    version,
    enabled,
    remark,
    created_at,
    updated_at
)
SELECT
    'ZED_STEREO_IMU_V1',
    'ZED Stereo + IMU Raw Import',
    'HUMAN_DEMO',
    'ZED_IMU_RAW',
    'ZED_IMU',
    'SESSION_ZIP_V1',
    'SESSION_JSONL_VIDEO_IMU_V1',
    'SESSION_ARCHIVE_V1',
    'MULTI_VIDEO_IMU_V1',
    'v1',
    1,
    '按 zed / imu 两类 source 导入原始数据，先保证 Session 入库和资产化跑通',
    NOW(),
    NOW()
WHERE NOT EXISTS (
    SELECT 1
    FROM collection_profile
    WHERE profile_code = 'ZED_STEREO_IMU_V1'
);

INSERT INTO collection_profile_source (
    profile_id,
    source_key,
    source_name,
    source_type,
    device_role_code,
    required_flag,
    file_pattern,
    parsed_asset_type,
    playback_kind,
    expected_fps,
    expected_sample_rate,
    sort_order,
    enabled,
    created_at,
    updated_at
)
SELECT
    p.id,
    s.source_key,
    s.source_name,
    s.source_type,
    s.device_role_code,
    s.required_flag,
    s.file_pattern,
    s.parsed_asset_type,
    s.playback_kind,
    s.expected_fps,
    s.expected_sample_rate,
    s.sort_order,
    1,
    NOW(),
    NOW()
FROM collection_profile p
JOIN (
    SELECT 'zed' AS source_key, 'ZED Raw Capture' AS source_name, 'zed_mcap' AS source_type, 'ZED' AS device_role_code, 1 AS required_flag, '%zed%' AS file_pattern, 'OTHER' AS parsed_asset_type, NULL AS playback_kind, NULL AS expected_fps, NULL AS expected_sample_rate, 1 AS sort_order
    UNION ALL
    SELECT 'imu', 'IMU / Pose CSV' AS source_name, 'pose_csv' AS source_type, 'IMU' AS device_role_code, 1, '%position%', 'OTHER', NULL, NULL, NULL, 2
) s
WHERE p.profile_code = 'ZED_STEREO_IMU_V1'
  AND NOT EXISTS (
      SELECT 1
      FROM collection_profile_source cps
      WHERE cps.profile_id = p.id
        AND cps.source_key = s.source_key
  );
