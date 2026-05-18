package com.honortech.dataplatform.qc.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.QcStatus;
import com.honortech.dataplatform.qc.dto.QcExecutionResult;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QcInspectionServiceImplTest {

    private final QcInspectionServiceImpl service = new QcInspectionServiceImpl(new ObjectMapper());

    @Test
    void shouldPassTxtTimeseriesSample() {
        String sample = """
                2026-04-24-11:31:02.549853,2.86328125,2.734375,9.109375
                2026-04-24-11:31:02.550866,2.86328125,2.734375,9.109375
                2026-04-24-11:31:02.553864,2.86328125,2.734375,9.109375
                """;
        QcExecutionResult result = service.inspect("sensor_data.txt", "txt", "text/plain", sample.getBytes(StandardCharsets.UTF_8), AssetType.OTHER);
        assertEquals(QcStatus.PASSED, result.status());
        assertTrue(result.reportJson().contains("txt-timeseries"));
    }

    @Test
    void shouldWarnCsvWithoutTimestampHeader() {
        String sample = """
                sensor_id,acc_x,acc_y,gyro_x
                imu_1,0.1,0.2,0.3
                """;
        QcExecutionResult result = service.inspect("imu.csv", "csv", "text/csv", sample.getBytes(StandardCharsets.UTF_8), AssetType.OTHER);
        assertEquals(QcStatus.WARNING, result.status());
        assertTrue(result.summary().contains("warnings"));
    }

    @Test
    void shouldFailUnsupportedSmplExtension() {
        QcExecutionResult result = service.inspect("pose.csv", "csv", "text/csv", "x".getBytes(StandardCharsets.UTF_8), AssetType.SMPL_RESULT);
        assertEquals(QcStatus.FAILED, result.status());
        assertTrue(result.reportJson().contains("smplResultExtension"));
    }

    @Test
    void shouldWarnMocapCsvWithoutQuaternionHeaders() {
        String sample = """
                timestamp,acc_x,acc_y
                1000,1.0,2.0
                """;
        QcExecutionResult result = service.inspect("mocap.csv", "csv", "text/csv", sample.getBytes(StandardCharsets.UTF_8), AssetType.MOCAP_CSV);
        assertEquals(QcStatus.WARNING, result.status());
        assertTrue(result.reportJson().contains("mocapQuaternionHeaders"));
    }
}
