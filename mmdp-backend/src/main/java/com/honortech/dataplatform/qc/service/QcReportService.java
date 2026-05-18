package com.honortech.dataplatform.qc.service;

import com.honortech.dataplatform.qc.dto.QcReportResponse;

import java.util.List;

public interface QcReportService {

    List<QcReportResponse> listReportsByTaskId(Long taskId);
}
