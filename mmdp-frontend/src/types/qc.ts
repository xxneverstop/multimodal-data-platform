export interface QcCheck {
  name: string;
  status: string;
  message: string;
}

export interface StructuredQcReport {
  fileInfo?: Record<string, unknown>;
  detectedFormat?: string;
  summary?: string;
  overallStatus?: string;
  checks?: QcCheck[];
  sample?: Record<string, unknown>;
  warnings?: string[];
  errors?: string[];
  [key: string]: unknown;
}

export interface QcReportResponse {
  id: number;
  taskId: number;
  fileId: number;
  qcStatus: string;
  summary: string;
  reportJson: StructuredQcReport;
  createdAt: string;
}
