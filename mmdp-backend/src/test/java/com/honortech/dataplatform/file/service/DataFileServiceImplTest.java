package com.honortech.dataplatform.file.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.config.MinioProperties;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.QcStatus;
import com.honortech.dataplatform.common.util.MinioStorageClient;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.qc.dto.QcExecutionResult;
import com.honortech.dataplatform.qc.mapper.QcReportMapper;
import com.honortech.dataplatform.qc.service.QcInspectionService;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataFileServiceImplTest {

    private final AcquisitionTaskService acquisitionTaskService = Mockito.mock(AcquisitionTaskService.class);
    private final DataFileMapper dataFileMapper = Mockito.mock(DataFileMapper.class);
    private final QcReportMapper qcReportMapper = Mockito.mock(QcReportMapper.class);
    private final QcInspectionService qcInspectionService = Mockito.mock(QcInspectionService.class);
    private final MinioStorageClient minioStorageClient = Mockito.mock(MinioStorageClient.class);
    private final DataAssetService dataAssetService = Mockito.mock(DataAssetService.class);
    private final MinioProperties minioProperties = new MinioProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private DataFileServiceImpl service;

    @BeforeEach
    void setUp() {
        minioProperties.setBucket("mmdp-bucket");
        service = new DataFileServiceImpl(
                acquisitionTaskService,
                dataFileMapper,
                qcReportMapper,
                qcInspectionService,
                minioStorageClient,
                minioProperties,
                objectMapper,
                dataAssetService
        );
        AcquisitionTask task = new AcquisitionTask();
        task.setId(1L);
        when(acquisitionTaskService.getTask(1L)).thenReturn(task);
        when(minioStorageClient.upload(any(), any(), any())).thenReturn("mmdp-bucket/tasks/1/demo.csv");
        when(qcInspectionService.inspect(any(), any(), any(), any(), any())).thenReturn(
                new QcExecutionResult(QcStatus.PASSED, "ok", "{\"overallStatus\":\"PASSED\"}", "csv")
        );
    }

    @Test
    void shouldDefaultUploadedAssetTypeToOther() throws Exception {
        MultipartFile file = mockFile("demo.csv", "text/csv", "a,b\n1,2".getBytes(StandardCharsets.UTF_8));

        service.uploadTaskFile(1L, file, null);

        verify(qcInspectionService).inspect(eq("demo.csv"), eq("csv"), eq("text/csv"), any(), eq(AssetType.OTHER));
        verify(dataAssetService).createUploadedAsset(eq(1L), any(DataFile.class), eq(AssetType.OTHER));
    }

    @Test
    void shouldCreateUploadedAssetWithProvidedAssetType() throws Exception {
        MultipartFile file = mockFile("mocap.csv", "text/csv", "timestamp,acc_x\n1,2".getBytes(StandardCharsets.UTF_8));

        service.uploadTaskFile(1L, file, "MOCAP_CSV");

        ArgumentCaptor<DataFile> fileCaptor = ArgumentCaptor.forClass(DataFile.class);
        verify(dataAssetService).createUploadedAsset(eq(1L), fileCaptor.capture(), eq(AssetType.MOCAP_CSV));
        assertEquals("mocap.csv", fileCaptor.getValue().getOriginalFilename());
    }

    private MultipartFile mockFile(String filename, String contentType, byte[] bytes) throws Exception {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getContentType()).thenReturn(contentType);
        when(file.getBytes()).thenReturn(bytes);
        when(file.getSize()).thenReturn((long) bytes.length);
        return file;
    }
}
