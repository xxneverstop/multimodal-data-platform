package com.honortech.dataplatform.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.honortech.dataplatform.common.enums.TaskStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.util.BusinessCodeGenerator;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.service.CollectionProfileService;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.subject.entity.Subject;
import com.honortech.dataplatform.subject.service.SubjectService;
import com.honortech.dataplatform.task.dto.CreateTaskRequest;
import com.honortech.dataplatform.task.dto.TaskListQueryRequest;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.mapper.AcquisitionTaskMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Service
public class AcquisitionTaskServiceImpl implements AcquisitionTaskService {

    private final AcquisitionTaskMapper acquisitionTaskMapper;
    private final SubjectService subjectService;
    private final CollectionProfileService collectionProfileService;
    private final BusinessCodeGenerator businessCodeGenerator;
    private final CollectionSessionMapper collectionSessionMapper;

    public AcquisitionTaskServiceImpl(
            AcquisitionTaskMapper acquisitionTaskMapper,
            SubjectService subjectService,
            CollectionProfileService collectionProfileService,
            BusinessCodeGenerator businessCodeGenerator,
            CollectionSessionMapper collectionSessionMapper) {
        this.acquisitionTaskMapper = acquisitionTaskMapper;
        this.subjectService = subjectService;
        this.collectionProfileService = collectionProfileService;
        this.businessCodeGenerator = businessCodeGenerator;
        this.collectionSessionMapper = collectionSessionMapper;
    }

    @Override
    public AcquisitionTask createTask(CreateTaskRequest request) {
        Subject subject = (request.subjectCode() != null && !request.subjectCode().isBlank())
                || (request.subjectName() != null && !request.subjectName().isBlank())
                ? subjectService.resolveSubject(request.subjectCode(), request.subjectName())
                : null;
        CollectionProfile profile = request.profileId() == null ? null : collectionProfileService.getRequiredById(request.profileId());
        AcquisitionTask task = new AcquisitionTask();
        task.setTaskCode(businessCodeGenerator.next("TASK"));
        task.setTaskName(request.taskName());
        if (subject != null) {
            task.setSubjectId(subject.getId());
            task.setSubjectCode(subject.getSubjectCode());
            task.setSubjectCodeSnapshot(subject.getSubjectCode());
        }
        if (request.actionName() != null && !request.actionName().isBlank()) {
            task.setActionName(request.actionName());
        }
        task.setDeviceType(request.deviceType() == null || request.deviceType().isBlank()
                ? (profile == null ? "" : profile.getDeviceGroupCode())
                : request.deviceType());
        task.setModality(request.modality() == null || request.modality().isBlank()
                ? (profile == null ? "" : profile.getModalityGroupCode())
                : request.modality());
        task.setCollectDate(request.collectDate());
        task.setScene(request.scene());
        task.setOperatorName(request.operatorName());
        task.setCaptureLocation(request.captureLocation());
        task.setProfileId(profile == null ? null : profile.getId());
        task.setTaskSource("MANUAL");
        task.setCollectorClientId(null);
        task.setRemark(request.remark());
        task.setStatus(TaskStatus.CREATED.name());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setDeleted(0);
        acquisitionTaskMapper.insert(task);
        return task;
    }

    @Override
    public Page<AcquisitionTask> listTasks(TaskListQueryRequest request) {
        Page<AcquisitionTask> page = new Page<>(
                request.getPage() == null || request.getPage() < 1 ? 1 : request.getPage(),
                request.getPageSize() == null || request.getPageSize() < 1 ? 20 : request.getPageSize()
        );
        LambdaQueryWrapper<AcquisitionTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getTaskId() != null, AcquisitionTask::getId, request.getTaskId());
        wrapper.eq(hasText(request.getTaskCode()), AcquisitionTask::getTaskCode, trimToNull(request.getTaskCode()));
        wrapper.eq(hasText(request.getStatus()), AcquisitionTask::getStatus, trimToNull(request.getStatus()));

