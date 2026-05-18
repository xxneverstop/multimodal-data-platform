package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.PipelineReadinessStatus;
import com.honortech.dataplatform.processing.PipelineIds;
import com.honortech.dataplatform.processing.dto.AvailablePipelineResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
public class PipelineAvailabilityServiceImpl implements PipelineAvailabilityService {

    private static final List<AssetType> REQUIRED_ASSETS = List.of(AssetType.MOCAP_CSV, AssetType.SMPL_RESULT);
    private static final List<AssetType> OPTIONAL_ASSETS = List.of(AssetType.RGB_VIDEO_MP4, AssetType.RGB_SEQ_RAW, AssetType.CAMERA_PARAM);

    private final DataAssetService dataAssetService;

    public PipelineAvailabilityServiceImpl(DataAssetService dataAssetService) {
        this.dataAssetService = dataAssetService;
    }

    @Override
    public List<AvailablePipelineResponse> listAvailablePipelines(Long taskId) {
        List<DataAsset> assets = dataAssetService.listByTaskId(taskId);
        Set<AssetType> existingTypes = EnumSet.noneOf(AssetType.class);
        assets.forEach(asset -> existingTypes.add(AssetType.fromNullable(asset.getAssetType())));

        List<String> missingRequiredAssets = REQUIRED_ASSETS.stream()
                .filter(assetType -> !existingTypes.contains(assetType))
                .map(Enum::name)
                .toList();

        List<String> existingAssets = new ArrayList<>(existingTypes.stream().map(Enum::name).toList());
        List<String> suggestedNextActions = missingRequiredAssets.isEmpty()
                ? OPTIONAL_ASSETS.stream()
                .filter(assetType -> !existingTypes.contains(assetType))
                .map(assetType -> "可补充资产 " + assetType.name() + " 以增强对齐结果")
                .toList()
                : missingRequiredAssets.stream()
                .map(assetType -> "请先补充必需资产 " + assetType)
                .toList();

        return List.of(new AvailablePipelineResponse(
                PipelineIds.RGB_MOCAP_ALIGNMENT,
                "RGB/SMPL 与动捕服 CSV 时间对齐",
                "基于任务下已有资产，执行 RGB/SMPL 与动捕服 CSV 的时间对齐。",
                missingRequiredAssets.isEmpty()
                        ? PipelineReadinessStatus.READY.name()
                        : PipelineReadinessStatus.MISSING_REQUIRED_ASSETS.name(),
                missingRequiredAssets,
                existingAssets,
                suggestedNextActions
        ));
    }
}
