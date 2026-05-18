const STATUS_LABELS: Record<string, string> = {
  CREATED: "已创建",
  PENDING: "待处理",
  RUNNING: "处理中",
  UPLOADING: "上传中",
  READY: "已就绪",
  MISSING_REQUIRED_ASSETS: "缺少必需资产",
  SUCCESS: "成功",
  FAILED: "失败",
  WARNING: "告警",
  PASSED: "通过",
  QC_PASSED: "质检通过",
  QC_WARNING: "存在告警",
  QC_FAILED: "质检失败"
};

const STATUS_TONES: Record<string, string> = {
  CREATED: "bg-slate-100 text-slate-700 ring-slate-200",
  PENDING: "bg-slate-100 text-slate-700 ring-slate-200",
  RUNNING: "bg-sky-100 text-sky-700 ring-sky-200",
  UPLOADING: "bg-sky-100 text-sky-700 ring-sky-200",
  READY: "bg-emerald-100 text-emerald-700 ring-emerald-200",
  MISSING_REQUIRED_ASSETS: "bg-amber-100 text-amber-700 ring-amber-200",
  SUCCESS: "bg-emerald-100 text-emerald-700 ring-emerald-200",
  PASSED: "bg-emerald-100 text-emerald-700 ring-emerald-200",
  QC_PASSED: "bg-emerald-100 text-emerald-700 ring-emerald-200",
  WARNING: "bg-amber-100 text-amber-700 ring-amber-200",
  QC_WARNING: "bg-amber-100 text-amber-700 ring-amber-200",
  FAILED: "bg-rose-100 text-rose-700 ring-rose-200",
  QC_FAILED: "bg-rose-100 text-rose-700 ring-rose-200"
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
    minute: "2-digit"
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
  return `${(size / (1024 * 1024)).toFixed(2)} MB`;
}

export function statusTone(status?: string): string {
  if (!status) {
    return "bg-slate-100 text-slate-700 ring-slate-200";
  }
  return STATUS_TONES[status] ?? "bg-slate-100 text-slate-700 ring-slate-200";
}

export function formatStatusLabel(status?: string): string {
  if (!status) {
    return "-";
  }
  return STATUS_LABELS[status] ?? status.split("_").join(" ");
}

export function formatEnumLabel(value?: string): string {
  if (!value) {
    return "-";
  }
  return value.split("_").join(" ");
}
