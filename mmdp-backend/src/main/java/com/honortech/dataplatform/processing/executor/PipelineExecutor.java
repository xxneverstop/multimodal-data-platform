package com.honortech.dataplatform.processing.executor;

import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.processing.entity.ProcessingJob;

import java.util.List;
import java.util.Map;

public interface PipelineExecutor {

    String getPipelineId();

    Map<String, Object> execute(ProcessingJob job, List<DataAsset> assets);
}
