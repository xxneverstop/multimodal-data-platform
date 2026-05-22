package com.honortech.dataplatform.common.enums;

import com.honortech.dataplatform.common.exception.BizException;

import java.util.Locale;

public enum AssetType {
    RGB_SEQ_RAW,
    RGB_VIDEO_MP4,
    MOCAP_CSV,
    SMPL_RESULT,
    ALIGNED_RESULT,
    CAMERA_PARAM,
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