        // subjectCode/actionName filters now query via session table
        if (hasText(request.getSubjectCode())) {
            List<Long> taskIds = findTaskIdsBySessionField("subject_code", trimToNull(request.getSubjectCode()));
            if (taskIds.isEmpty()) {
                wrapper.eq(AcquisitionTask::getId, -1L); // force empty result
            } else {
                wrapper.in(AcquisitionTask::getId, taskIds);
            }
        }
        if (hasText(request.getActionName())) {
            List<Long> taskIds = findTaskIdsBySessionField("action_name", trimToNull(request.getActionName()));
            if (taskIds.isEmpty()) {
                wrapper.eq(AcquisitionTask::getId, -1L); // force empty result
            } else {
                wrapper.in(AcquisitionTask::getId, taskIds);
            }
        }

        if (hasText(request.getKeyword())) {
            String keyword = trimToNull(request.getKeyword());
            List<Long> sessionTaskIds = findTaskIdsBySessionKeyword(keyword);
            if (!sessionTaskIds.isEmpty()) {
                wrapper.and(q -> q
                        .like(AcquisitionTask::getTaskName, keyword)
                        .or()
                        .in(AcquisitionTask::getId, sessionTaskIds)
                );
            } else {
                wrapper.like(AcquisitionTask::getTaskName, keyword);
            }
        }
        wrapper.ge(request.getCollectDateFrom() != null, AcquisitionTask::getCollectDate, request.getCollectDateFrom());
        wrapper.le(request.getCollectDateTo() != null, AcquisitionTask::getCollectDate, request.getCollectDateTo());
        applySorting(wrapper, request.getSortBy(), request.getSortOrder());
        return acquisitionTaskMapper.selectPage(page, wrapper);
    }

    private List<Long> findTaskIdsBySessionField(String column, String value) {
        List<CollectionSession> sessions = collectionSessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>()
                        .select(CollectionSession::getTaskId)
                        .like("subject_code".equals(column), CollectionSession::getSubjectCode, value)
                        .like("action_name".equals(column), CollectionSession::getActionName, value)
                        .groupBy(CollectionSession::getTaskId)
        );
        return sessions.stream().map(CollectionSession::getTaskId).distinct().toList();
    }

    private List<Long> findTaskIdsBySessionKeyword(String keyword) {
        List<CollectionSession> sessions = collectionSessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>()
                        .select(CollectionSession::getTaskId)
                        .and(q -> q
                                .like(CollectionSession::getSubjectCode, keyword)
                                .or()
                                .like(CollectionSession::getActionName, keyword)
                        )
                        .groupBy(CollectionSession::getTaskId)
        );
        return sessions.stream().map(CollectionSession::getTaskId).distinct().toList();
    }

    @Override
    public AcquisitionTask getTask(Long taskId) {
        AcquisitionTask task = acquisitionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException("Task not found: " + taskId);
        }
        return task;
    }

    @Override
    public void updateStatus(Long taskId, TaskStatus status) {
        AcquisitionTask task = getTask(taskId);
        task.setStatus(status.name());
        task.setUpdatedAt(LocalDateTime.now());
        acquisitionTaskMapper.updateById(task);
    }

    private void applySorting(LambdaQueryWrapper<AcquisitionTask> wrapper, String sortBy, String sortOrder) {
        boolean ascending = "asc".equalsIgnoreCase(trimToNull(sortOrder));
        String sortKey = trimToNull(sortBy);
        if (sortKey == null) {
            wrapper.orderByDesc(AcquisitionTask::getCreatedAt);
            return;
        }
        switch (sortKey.toLowerCase(Locale.ROOT)) {
            case "taskid", "id" -> wrapper.orderBy(true, ascending, AcquisitionTask::getId);
            case "collectdate" -> wrapper.orderBy(true, ascending, AcquisitionTask::getCollectDate);
            case "createdat" -> wrapper.orderBy(true, ascending, AcquisitionTask::getCreatedAt);
            default -> wrapper.orderByDesc(AcquisitionTask::getCreatedAt);
        }
    }

    private boolean hasText(String value) {
        return trimToNull(value) != null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

}
