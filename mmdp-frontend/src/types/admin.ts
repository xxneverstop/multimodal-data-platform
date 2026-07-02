export interface CleanupResult {
  deletedFileCount: number;
  deletedAssetCount: number;
  deletedJobCount: number;
  deletedReportCount: number;
  deletedImportRecordCount: number;
  deletedSessionCount: number;
  ossDeletedCount: number;
  ossFailedKeys: string[];
  summary: string;
}
