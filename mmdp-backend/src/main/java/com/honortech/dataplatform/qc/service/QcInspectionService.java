package com.honortech.dataplatform.qc.service;

import com.honortech.dataplatform.qc.dto.QcExecutionResult;
import com.honortech.dataplatform.common.enums.AssetType;

public interface QcInspectionService {

    QcExecutionResult inspect(String originalFilename, String fileExt, String contentType, byte[] content, AssetType assetType);
}
