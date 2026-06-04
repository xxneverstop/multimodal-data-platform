package com.honortech.dataplatform.asset.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.asset.dto.CreateExternalAssetRequest;
import com.honortech.dataplatform.asset.dto.CreateDerivedAssetRequest;
import com.honortech.dataplatform.asset.dto.DataAssetResponse;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.mapper.DataAssetMapper;
import com.honortech.dataplatform.common.enums.AssetSourceType;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class DataAssetServiceImpl implements DataAssetService {

    private final DataAssetMapper dataAssetMapper;
    private final AcquisitionTaskService acquisitionTaskService;
    private final DataFileMapper dataFileMapper;

    public DataAssetServiceImpl(
            DataAssetMapper dataAssetMapper,
            AcquisitionTaskService acquisitionTaskService,
            DataFileMapper dataFileMapper) {
        this.dataAssetMapper = dataAssetMapper;
        this.acquisitionTaskService = acquisitionTaskService;
        this.dataFileMapper = dataFileMapper;
    }

    @Override
    @Transactional
    public DataAsset createUploadedAsset(Long taskId, DataFile file, AssetType assetType) {
        acquisitionTaskService.getTask(taskId);
        DataAsset asset = new DataAsset();
        asset.setTaskId(taskId);
        asset.setSessionId(file.getSessionId());
        asset.setSourceType(AssetSourceType.UPLOADED_FILE.name());
        asset.setSourceKey(file.getSourceKey());
        asset.setAssetType(assetType.name());
        asset.setDisplayName(file.getOriginalFilename());
        asset.setFileId(file.getId());
        asset.setFileFormat(file.getFileExt());
        asset.setCreatedAt(LocalDateTime.now());
        dataAssetMapper.insert(asset);
        return asset;
    }

    @Override
    @Transactional
    public DataAsset createAcquisitionAsset(Long taskId, Long sessionId, String sourceKey, DataFile file, AssetType assetType) {
        acquisitionTaskService.getTask(taskId);
        DataAsset asset = new DataAsset();
        asset.setTaskId(taskId);
        asset.setSessionId(sessionId);
        asset.setSourceType(AssetSourceType.ACQUISITION_SYNC.name());
        asset.setSourceKey(sourceKey);
        asset.setAssetType(assetType.name());
        asset.setDisplayName(file.getOriginalFilename());
        asset.setFileId(file.getId());
        asset.setFileFormat(file.getFileExt());
        asset.setCreatedAt(LocalDateTime.now());
        dataAssetMapper.insert(asset);
        return asset;
    }

    @Override
    public DataAsset findByFileId(Long fileId) {
        if (fileId == null) {
            return null;
        }
        return dataAssetMapper.selectOne(
                new LambdaQueryWrapper<DataAsset>()
                        .eq(DataAsset::getFileId, fileId)
                        .last("limit 1")
        );
    }

    @Override
    @Transactional
    public DataAsset createUploadedAssetIfAbsent(Long taskId, DataFile file, AssetType assetType) {
        DataAsset existing = findByFileId(file.getId());
        if (existing != null) {
            return existing;
        }
        return createUploadedAsset(taskId, file, assetType);
    }

    @Override
    @Transactional
    public DataAsset createExternalAsset(Long taskId, CreateExternalAssetRequest request) {
        acquisitionTaskService.getTask(taskId);
        AssetType assetType = AssetType.fromNullable(request.assetType());
        if (request.externalPath() == null || request.externalPath().isBlank()) {
            throw new BizException("externalPath must not be blank");
        }
        if (assetType == AssetType.RGB_SEQ_RAW && (request.description() == null || request.description().isBlank())) {
            throw new BizException("description is required for RGB_SEQ_RAW external assets");
        }

        DataAsset asset = new DataAsset();
        asset.setTaskId(taskId);
        asset.setSessionId(null);
        asset.setSourceType(AssetSourceType.EXTERNAL_PATH.name());
        asset.setSourceKey(null);
        asset.setAssetType(assetType.name());
        asset.setDisplayName(request.displayName());
        asset.setExternalPath(request.externalPath());
        asset.setFileFormat(request.fileFormat());
        asset.setSizeRemark(request.sizeRemark());
        asset.setDescription(request.description());
        asset.setOperatorRemark(request.operatorRemark());
        asset.setCreatedAt(LocalDateTime.now());
        dataAssetMapper.insert(asset);
        return asset;
    }

    @Override
    @Transactional
    public DataAsset createDerivedAsset(Long taskId, Long producedByJobId, CreateDerivedAssetRequest request) {
        acquisitionTaskService.getTask(taskId);
        AssetType assetType = AssetType.fromNullable(request.assetType());
        AssetSourceType sourceType = AssetSourceType.valueOf(request.sourceType());
        boolean hasFileId = request.fileId() != null;
        boolean hasExternalPath = request.externalPath() != null && !request.externalPath().isBlank();
        if (hasFileId == hasExternalPath) {
            throw new BizException("Each output asset must provide exactly one of fileId or externalPath");
        }

        DataAsset asset = new DataAsset();
        asset.setTaskId(taskId);
        asset.setSessionId(null);
        asset.setSourceType(sourceType.name());
        asset.setSourceKey(null);
        asset.setAssetType(assetType.name());
        asset.setDisplayName(request.assetName());
        asset.setFileId(request.fileId());
        asset.setExternalPath(request.externalPath());
        asset.setDescription(request.description());
        asset.setProducedByJobId(producedByJobId);
        asset.setCreatedAt(LocalDateTime.now());
        dataAssetMapper.insert(asset);
        return asset;
    }

    @Override
    public List<DataAsset> listByTaskId(Long taskId) {
        acquisitionTaskService.getTask(taskId);
        return dataAssetMapper.selectList(
                new LambdaQueryWrapper<DataAsset>()
                        .eq(DataAsset::getTaskId, taskId)
                        .orderByDesc(DataAsset::getCreatedAt)
        );
    }

    @Override
    public List<DataAssetResponse> listAssetResponsesByTaskId(Long taskId) {
        List<DataAsset> assets = listByTaskId(taskId);
        Map<Long, DataFile> fileMap = new HashMap<>();
        assets.stream()
                .map(DataAsset::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(fileId -> fileMap.put(fileId, dataFileMapper.selectById(fileId)));

        return assets.stream().map(asset -> {
            DataFile file = asset.getFileId() == null ? null : fileMap.get(asset.getFileId());
            return new DataAssetResponse(
                    asset.getId(),
                    asset.getTaskId(),
                    asset.getSessionId(),
                    asset.getSourceType(),
                    asset.getSourceKey(),
                    asset.getAssetType(),
                    asset.getDisplayName(),
                    asset.getFileId(),
                    file == null ? null : file.getOriginalFilename(),
                    file == null ? null : file.getFileExt(),
                    file == null ? null : file.getContentType(),
                    file == null ? null : file.getFileSize(),
                    file == null ? null : file.getUploadStatus(),
                    asset.getExternalPath(),
                    asset.getFileFormat(),
                    asset.getSizeRemark(),
                    asset.getDescription(),
                    asset.getOperatorRemark(),
                    asset.getProducedByJobId(),
                    asset.getCreatedAt(),
                    file == null ? null : file.getObjectKey(),
                    file == null ? null : file.getStorageUrl()
            );
        }).toList();
    }
}
