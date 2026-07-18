package com.honortech.dataplatform.task.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.common.dto.PageResponse;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.service.CollectionProfileService;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.subject.entity.Subject;
import com.honortech.dataplatform.subject.service.SubjectService;
import com.honortech.dataplatform.task.dto.CreateTaskRequest;
import com.honortech.dataplatform.task.dto.TaskListQueryRequest;
import com.honortech.dataplatform.task.dto.TaskResponse;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Validated
@RestController
@RequestMapping("/api/tasks")
public class AcquisitionTaskController {

    private final AcquisitionTaskService acquisitionTaskService;
    private final SubjectService subjectService;
    private final CollectionProfileService collectionProfileService;
    private final CollectionSessionMapper collectionSessionMapper;

    public AcquisitionTaskController(
            AcquisitionTaskService acquisitionTaskService,
            SubjectService subjectService,
            CollectionProfileService collectionProfileService,
            CollectionSessionMapper collectionSessionMapper) {
        this.acquisitionTaskService = acquisitionTaskService;
        this.subjectService = subjectService;
        this.collectionProfileService = collectionProfileService;
        this.collectionSessionMapper = collectionSessionMapper;
    }

    @PostMapping
    public ApiResponse<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.success("Task created", toResponse(acquisitionTaskService.createTask(request)));
    }

    @GetMapping
    public ApiResponse<PageResponse<TaskResponse>> listTasks(TaskListQueryRequest request) {
        Page<AcquisitionTask> page = acquisitionTaskService.listTasks(request);
        List<Long> taskIds = page.getRecords().stream().map(AcquisitionTask::getId).toList();
        Map<Long, Long> sessionCounts = Map.of();
        Map<Long, CollectionSession> latestSessions = Map.of();
        if (!taskIds.isEmpty()) {
            List<CollectionSession> allSessions = collectionSessionMapper.selectList(
                    new LambdaQueryWrapper<CollectionSession>()
                            .in(CollectionSession::getTaskId, taskIds)
                            .orderByDesc(CollectionSession::getStartedAt)
                            .orderByDesc(CollectionSession::getCreatedAt));
            sessionCounts = allSessions.stream()
                    .collect(Collectors.groupingBy(CollectionSession::getTaskId, Collectors.counting()));
            latestSessions = allSessions.stream()
                    .collect(Collectors.toMap(
                            CollectionSession::getTaskId,
                            session -> session,
                            (left, right) -> Comparator
                                    .comparing(CollectionSession::getStartedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                                    .thenComparing(CollectionSession::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                                    .compare(left, right) >= 0 ? left : right
                    ));
        }
        Map<Long, Long> finalSessionCounts = sessionCounts;
        Map<Long, CollectionSession> finalLatestSessions = latestSessions;
        List<TaskResponse> records = page.getRecords().stream()
                .map(task -> toResponse(task, finalSessionCounts.getOrDefault(task.getId(), 0L), finalLatestSessions.get(task.getId())))
                .toList();
        return ApiResponse.success(PageResponse.of(page, records));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> getTask(@PathVariable Long taskId) {
        AcquisitionTask task = acquisitionTaskService.getTask(taskId);
        List<CollectionSession> sessions = collectionSessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>()
                        .eq(CollectionSession::getTaskId, taskId)
                        .orderByDesc(CollectionSession::getStartedAt)
                        .orderByDesc(CollectionSession::getCreatedAt));
        return ApiResponse.success(toResponse(task, (long) sessions.size(), sessions.isEmpty() ? null : sessions.get(0)));
    }

    private TaskResponse toResponse(AcquisitionTask task) {
        List<CollectionSession> sessions = collectionSessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>()
                        .eq(CollectionSession::getTaskId, task.getId())
                        .orderByDesc(CollectionSession::getStartedAt)
                        .orderByDesc(CollectionSession::getCreatedAt));
        return toResponse(task, (long) sessions.size(), sessions.isEmpty() ? null : sessions.get(0));
    }

    private TaskResponse toResponse(AcquisitionTask task, long sessionCount, CollectionSession latestSession) {
        Long effectiveSubjectId = latestSession != null && latestSession.getSubjectId() != null
                ? latestSession.getSubjectId() : task.getSubjectId();
        Subject subject = effectiveSubjectId != null ? subjectService.findById(effectiveSubjectId) : null;
        CollectionProfile profile = task.getProfileId() == null ? null : collectionProfileService.getRequiredById(task.getProfileId());
        return new TaskResponse(
                task.getId(),
                task.getTaskCode(),
                task.getTaskName(),
                effectiveSubjectId,
                latestSession != null && latestSession.getSubjectCode() != null
                        ? latestSession.getSubjectCode() : task.getSubjectCode(),
                subject == null ? null : subject.getSubjectName(),
                latestSession != null && latestSession.getActionName() != null
                        ? latestSession.getActionName() : task.getActionName(),
                task.getProfileId(),
                profile == null ? null : profile.getProfileName(),
                task.getDeviceType(),
                task.getModality(),
                task.getCollectDate(),
                task.getScene(),
                task.getOperatorName(),
                task.getCaptureLocation(),
                task.getStatus(),
                task.getRemark(),
                sessionCount,
                latestSession == null ? null : latestSession.getStartedAt(),
                latestSession == null ? null : resolveLatestSessionStatus(latestSession),
                latestSession == null ? null : latestSession.getSessionId(),
                latestSession == null ? null : latestSession.getSessionCode(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private String resolveLatestSessionStatus(CollectionSession latestSession) {
        if (latestSession.getSessionStatus() != null && !latestSession.getSessionStatus().isBlank()) {
            return latestSession.getSessionStatus();
        }
        return latestSession.getUploadStatus();
    }
}
