package com.honortech.dataplatform.asset.controller;

import com.honortech.dataplatform.asset.dto.CreateExternalAssetRequest;
import com.honortech.dataplatform.asset.dto.DataAssetResponse;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/tasks/{taskId}/assets")
public class DataAssetController {

    private final DataAssetService dataAssetService;

    public DataAssetController(DataAssetService dataAssetService) {
        this.dataAssetService = dataAssetService;
    }

    @PostMapping("/external")
    public ApiResponse<DataAssetResponse> createExternalAsset(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateExternalAssetRequest request) {
        DataAsset asset = dataAssetService.createExternalAsset(taskId, request);
        DataAssetResponse response = dataAssetService.listAssetResponsesByTaskId(taskId).stream()
                .filter(item -> item.id().equals(asset.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Created asset not found"));
        return ApiResponse.success("External asset created", response);
    }

    @GetMapping
    public ApiResponse<List<DataAssetResponse>> listAssets(@PathVariable Long taskId) {
        return ApiResponse.success(dataAssetService.listAssetResponsesByTaskId(taskId));
    }
}
