package com.honortech.dataplatform.qc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.qc.dto.QcReportResponse;
import com.honortech.dataplatform.qc.entity.QcReport;
import com.honortech.dataplatform.qc.mapper.QcReportMapper;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
public class QcReportServiceImpl implements QcReportService {

    private final QcReportMapper qcReportMapper;
    private final AcquisitionTaskService acquisitionTaskService;
    private final ObjectMapper objectMapper;

    public QcReportServiceImpl(
            QcReportMapper qcReportMapper,
            AcquisitionTaskService acquisitionTaskService,
            ObjectMapper objectMapper) {
        this.qcReportMapper = qcReportMapper;
        this.acquisitionTaskService = acquisitionTaskService;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<QcReportResponse> listReportsByTaskId(Long taskId) {
        acquisitionTaskService.getTask(taskId);
        return qcReportMapper.selectList(
                        new LambdaQueryWrapper<QcReport>()
                                .eq(QcReport::getTaskId, taskId)
                                .orderByDesc(QcReport::getCreatedAt))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private QcReportResponse toResponse(QcReport report) {
        return new QcReportResponse(
                report.getId(),
                report.getTaskId(),
                report.getFileId(),
                report.getQcStatus(),
                report.getSummary(),
                parseJson(report.getReportJson()),
                report.getCreatedAt()
        );
    }

    private JsonNode parseJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (IOException exception) {
            throw new BizException("Failed to parse stored QC report JSON", exception);
        }
    }
}
