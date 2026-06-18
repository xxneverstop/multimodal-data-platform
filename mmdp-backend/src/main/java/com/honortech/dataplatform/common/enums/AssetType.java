package com.honortech.dataplatform.common.enums;

import com.honortech.dataplatform.common.exception.BizException;

import java.util.Locale;

public enum AssetType {
    SESSION_ARCHIVE_ZIP,
    RGB_SEQ_RAW,
    RGB_VIDEO_MP4,
    MOCAP_CSV,
    SMPL_RESULT,
    ALIGNED_RESULT,
    CAMERA_PARAM,
    LEFT_IMAGE_SEQUENCE,
    RIGHT_IMAGE_SEQUENCE,
    RAW_IMU_CSV,
    FRAME_TIMESTAMPS_CSV,
    DEPTH_RAW,
    POSE_CACHE,
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
