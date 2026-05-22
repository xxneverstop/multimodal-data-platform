package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.processing.dto.TaskLineageResponse;

public interface TaskLineageService {

    TaskLineageResponse getTaskLineage(Long taskId);
}
