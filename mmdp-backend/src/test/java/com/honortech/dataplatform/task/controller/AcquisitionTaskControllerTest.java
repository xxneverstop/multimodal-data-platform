package com.honortech.dataplatform.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.honortech.dataplatform.profile.service.CollectionProfileService;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.subject.service.SubjectService;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AcquisitionTaskControllerTest {

    @Test
    void shouldReturnLatestSessionIdentityInTaskList() throws Exception {
        AcquisitionTaskService taskService = Mockito.mock(AcquisitionTaskService.class);
        SubjectService subjectService = Mockito.mock(SubjectService.class);
        CollectionProfileService profileService = Mockito.mock(CollectionProfileService.class);
        CollectionSessionMapper sessionMapper = Mockito.mock(CollectionSessionMapper.class);

        AcquisitionTask task = new AcquisitionTask();
        task.setId(20L);
        task.setTaskCode("TASK-020");
        task.setTaskName("步态采集任务");
        task.setCreatedAt(LocalDateTime.of(2026, 6, 15, 9, 0));
        Page<AcquisitionTask> page = new Page<>(1, 20, 1);
        page.setRecords(List.of(task));
        Mockito.when(taskService.listTasks(Mockito.any())).thenReturn(page);

        CollectionSession session = new CollectionSession();
        session.setTaskId(20L);
        session.setSessionId("session-001");
        session.setSessionCode("SESSION-001");
        session.setStartedAt(LocalDateTime.of(2026, 6, 15, 10, 0));
        Mockito.when(sessionMapper.selectList(Mockito.any())).thenReturn(List.of(session));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AcquisitionTaskController(taskService, subjectService, profileService, sessionMapper)
        ).build();

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].latestSessionId").value("session-001"))
                .andExpect(jsonPath("$.data.records[0].latestSessionCode").value("SESSION-001"));
    }
}
