package com.honortech.dataplatform.pipeline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.pipeline.dto.PipelineDefinitionResponse;
import com.honortech.dataplatform.pipeline.entity.PipelineDefinition;
import com.honortech.dataplatform.pipeline.entity.ProfilePipeline;
import com.honortech.dataplatform.pipeline.mapper.PipelineDefinitionMapper;
import com.honortech.dataplatform.pipeline.mapper.ProfilePipelineMapper;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.processing.service.WorkerPipelineRegistry;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PipelineDefinitionServiceImplTest {

    @Test
    void shouldUseJobIdToFindLatestJobWhenTimestampsAreInDifferentTimezones() {
        PipelineDefinitionMapper pipelineMapper = Mockito.mock(PipelineDefinitionMapper.class);
        ProfilePipelineMapper profilePipelineMapper = Mockito.mock(ProfilePipelineMapper.class);
        CollectionSessionMapper sessionMapper = Mockito.mock(CollectionSessionMapper.class);
        DataAssetService dataAssetService = Mockito.mock(DataAssetService.class);
        ProcessingJobMapper processingJobMapper = Mockito.mock(ProcessingJobMapper.class);
        WorkerPipelineRegistry workerPipelineRegistry = Mockito.mock(WorkerPipelineRegistry.class);
        PipelineDefinitionServiceImpl service = new PipelineDefinitionServiceImpl(
                pipelineMapper,
                profilePipelineMapper,
                sessionMapper,
                dataAssetService,
                processingJobMapper,
                new ObjectMapper(),
                workerPipelineRegistry);

        CollectionSession session = new CollectionSession();
        session.setId(74L);
        session.setTaskId(1L);
        session.setProfileId(1L);
        when(sessionMapper.selectById(74L)).thenReturn(session);

        ProfilePipeline link = new ProfilePipeline();
        link.setProfileId(1L);
        link.setPipelineId("G1_GENERATE_PLAYBACK");
        link.setEnabled(1);
        when(profilePipelineMapper.selectList(any())).thenReturn(List.of(link));

        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.setId(1L);
        pipeline.setPipelineId("G1_GENERATE_PLAYBACK");
        pipeline.setInputAssetTypes(null);
        pipeline.setEnabled(1);
        when(pipelineMapper.selectList(any())).thenReturn(List.of(pipeline));
        when(dataAssetService.listByTaskId(1L)).thenReturn(List.of());
        when(workerPipelineRegistry.isRegistered("G1_GENERATE_PLAYBACK")).thenReturn(true);

        ProcessingJob olderCleanedJob = job(
                101L,
                "CLEANED",
                LocalDateTime.of(2026, 8, 17, 20, 49, 56));
        ProcessingJob newerSuccessfulJob = job(
                104L,
                "SUCCESS",
                LocalDateTime.of(2026, 8, 17, 13, 52, 46));
        when(processingJobMapper.selectList(any())).thenReturn(List.of(olderCleanedJob, newerSuccessfulJob));

        PipelineDefinitionResponse response = service.getAvailablePipelines(74L).getFirst();

        assertEquals("SUCCESS", response.latestJobStatus());
        assertFalse(response.isReady());
    }

    private ProcessingJob job(Long id, String status, LocalDateTime createdAt) {
        ProcessingJob job = new ProcessingJob();
        job.setId(id);
        job.setSessionId(74L);
        job.setPipelineId("G1_GENERATE_PLAYBACK");
        job.setStatus(status);
        job.setCreatedAt(createdAt);
        return job;
    }
}
