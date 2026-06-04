package com.honortech.dataplatform.asset.service;

import com.honortech.dataplatform.asset.dto.CreateExternalAssetRequest;
import com.honortech.dataplatform.asset.dto.CreateDerivedAssetRequest;
import com.honortech.dataplatform.asset.dto.DataAssetResponse;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.file.entity.DataFile;

import java.util.List;

public interface DataAssetService {

    DataAsset createUploadedAsset(Long taskId, DataFile file, AssetType assetType);

    DataAsset findByFileId(Long fileId);

    DataAsset createUploadedAssetIfAbsent(Long taskId, DataFile file, AssetType assetType);

    DataAsset createAcquisitionAsset(Long taskId, Long sessionId, String sourceKey, DataFile file, AssetType assetType);

    DataAsset createExternalAsset(Long taskId, CreateExternalAssetRequest request);

    DataAsset createDerivedAsset(Long taskId, Long producedByJobId, CreateDerivedAssetRequest request);

    List<DataAsset> listByTaskId(Long taskId);

    List<DataAssetResponse> listAssetResponsesByTaskId(Long taskId);
}
