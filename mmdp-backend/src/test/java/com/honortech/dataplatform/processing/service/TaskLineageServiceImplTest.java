package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetSourceType;
import com.honortech.dataplatform.processing.dto.TaskLineageResponse;
import com.honortech.dataplatform.processing.entity.AssetLineage;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.AssetLineageMapper;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class TaskLineageServiceImplTest {

    private final AcquisitionTaskService acquisitionTaskService = Mockito.mock(AcquisitionTaskService.class);
    private final DataAssetService dataAssetService = Mockito.mock(DataAssetService.class);
    private final ProcessingJobMapper processingJobMapper = Mockito.mock(ProcessingJobMapper.class);
    private final AssetLineageMapper assetLineageMapper = Mockito.mock(AssetLineageMapper.class);
    private TaskLineageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TaskLineageServiceImpl(acquisitionTaskService, dataAssetService, processingJobMapper, assetLineageMapper);
        AcquisitionTask task = new AcquisitionTask();
        task.setId(1L);
        task.setTaskName("Task 001");
        task.setStatus("READY");
        when(acquisitionTaskService.getTask(1L)).thenReturn(task);
    }

    @Test
    void shouldBuildTaskAssetJobAssetLineage() {
        DataAsset input = asset(11L, "RGB_VIDEO_MP4", null, "rgb.mp4");
        DataAsset output = asset(12L, "ALIGNED_RESULT", 99L, "aligned");
        ProcessingJob job = new ProcessingJob();
        job.setId(99L);
        job.setTaskId(1L);
        job.setPipelineId("RGB_MOCAP_ALIGNMENT");
        job.setStatus("SUCCESS");

        AssetLineage lineage = new AssetLineage();
        lineage.setTaskId(1L);
        lineage.setSourceAssetId(11L);
        lineage.setTargetAssetId(12L);
        lineage.setJobId(99L);
        lineage.setCreatedAt(LocalDateTime.now());

        when(dataAssetService.listByTaskId(1L)).thenReturn(List.of(input, output));
        when(processingJobMapper.selectList(any())).thenReturn(List.of(job));
        when(assetLineageMapper.selectList(any())).thenReturn(List.of(lineage));

        TaskLineageResponse response = service.getTaskLineage(1L);

        assertEquals(4, response.nodes().size());
        assertEquals(3, response.edges().size());
        assertEquals("task-1", response.edges().get(0).source());
        assertEquals("asset-11", response.edges().get(0).target());
        assertEquals("asset-11", response.edges().get(1).source());
        assertEquals("job-99", response.edges().get(1).target());
        assertEquals("job-99", response.edges().get(2).source());
        assertEquals("asset-12", response.edges().get(2).target());
    }

    private DataAsset asset(Long id, String assetType, Long producedByJobId, String name) {
        DataAsset asset = new DataAsset();
        asset.setId(id);
        asset.setTaskId(1L);
        asset.setSourceType(AssetSourceType.UPLOADED_FILE.name());
        asset.setAssetType(assetType);
        asset.setDisplayName(name);
        asset.setProducedByJobId(producedByJobId);
        return asset;
    }
}
