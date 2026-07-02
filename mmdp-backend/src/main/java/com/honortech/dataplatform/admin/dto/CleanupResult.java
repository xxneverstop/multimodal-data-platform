package com.honortech.dataplatform.admin.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据清理结果摘要
 */
public class CleanupResult {

    private int deletedFileCount;
    private int deletedAssetCount;
    private int deletedJobCount;
    private int deletedReportCount;
    private int deletedImportRecordCount;
    private int deletedSessionCount;
    private int ossDeletedCount;
    private final List<String> ossFailedKeys = new ArrayList<>();
    private String summary;

    public int getDeletedFileCount() {
        return deletedFileCount;
    }

    public void setDeletedFileCount(int deletedFileCount) {
        this.deletedFileCount = deletedFileCount;
    }

    public int getDeletedAssetCount() {
        return deletedAssetCount;
    }

    public void setDeletedAssetCount(int deletedAssetCount) {
        this.deletedAssetCount = deletedAssetCount;
    }

    public int getDeletedJobCount() {
        return deletedJobCount;
    }

    public void setDeletedJobCount(int deletedJobCount) {
        this.deletedJobCount = deletedJobCount;
    }

    public int getDeletedReportCount() {
        return deletedReportCount;
    }

    public void setDeletedReportCount(int deletedReportCount) {
        this.deletedReportCount = deletedReportCount;
    }

    public int getDeletedImportRecordCount() {
        return deletedImportRecordCount;
    }

    public void setDeletedImportRecordCount(int deletedImportRecordCount) {
        this.deletedImportRecordCount = deletedImportRecordCount;
    }

    public int getDeletedSessionCount() {
        return deletedSessionCount;
    }

    public void setDeletedSessionCount(int deletedSessionCount) {
        this.deletedSessionCount = deletedSessionCount;
    }

    public int getOssDeletedCount() {
        return ossDeletedCount;
    }

    public void setOssDeletedCount(int ossDeletedCount) {
        this.ossDeletedCount = ossDeletedCount;
    }

    public List<String> getOssFailedKeys() {
        return ossFailedKeys;
    }

    public void addOssFailedKey(String key) {
        this.ossFailedKeys.add(key);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
