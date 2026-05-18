package com.honortech.dataplatform.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetSourceType;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.processing.PipelineIds;
import com.honortech.dataplatform.processing.dto.CreateProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class ProcessingJobServiceImplTest {

    private final ProcessingJobMapper processingJobMapper = Mockito.mock(ProcessingJobMapper.class);
    private final AcquisitionTaskService acquisitionTaskService = Mockito.mock(AcquisitionTaskService.class);
    private final DataAssetService dataAssetService = Mockito.mock(DataAssetService.class);
    private final ProcessingJobExecutor processingJobExecutor = Mockito.mock(ProcessingJobExecutor.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ProcessingJobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProcessingJobServiceImpl(
                processingJobMapper,
                acquisitionTaskService,
                dataAssetService,
                processingJobExecutor,
                objectMapper
        );
        AcquisitionTask task = new AcquisitionTask();
        task.setId(1L);
        when(acquisitionTaskService.getTask(1L)).thenReturn(task);
        doAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            job.setId(99L);
            return 1;
        }).when(processingJobMapper).insert(any(ProcessingJob.class));
    }

    @Test
    void shouldFailWhenRequiredAssetsMissing() {
        when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(asset("MOCAP_CSV")));

        BizException exception = assertThrows(BizException.class, () -> service.createJob(1L, new CreateProcessingJobRequest(PipelineIds.RGB_MOCAP_ALIGNMENT, null)));

        assertEquals("Missing required assets: SMPL_RESULT", exception.getMessage());
    }

    @Test
    void shouldCreateMockJobWhenAssetsReady() {
        when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(asset("MOCAP_CSV"), asset("SMPL_RESULT")));
        when(processingJobExecutor.execute(any(ProcessingJob.class), any())).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            job.setStatus("SUCCESS");
            job.setResultJson("{\"offsetMs\":42}");
            job.setUpdatedAt(LocalDateTime.now());
            return job;
        });

        ProcessingJobResponse response = service.createJob(1L, new CreateProcessingJobRequest(PipelineIds.RGB_MOCAP_ALIGNMENT, null));

        assertEquals("SUCCESS", response.status());
        assertEquals(42, response.resultJson().get("offsetMs").asInt());
    }

    private DataAsset asset(String assetType) {
        DataAsset asset = new DataAsset();
        asset.setId((long) assetType.hashCode());
        asset.setTaskId(1L);
        asset.setSourceType(AssetSourceType.UPLOADED_FILE.name());
        asset.setAssetType(assetType);
        asset.setDisplayName(assetType);
        return asset;
    }
}
