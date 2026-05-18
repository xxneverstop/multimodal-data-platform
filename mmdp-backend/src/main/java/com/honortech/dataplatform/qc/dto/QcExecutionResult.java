package com.honortech.dataplatform.qc.dto;

import com.honortech.dataplatform.common.enums.QcStatus;

public record QcExecutionResult(QcStatus status, String summary, String reportJson, String detectedFormat) {
}
