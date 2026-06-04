const STATUS_LABELS: Record<string, string> = {
  ACTIVE: "进行中",
  CREATED: "已创建",
  ERROR: "异常",
  FAILED: "失败",
  IMPORTED: "已导入",
  MISSING_REQUIRED_ASSETS: "缺少必需资产",
  PASSED: "通过",
  PENDING: "待处理",
  PLAYABLE: "可播放",
  QC_FAILED: "质检失败",
  QC_PASSED: "质检通过",
  QC_WARNING: "质检警告",
  READY: "已就绪",
  REGISTERED: "已登记",
  RUNNING: "处理中",
  SUCCESS: "成功",
  UPLOADED: "已上传",
  UPLOADING: "上传中",
  WAITING: "等待中",
  WARNING: "警告",
};

const STATUS_TONES: Record<string, string> = {
  ACTIVE: "bg-emerald-50 text-emerald-700 border-emerald-200",
  CREATED: "bg-slate-50 text-slate-700 border-slate-200",
  ERROR: "bg-rose-50 text-rose-700 border-rose-200",
  FAILED: "bg-rose-50 text-rose-700 border-rose-200",
  IMPORTED: "bg-cyan-50 text-cyan-700 border-cyan-200",
  MISSING_REQUIRED_ASSETS: "bg-amber-50 text-amber-700 border-amber-200",
  PASSED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  PENDING: "bg-slate-50 text-slate-700 border-slate-200",
  PLAYABLE: "bg-violet-50 text-violet-700 border-violet-200",
  QC_FAILED: "bg-rose-50 text-rose-700 border-rose-200",
  QC_PASSED: "bg-emerald-50 text-emerald-700 border-emerald-200",
  QC_WARNING: "bg-amber-50 text-amber-700 border-amber-200",
  READY: "bg-emerald-50 text-emerald-700 border-emerald-200",
  REGISTERED: "bg-cyan-50 text-cyan-700 border-cyan-200",
  RUNNING: "bg-amber-50 text-amber-700 border-amber-200",
  SUCCESS: "bg-emerald-50 text-emerald-700 border-emerald-200",
  UPLOADED: "bg-blue-50 text-blue-700 border-blue-200",
  UPLOADING: "bg-amber-50 text-amber-700 border-amber-200",
  WAITING: "bg-slate-50 text-slate-700 border-slate-200",
  WARNING: "bg-amber-50 text-amber-700 border-amber-200",
};

export function formatDateTime(value?: string): string {
  if (!value) {
    return "-";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date);
}

export function formatFileSize(size?: number | null): string {
  if (!size && size !== 0) {
    return "-";
  }
  if (size < 1024) {
    return `${size} B`;
  }
  if (size < 1024 * 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  if (size < 1024 * 1024 * 1024) {
    return `${(size / (1024 * 1024)).toFixed(2)} MB`;
  }
  return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export function statusTone(status?: string): string {
  if (!status) {
    return "bg-slate-50 text-slate-700 border-slate-200";
  }
  return STATUS_TONES[status] ?? "bg-slate-50 text-slate-700 border-slate-200";
}

export function formatStatusLabel(status?: string): string {
  if (!status) {
    return "-";
  }
  return STATUS_LABELS[status] ?? status;
}

export function formatEnumLabel(value?: string): string {
  if (!value) {
    return "-";
  }
  return value;
}

export function formatSourceType(value?: string): string {
  switch (value) {
    case "upload":
      return "手动上传";
    case "acquisition_sync":
      return "采集同步";
    case "derived":
      return "派生结果";
    case "external_register":
      return "外部登记";
    default:
      return value || "-";
  }
}
