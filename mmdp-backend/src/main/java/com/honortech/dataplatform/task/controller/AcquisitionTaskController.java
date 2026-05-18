package com.honortech.dataplatform.task.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.task.dto.CreateTaskRequest;
import com.honortech.dataplatform.task.dto.TaskPageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/tasks")
public class AcquisitionTaskController {

    private final AcquisitionTaskService acquisitionTaskService;

    public AcquisitionTaskController(AcquisitionTaskService acquisitionTaskService) {
        this.acquisitionTaskService = acquisitionTaskService;
    }

    @PostMapping
    public ApiResponse<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.success("Task created", toResponse(acquisitionTaskService.createTask(request)));
    }

    @GetMapping
    public ApiResponse<TaskPageResponse> listTasks(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size) {
        Page<AcquisitionTask> page = acquisitionTaskService.listTasks(current, size);
        List<TaskResponse> records = page.getRecords().stream().map(this::toResponse).toList();
        return ApiResponse.success(new TaskPageResponse(page.getCurrent(), page.getSize(), page.getTotal(), records));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<TaskResponse> getTask(@PathVariable Long taskId) {
        return ApiResponse.success(toResponse(acquisitionTaskService.getTask(taskId)));
    }

    private TaskResponse toResponse(AcquisitionTask task) {
        return new TaskResponse(
                task.getId(),
                task.getTaskName(),
                task.getSubjectCode(),
                task.getActionName(),
                task.getDeviceType(),
                task.getModality(),
                task.getCollectDate(),
                task.getScene(),
                task.getOperatorName(),
                task.getCaptureLocation(),
                task.getStatus(),
                task.getRemark(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
