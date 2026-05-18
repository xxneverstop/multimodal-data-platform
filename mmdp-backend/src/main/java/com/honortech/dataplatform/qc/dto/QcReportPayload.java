package com.honortech.dataplatform.qc.dto;

import java.util.List;

public record QcReportPayload(
        QcFileInfo fileInfo,
        String detectedFormat,
        String summary,
        String overallStatus,
        List<QcCheckResult> checks,
        QcSampleData sample,
        List<String> warnings,
        List<String> errors
) {
}
