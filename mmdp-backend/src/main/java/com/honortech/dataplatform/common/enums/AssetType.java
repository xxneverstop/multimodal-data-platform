package com.honortech.dataplatform.common.enums;

import com.honortech.dataplatform.common.exception.BizException;

import java.util.Locale;

public enum AssetType {
    SESSION_ARCHIVE_ZIP,
    RGB_SEQ_RAW,
    RGB_VIDEO_MP4,
    MOCAP_CSV,
    SMPL_RESULT,
    SMPL_NPZ,
    PHYSICS_REPORT,
    PHYSICS_REPORT_V2,
    MOTION_VIEWER_JSON,
    MOTION_FBX,
    ALIGNED_RESULT,
    CAMERA_PARAM,
    LEFT_IMAGE_SEQUENCE,
    RIGHT_IMAGE_SEQUENCE,
    RAW_IMU_CSV,
    FRAME_TIMESTAMPS_CSV,
    DEPTH_RAW,
    POSE_CACHE,
    IMU_ALIGNED_CSV,
    ALIGNMENT_REPORT,
    QC_SUMMARY,

    // G1 机器人遥操作数据
    G1_ROBOT_HDF5,
    G1_CAMERA_SVO2,
    G1_MERGED_HDF5,
    G1_LEROBOT_PARQUET,

    OTHER;

    public static AssetType fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }
        try {
            return AssetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BizException("Unsupported assetType: " + value);
        }
    }
}
