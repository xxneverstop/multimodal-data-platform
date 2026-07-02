package com.honortech.dataplatform.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.admin.dto.CleanupResult;
import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.mapper.DataAssetMapper;
import com.honortech.dataplatform.common.storage.StorageRouter;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.processing.entity.AssetLineage;
import com.honortech.dataplatform.processing.entity.ProcessingJob;
import com.honortech.dataplatform.processing.mapper.AssetLineageMapper;
import com.honortech.dataplatform.processing.mapper.ProcessingJobMapper;
import com.honortech.dataplatform.qc.entity.QcReport;
import com.honortech.dataplatform.qc.mapper.QcReportMapper;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.sessionimport.entity.SessionImportRecord;
import com.honortech.dataplatform.sessionimport.mapper.SessionImportRecordMapper;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.mapper.AcquisitionTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DataCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DataCleanupService.class);

    private final StorageRouter storageRouter;
    private final DataFileMapper dataFileMapper;
    private final DataAssetMapper dataAssetMapper;
    private final AssetLineageMapper assetLineageMapper;
    private final ProcessingJobMapper processingJobMapper;
    private final QcReportMapper qcReportMapper;
    private final SessionImportRecordMapper sessionImportRecordMapper;
    private final CollectionSessionMapper collectionSessionMapper;
    private final AcquisitionTaskMapper acquisitionTaskMapper;

    public DataCleanupService(
            StorageRouter storageRouter,
            DataFileMapper dataFileMapper,
            DataAssetMapper dataAssetMapper,
            AssetLineageMapper assetLineageMapper,
            ProcessingJobMapper processingJobMapper,
            QcReportMapper qcReportMapper,
            SessionImportRecordMapper sessionImportRecordMapper,
            CollectionSessionMapper collectionSessionMapper,
            AcquisitionTaskMapper acquisitionTaskMapper) {
        this.storageRouter = storageRouter;
        this.dataFileMapper = dataFileMapper;
        this.dataAssetMapper = dataAssetMapper;
        this.assetLineageMapper = assetLineageMapper;
        this.processingJobMapper = processingJobMapper;
        this.qcReportMapper = qcReportMapper;
        this.sessionImportRecordMapper = sessionImportRecordMapper;
        this.collectionSessionMapper = collectionSessionMapper;
        this.acquisitionTaskMapper = acquisitionTaskMapper;
    }

    /**
     * 删除处理 Job 的产出（派生文件+资产+lineage），保留 Job 记录标记为 CLEANED
     */
    @Transactional
    public CleanupResult deleteProcessingJobOutputs(Long jobId) {
        CleanupResult result = new CleanupResult();
        ProcessingJob job = processingJobMapper.selectById(jobId);
        if (job == null) {
            throw new IllegalArgumentException("处理任务不存在: " + jobId);
        }

        // 1. 删除 asset_lineage（该 job 作为来源的）
        List<AssetLineage> lineages = assetLineageMapper.selectList(
                new LambdaQueryWrapper<AssetLineage>().eq(AssetLineage::getJobId, jobId));
        for (AssetLineage lineage : lineages) {
            assetLineageMapper.deleteById(lineage.getId());
        }
        log.info("已删除 {} 条 asset_lineage 记录 (jobId={})", lineages.size(), jobId);

        // 2. 删除 data_asset（由该 job 产生的）
        List<DataAsset> assets = dataAssetMapper.selectList(
                new LambdaQueryWrapper<DataAsset>().eq(DataAsset::getProducedByJobId, jobId));
        for (DataAsset asset : assets) {
            // 3. 删除关联的 data_file（派生文件）+ OSS 对象
            if (asset.getFileId() != null) {
                DataFile file = dataFileMapper.selectById(asset.getFileId());
                if (file != null && "DERIVED".equals(file.getFileRole())) {
                    deleteOssObject(file, result);
                    dataFileMapper.deleteById(file.getId());
                    result.setDeletedFileCount(result.getDeletedFileCount() + 1);
                }
            }
            dataAssetMapper.deleteById(asset.getId());
            result.setDeletedAssetCount(result.getDeletedAssetCount() + 1);
        }
        log.info("已删除 {} 个 data_asset (jobId={})", assets.size(), jobId);

        // 4. 标记 Job 为已清理
        job.setStatus("CLEANED");
        processingJobMapper.updateById(job);

        result.setSummary(String.format("已清除处理任务 #%d 的产出：%d 个资产，%d 个文件，OSS 删除 %d 个",
                jobId, result.getDeletedAssetCount(), result.getDeletedFileCount(), result.getOssDeletedCount()));
        return result;
    }

    /**
     * 删除整个采集会话及其所有关联数据
     */
    @Transactional
    public CleanupResult deleteSession(Long sessionId) {
        CleanupResult result = new CleanupResult();
        CollectionSession session = collectionSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("采集会话不存在: " + sessionId);
        }

        // 1. asset_lineage
        List<AssetLineage> lineages = assetLineageMapper.selectList(
                new LambdaQueryWrapper<AssetLineage>().eq(AssetLineage::getSessionId, sessionId));
        for (AssetLineage lineage : lineages) {
            assetLineageMapper.deleteById(lineage.getId());
        }

        // 2. data_asset
        List<DataAsset> assets = dataAssetMapper.selectList(
                new LambdaQueryWrapper<DataAsset>().eq(DataAsset::getSessionId, sessionId));
        for (DataAsset asset : assets) {
            dataAssetMapper.deleteById(asset.getId());
        }
        result.setDeletedAssetCount(assets.size());

        // 3. qc_report
        List<QcReport> reports = qcReportMapper.selectList(
                new LambdaQueryWrapper<QcReport>().eq(QcReport::getSessionId, sessionId));
        for (QcReport report : reports) {
            qcReportMapper.deleteById(report.getId());
        }
        result.setDeletedReportCount(reports.size());

        // 4. session_import_record
        List<SessionImportRecord> records = sessionImportRecordMapper.selectList(
                new LambdaQueryWrapper<SessionImportRecord>().eq(SessionImportRecord::getSessionRecordId, sessionId));
        for (SessionImportRecord record : records) {
            sessionImportRecordMapper.deleteById(record.getId());
        }
        result.setDeletedImportRecordCount(records.size());

        // 5. processing_job
        List<ProcessingJob> jobs = processingJobMapper.selectList(
                new LambdaQueryWrapper<ProcessingJob>().eq(ProcessingJob::getSessionId, sessionId));
        for (ProcessingJob job : jobs) {
            processingJobMapper.deleteById(job.getId());
        }
        result.setDeletedJobCount(jobs.size());

        // 6. data_file + OSS
        List<DataFile> files = dataFileMapper.selectList(
                new LambdaQueryWrapper<DataFile>().eq(DataFile::getSessionId, sessionId));
        for (DataFile file : files) {
            deleteOssObject(file, result);
            dataFileMapper.deleteById(file.getId());
        }
        result.setDeletedFileCount(files.size());

        // 7. collection_session
        collectionSessionMapper.deleteById(sessionId);

        result.setSummary(String.format("已删除采集会话 #%d：%d 个文件，%d 个资产，%d 个 Job，%d 个报告，OSS 删除 %d 个",
                sessionId, result.getDeletedFileCount(), result.getDeletedAssetCount(),
                result.getDeletedJobCount(), result.getDeletedReportCount(), result.getOssDeletedCount()));
        return result;
    }

    /**
     * 删除采集任务及其下所有会话
     */
    @Transactional
    public CleanupResult deleteTask(Long taskId) {
        CleanupResult result = new CleanupResult();
        AcquisitionTask task = acquisitionTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("采集任务不存在: " + taskId);
        }

        // 1. 查出所有 session 并逐个删除
        List<CollectionSession> sessions = collectionSessionMapper.selectList(
                new LambdaQueryWrapper<CollectionSession>().eq(CollectionSession::getTaskId, taskId));
        for (CollectionSession session : sessions) {
            CleanupResult sessionResult = deleteSession(session.getId());
            result.setDeletedFileCount(result.getDeletedFileCount() + sessionResult.getDeletedFileCount());
            result.setDeletedAssetCount(result.getDeletedAssetCount() + sessionResult.getDeletedAssetCount());
            result.setDeletedJobCount(result.getDeletedJobCount() + sessionResult.getDeletedJobCount());
            result.setDeletedReportCount(result.getDeletedReportCount() + sessionResult.getDeletedReportCount());
            result.setDeletedImportRecordCount(result.getDeletedImportRecordCount() + sessionResult.getDeletedImportRecordCount());
            result.setOssDeletedCount(result.getOssDeletedCount() + sessionResult.getOssDeletedCount());
            result.getOssFailedKeys().addAll(sessionResult.getOssFailedKeys());
        }
        result.setDeletedSessionCount(sessions.size());

        // 2. 清理任务级 data_file（没有 session 的文件）+ OSS
        List<DataFile> orphanFiles = dataFileMapper.selectList(
                new LambdaQueryWrapper<DataFile>()
                        .eq(DataFile::getTaskId, taskId)
                        .isNull(DataFile::getSessionId));
        for (DataFile file : orphanFiles) {
            deleteOssObject(file, result);
            dataFileMapper.deleteById(file.getId());
        }
        result.setDeletedFileCount(result.getDeletedFileCount() + orphanFiles.size());

        // 3. 清理任务级 data_asset（没有 session 的资产）
        List<DataAsset> orphanAssets = dataAssetMapper.selectList(
                new LambdaQueryWrapper<DataAsset>()
                        .eq(DataAsset::getTaskId, taskId)
                        .isNull(DataAsset::getSessionId));
        for (DataAsset asset : orphanAssets) {
            dataAssetMapper.deleteById(asset.getId());
        }
        result.setDeletedAssetCount(result.getDeletedAssetCount() + orphanAssets.size());

        // 4. 清理任务级 asset_lineage
        List<AssetLineage> orphanLineages = assetLineageMapper.selectList(
                new LambdaQueryWrapper<AssetLineage>()
                        .eq(AssetLineage::getTaskId, taskId)
                        .isNull(AssetLineage::getSessionId));
        for (AssetLineage lineage : orphanLineages) {
            assetLineageMapper.deleteById(lineage.getId());
        }

        // 5. 删除 task
        acquisitionTaskMapper.deleteById(taskId);

        result.setSummary(String.format("已删除采集任务 #%d 及 %d 个会话：%d 个文件，%d 个资产，OSS 删除 %d 个",
                taskId, result.getDeletedSessionCount(), result.getDeletedFileCount(),
                result.getDeletedAssetCount(), result.getOssDeletedCount()));
        return result;
    }

    private void deleteOssObject(DataFile file, CleanupResult result) {
        if (file.getBucketName() == null || file.getObjectKey() == null
                || file.getBucketName().isBlank() || file.getObjectKey().isBlank()) {
            return;
        }
        try {
            storageRouter.defaultService().deleteObject(file.getBucketName(), file.getObjectKey());
            result.setOssDeletedCount(result.getOssDeletedCount() + 1);
        } catch (Exception e) {
            log.warn("OSS 删除失败: bucket={}, key={}, error={}",
                    file.getBucketName(), file.getObjectKey(), e.getMessage());
            result.addOssFailedKey(file.getObjectKey());
        }
    }
}
