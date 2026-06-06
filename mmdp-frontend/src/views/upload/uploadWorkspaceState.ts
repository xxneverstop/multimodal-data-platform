import { reactive, ref } from "vue";

export type UploadItemState = "idle" | "initiating" | "uploading" | "uploaded" | "completing" | "success" | "complete_failed" | "error";
export type UploadPhase = "upload" | "complete";
export type IngestModeKey = "files" | "archive" | "external";
export type AssetTypeOptionKey = "video" | "csv" | "smpl" | "image" | "archive" | "external-path" | "result" | "other";

export interface ExternalPreviewItem {
  key: string;
  path: string;
  storageType: string;
  displayName: string;
  remark: string;
  assetTypeKey: AssetTypeOptionKey;
}

export interface UploadStateRecord {
  progress: number;
  state: UploadItemState;
  phase?: UploadPhase;
  fileId?: number;
  message?: string;
}

const defaultExternalDraftForm = () => ({
  path: "",
  storageType: "OSS",
  displayName: "",
  remark: "",
});

const defaultModeAssetTypeSelections = (): Record<IngestModeKey, AssetTypeOptionKey> => ({
  files: "video",
  archive: "archive",
  external: "external-path",
});

export const uploadWorkspaceState = {
  submitting: ref(false),
  message: ref(""),
  uploadError: ref(false),
  batchSessionId: ref<number | null>(null),
  batchSessionCode: ref(""),
  activeIngestMode: ref<IngestModeKey>("files"),
  externalPreviewItems: ref<ExternalPreviewItem[]>([]),
  form: reactive({
    taskId: "",
    sessionId: "",
    uploadMode: "files" as "files" | "archive",
    selectedFiles: [] as File[],
    archiveFiles: [] as File[],
    remark: "",
  }),
  externalDraftForm: reactive(defaultExternalDraftForm()),
  modeAssetTypeSelections: reactive(defaultModeAssetTypeSelections()),
  uploadStates: reactive<Record<string, UploadStateRecord>>({}),
};

export function hasWorkspaceEntries(): boolean {
  return Boolean(
    uploadWorkspaceState.form.selectedFiles.length ||
      uploadWorkspaceState.form.archiveFiles.length ||
      uploadWorkspaceState.externalPreviewItems.value.length ||
      uploadWorkspaceState.form.remark.trim() ||
      uploadWorkspaceState.externalDraftForm.path.trim() ||
      uploadWorkspaceState.externalDraftForm.displayName.trim() ||
      uploadWorkspaceState.externalDraftForm.remark.trim() ||
      Object.keys(uploadWorkspaceState.uploadStates).length,
  );
}

export function hasActiveItems(): boolean {
  return Object.values(uploadWorkspaceState.uploadStates).some((state) =>
    ["initiating", "uploading", "uploaded", "completing"].includes(state.state),
  );
}

export function clearWorkspace(options?: {
  preserveTask?: boolean;
  preserveSession?: boolean;
  showMessage?: boolean;
}) {
  const preserveTask = options?.preserveTask ?? true;
  const preserveSession = options?.preserveSession ?? true;
  const showMessage = options?.showMessage ?? true;

  const currentTaskId = uploadWorkspaceState.form.taskId;
  const currentSessionId = uploadWorkspaceState.form.sessionId;

  uploadWorkspaceState.form.taskId = preserveTask ? currentTaskId : "";
  uploadWorkspaceState.form.sessionId = preserveTask && preserveSession ? currentSessionId : "";
  uploadWorkspaceState.form.uploadMode = "files";
  uploadWorkspaceState.form.selectedFiles = [];
  uploadWorkspaceState.form.archiveFiles = [];
  uploadWorkspaceState.form.remark = "";

  uploadWorkspaceState.activeIngestMode.value = "files";
  uploadWorkspaceState.externalPreviewItems.value = [];
  Object.assign(uploadWorkspaceState.externalDraftForm, defaultExternalDraftForm());

  const nextSelections = defaultModeAssetTypeSelections();
  uploadWorkspaceState.modeAssetTypeSelections.files = nextSelections.files;
  uploadWorkspaceState.modeAssetTypeSelections.archive = nextSelections.archive;
  uploadWorkspaceState.modeAssetTypeSelections.external = nextSelections.external;

  Object.keys(uploadWorkspaceState.uploadStates).forEach((key) => {
    delete uploadWorkspaceState.uploadStates[key];
  });

  uploadWorkspaceState.batchSessionId.value = null;
  uploadWorkspaceState.batchSessionCode.value = "";
  uploadWorkspaceState.uploadError.value = false;
  uploadWorkspaceState.submitting.value = false;
  uploadWorkspaceState.message.value = showMessage ? "当前上传工作台已清空。" : "";
}
