package com.honortech.dataplatform.processing.executor;

import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.processing.PipelineIds;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RgbMocapAlignmentExecutor implements PipelineExecutor {

    @Override
    public String getPipelineId() {
        return PipelineIds.RGB_MOCAP_ALIGNMENT;
    }

    @Override
    public Map<String, Object> execute(ProcessingJob job, List<DataAsset> assets) {
        List<Map<String, Object>> inputAssets = assets.stream()
                .map(asset -> Map.<String, Object>of(
                        "assetId", asset.getId(),
                        "assetType", asset.getAssetType(),
                        "displayName", asset.getDisplayName(),
                        "sourceType", asset.getSourceType()
                ))
                .toList();

        return Map.of(
                "pipelineId", job.getPipelineId(),
                "inputAssets", inputAssets,
                "offsetMs", 42,
                "matchedFrames", 128,
                "qualityStatus", "MOCK_READY",
                "message", "当前为占位结果，后续接入 Python 时间对齐脚本"
        );
    }
}
