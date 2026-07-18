package com.honortech.dataplatform.processing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.dto.CreateDerivedAssetRequest;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.mapper.DataAssetMapper;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetSourceType;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.storage.StorageProperties;
import com.honortech.dataplatform.common.storage.StorageRouter;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.pipeline.entity.PipelineDefinition;
import com.honortech.dataplatform.pipeline.mapper.PipelineDefinitionMapper;
import com.honortech.dataplatform.processing.PipelineIds;
import com.honortech.dataplatform.processing.dto.CreateManualProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.CreateProcessingJobRequest;
import com.honortech.dataplatform.processing.dto.ManualProcessingJobResponse;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;
import com.honortech.dataplatform.processing.entity.AssetLineage;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.AssetLineageMapper;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final DataAssetMapper dataAssetMapper = Mockito.mock(DataAssetMapper.class);
    private final ProcessingJobExecutor processingJobExecutor = Mockito.mock(ProcessingJobExecutor.class);
    private final AssetLineageMapper assetLineageMapper = Mockito.mock(AssetLineageMapper.class);
    private final CollectionSessionMapper sessionMapper = Mockito.mock(CollectionSessionMapper.class);
    private final DataFileMapper dataFileMapper = Mockito.mock(DataFileMapper.class);
    private final PipelineDefinitionMapper pipelineDefMapper = Mockito.mock(PipelineDefinitionMapper.class);
    private final StorageRouter storageRouter = Mockito.mock(StorageRouter.class);
    private final StorageProperties storageProperties = Mockito.mock(StorageProperties.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WorkerPipelineRegistry workerPipelineRegistry = Mockito.mock(WorkerPipelineRegistry.class);
    private final List<AssetLineage> storedLineages = new ArrayList<>();
    private ProcessingJobServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProcessingJobServiceImpl(
                processingJobMapper,
                acquisitionTaskService,
                dataAssetService,
                dataAssetMapper,
                processingJobExecutor,
                assetLineageMapper,
                sessionMapper,
                dataFileMapper,
                pipelineDefMapper,
                storageRouter,
                storageProperties,
                objectMapper,
                workerPipelineRegistry
        );
        AcquisitionTask task = new AcquisitionTask();
        task.setId(1L);
        when(acquisitionTaskService.getTask(1L)).thenReturn(task);
        // validatePipelineId() 需要 pipelineDefMapper 返回有效记录
        when(pipelineDefMapper.selectOne(any())).thenReturn(validPipelineDef());
        doAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            job.setId(99L);
            return 1;
        }).when(processingJobMapper).insert(any(ProcessingJob.class));
        doAnswer(invocation -> {
            AssetLineage lineage = invocation.getArgument(0);
            storedLineages.add(lineage);
            return 1;
        }).when(assetLineageMapper).insert(any(AssetLineage.class));
    }

    @Test
    void shouldFailWhenRequiredAssetsMissing() {
        when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(asset(11L, "MOCAP_CSV")));

        BizException exception = assertThrows(BizException.class, () -> service.createJob(1L, new CreateProcessingJobRequest(PipelineIds.RGB_MOCAP_ALIGNMENT, null)));

        assertEquals("Missing required assets: SMPL_RESULT", exception.getMessage());
    }

    @Test
    void shouldCreateMockJobWhenAssetsReady() {
        when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(asset(11L, "MOCAP_CSV"), asset(12L, "SMPL_RESULT")));
        when(processingJobExecutor.execute(any(ProcessingJob.class), any())).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            job.setStatus("SUCCESS");
            job.setExecutorType("MOCK");
            job.setResultJson("{\"offsetMs\":42}");
            job.setUpdatedAt(LocalDateTime.now());
            return job;
        });

        ProcessingJobResponse response = service.createJob(1L, new CreateProcessingJobRequest(PipelineIds.RGB_MOCAP_ALIGNMENT, null));

        assertEquals("SUCCESS", response.status());
        assertEquals("MOCK", response.executorType());
        assertEquals(42, response.resultJson().get("offsetMs").asInt());
    }

    @Test
    void shouldCreateManualJobAndDerivedAssets() {
        DataAsset inputAsset = asset(11L, "RGB_VIDEO_MP4");
        DataAsset outputAsset = asset(21L, "ALIGNED_RESULT");
        outputAsset.setProducedByJobId(99L);
        when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(inputAsset));
        when(dataAssetService.createDerivedAsset(any(), any(), any())).thenReturn(outputAsset);
        when(dataAssetService.listAssetResponsesByTaskId(1L)).thenReturn(List.of(
                new com.honortech.dataplatform.asset.dto.DataAssetResponse(
                        21L,
                        1L,
                        null,
                        "EXTERNAL_PATH",
                        null,
                        "ALIGNED_RESULT",
                        "aligned",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "\\\\nas\\aligned",
                        null,
                        null,
                        "aligned output",
                        null,
                        99L,
                        LocalDateTime.now(),
                        null,
                        null
                )
        ));

        ManualProcessingJobResponse response = service.createManualJob(1L, new CreateManualProcessingJobRequest(
                PipelineIds.RGB_MOCAP_ALIGNMENT,
                List.of(11L),
                List.of(new CreateDerivedAssetRequest("aligned", "ALIGNED_RESULT", "EXTERNAL_PATH", null, "\\\\nas\\aligned", "aligned output")),
                "alice",
                "aligner",
                "v1",
                objectMapper.createObjectNode().put("syncOffsetMs", 42),
                "\\\\nas\\logs\\job-99.log",
                "manual path"
        ));

        assertEquals("MANUAL", response.job().executorType());
        assertEquals("SUCCESS", response.job().status());
        assertEquals(1, response.outputAssets().size());
        assertEquals(99L, response.outputAssets().getFirst().producedByJobId());
        assertEquals(1, storedLineages.size());
        assertEquals(11L, storedLineages.getFirst().getSourceAssetId());
        assertEquals(21L, storedLineages.getFirst().getTargetAssetId());
    }

    @Test
    void shouldFailWhenInputAssetDoesNotBelongToTask() {
        when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(asset(11L, "RGB_VIDEO_MP4")));

        BizException exception = assertThrows(BizException.class, () -> service.createManualJob(1L, new CreateManualProcessingJobRequest(
                PipelineIds.RGB_MOCAP_ALIGNMENT,
                List.of(999L),
                List.of(new CreateDerivedAssetRequest("aligned", "ALIGNED_RESULT", "EXTERNAL_PATH", null, "\\\\nas\\aligned", null)),
                null, null, null, null, null, null
        )));

        assertEquals("Input asset does not belong to task: 999", exception.getMessage());
    }

    @Test
    void shouldFailWhenOutputAssetHasNoPathOrFileId() {
        when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(asset(11L, "RGB_VIDEO_MP4")));
        when(dataAssetService.createDerivedAsset(any(), any(), any())).thenThrow(new BizException("Each output asset must provide exactly one of fileId or externalPath"));

        BizException exception = assertThrows(BizException.class, () -> service.createManualJob(1L, new CreateManualProcessingJobRequest(
                PipelineIds.RGB_MOCAP_ALIGNMENT,
                List.of(11L),
                List.of(new CreateDerivedAssetRequest("aligned", "ALIGNED_RESULT", "EXTERNAL_PATH", null, null, null)),
                null, null, null, null, null, null
        )));

        assertEquals("Each output asset must provide exactly one of fileId or externalPath", exception.getMessage());
    }

    private PipelineDefinition validPipelineDef() {
        PipelineDefinition def = new PipelineDefinition();
        def.setPipelineId(PipelineIds.RGB_MOCAP_ALIGNMENT);
        def.setEnabled(1);
        def.setDisplayName("RGB Mocap对齐");
        def.setExecutorType("PYTHON_WORKER");
        return def;
    }

    private DataAsset asset(Long id, String assetType) {
        DataAsset asset = new DataAsset();
        asset.setId(id);
        asset.setTaskId(1L);
        asset.setSourceType(AssetSourceType.UPLOADED_FILE.name());
        asset.setAssetType(assetType);
        asset.setDisplayName(assetType);
        return asset;
    }
}
