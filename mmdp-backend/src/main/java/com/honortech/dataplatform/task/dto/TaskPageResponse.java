package com.honortech.dataplatform.task.dto;

import java.util.List;

public record TaskPageResponse(long current, long size, long total, List<TaskResponse> records) {
}
