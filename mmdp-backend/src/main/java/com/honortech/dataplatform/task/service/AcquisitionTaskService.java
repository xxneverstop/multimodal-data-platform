package com.honortech.dataplatform.task.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.honortech.dataplatform.common.enums.TaskStatus;
import com.honortech.dataplatform.task.dto.CreateTaskRequest;
import com.honortech.dataplatform.task.dto.TaskListQueryRequest;
import com.honortech.dataplatform.task.entity.AcquisitionTask;

public interface AcquisitionTaskService {

    AcquisitionTask createTask(CreateTaskRequest request);

    Page<AcquisitionTask> listTasks(TaskListQueryRequest request);

    AcquisitionTask getTask(Long taskId);

    void updateStatus(Long taskId, TaskStatus status);
}
