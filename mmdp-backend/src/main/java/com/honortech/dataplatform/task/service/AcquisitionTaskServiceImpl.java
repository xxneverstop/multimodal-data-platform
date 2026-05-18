package com.honortech.dataplatform.task.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.honortech.dataplatform.common.enums.TaskStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.task.dto.CreateTaskRequest;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.mapper.AcquisitionTaskMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AcquisitionTaskServiceImpl implements AcquisitionTaskService {

    private final AcquisitionTaskMapper acquisitionTaskMapper;

    public AcquisitionTaskServiceImpl(AcquisitionTaskMapper acquisitionTaskMapper) {
        this.acquisitionTaskMapper = acquisitionTaskMapper;
    }

    @Override
    public AcquisitionTask createTask(CreateTaskRequest request) {
        AcquisitionTask task = new AcquisitionTask();
        task.setTaskName(request.taskName());
        task.setSubjectCode(request.subjectCode());
        task.setActionName(request.actionName());
        task.setDeviceType(request.deviceType());
        task.setModality(request.modality());
        task.setCollectDate(request.collectDate());
        task.setScene(request.scene());
        task.setOperatorName(request.operatorName());
        task.setCaptureLocation(request.captureLocation());
        task.setRemark(request.remark());
        task.setStatus(TaskStatus.CREATED.name());
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        task.setDeleted(0);
        acquisitionTaskMapper.insert(task);
        return task;
    }

    @Override
    public Page<AcquisitionTask> listTasks(long current, long size) {
        Page<AcquisitionTask> page = new Page<>(current, size);
        return acquisitionTaskMapper.selectPage(
                page,
                new LambdaQueryWrapper<AcquisitionTask>()
                        .orderByDesc(AcquisitionTask::getCreatedAt)
        );
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
}
