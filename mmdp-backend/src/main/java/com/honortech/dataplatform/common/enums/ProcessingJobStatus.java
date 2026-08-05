package com.honortech.dataplatform.common.enums;

public enum ProcessingJobStatus {
    CREATED,
    CLAIMED,
    RUNNING,
    REGISTERED,
    SUCCESS,
    FAILED,
    /** 产物已被管理员清除，允许重新提交 */
    CLEANED
}
