package com.honortech.dataplatform.qc.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.qc.dto.QcReportResponse;
import com.honortech.dataplatform.qc.service.QcReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks/{taskId}/qc-report")
public class QcReportController {

    private final QcReportService qcReportService;

    public QcReportController(QcReportService qcReportService) {
        this.qcReportService = qcReportService;
    }

    @GetMapping
    public ApiResponse<List<QcReportResponse>> listReports(@PathVariable Long taskId) {
        return ApiResponse.success(qcReportService.listReportsByTaskId(taskId));
    }
}
