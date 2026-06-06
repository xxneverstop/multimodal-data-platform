<template>
  <div class="space-y-4">
    <PageHeader
      eyebrow="数据接入"
      title="数据上传"
      description="普通文件上传保留原样；标准 Session 采用目录导入模式，由 manifest.json 驱动 Session 落库。"
      :meta="headerMeta"
    />

    <div class="grid gap-4 xl:grid-cols-[minmax(0,1.6fr)_320px]">
      <form class="space-y-4" @submit.prevent="handleSubmit">
        <PageCard title="上传工作台" description="先选择 Task，再选择普通文件或标准 Session 目录。">
          <div class="space-y-4">
            <section class="grid gap-3 lg:grid-cols-[minmax(220px,1fr)_minmax(220px,1fr)_minmax(0,1.2fr)]">
              <label class="block">
                <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">
                  归属 Task <span class="text-[var(--color-danger-700)]">*</span>
                </span>
                <select v-model="form.taskId" required class="app-input app-input-compact" @change="onTaskChange">
                  <option value="" disabled>请选择 Task</option>
                  <option v-for="task in tasks" :key="task.id" :value="String(task.id)">
                    {{ task.taskName }} ({{ task.taskCode }})
                  </option>
                </select>
              </label>

              <label v-if="activeIngestMode !== 'archive'" class="block">
                <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">关联 Session</span>
                <select v-model="form.sessionId" class="app-input app-input-compact">
                  <option value="">自动创建新 Session</option>
                  <option v-for="sess in sessions" :key="sess.id" :value="String(sess.id)">
                    {{ sess.sessionCode ?? sess.sessionId }} - {{ sess.actionName }}
                  </option>
                </select>
              </label>

              <div class="block">
                <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">接入方式</span>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="option in ingestModeOptions"
                    :key="option.key"
                    type="button"
                    class="rounded-[10px] border px-3 py-1.5 text-xs font-medium transition"
                    :class="activeIngestMode === option.key ? option.activeClass : option.inactiveClass"
                    @click="setActiveIngestMode(option.key)"
                  >
                    {{ option.title }}
                  </button>
                </div>
              </div>
            </section>

            <section class="rounded-[16px] border border-[var(--color-border-soft)] bg-white p-4">
              <template v-if="activeIngestMode === 'files'">
                <div
                  class="rounded-[16px] border border-dashed border-[var(--color-border-default)] bg-[var(--color-surface-muted)] px-5 py-8 text-center transition hover:border-[var(--color-brand-500)]"
                  @dragover.prevent
                  @drop.prevent="onDropFiles"
                  @click="triggerFileInput"
                >
                  <p class="text-sm font-semibold text-[var(--color-text-primary)]">拖拽文件到这里，或点击选择文件</p>
                  <p class="mt-2 text-xs leading-5 text-[var(--color-text-secondary)]">
                    用于零散文件、补充文件、非标准数据，保持原有 OSS 直传链路不变。
                  </p>
                  <input ref="fileInputRef" type="file" multiple class="hidden" @change="onFilesSelected" />
                </div>
              </template>

              <template v-else-if="activeIngestMode === 'archive'">
                <div class="grid gap-3 lg:grid-cols-[minmax(0,1fr)_280px]">
                  <div class="rounded-[16px] border border-dashed border-amber-200 bg-amber-50/60 px-5 py-6">
                    <div class="space-y-3 text-center">
                      <p class="text-sm font-semibold text-[var(--color-text-primary)]">选择标准 Session 目录</p>
                      <p class="text-xs leading-5 text-[var(--color-text-secondary)]">
                        目录必须包含根级 <code>manifest.json</code>、<code>sources/</code>，以及可选的 <code>artifacts/</code>。
                      </p>
                      <label class="inline-flex cursor-pointer rounded-[10px] border border-[var(--color-border-default)] bg-white px-3 py-1.5 text-xs font-medium text-[var(--color-text-primary)] hover:bg-slate-50">
                        选择目录
                        <input
                          ref="directoryInputRef"
                          type="file"
                          multiple
                          webkitdirectory
                          directory
                          class="hidden"
                          @change="onDirectorySelected"
                        />
                      </label>
                      <p v-if="!directoryUploadSupported" class="text-xs text-[var(--color-danger-700)]">
                        当前浏览器不支持目录上传，请使用桌面版 Chrome / Edge。
                      </p>
                    </div>
                  </div>

                  <div class="rounded-[16px] border border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] px-3 py-3">
                    <div class="text-xs font-medium tracking-[0.06em] text-[var(--color-text-tertiary)] uppercase">目录规范</div>
                    <div class="mt-2 rounded-[12px] bg-white px-3 py-3 font-mono text-[11px] leading-5 text-[var(--color-text-secondary)]">
                      <div>session_dir/</div>
                      <div>├─ manifest.json</div>
                      <div>├─ sources/</div>
                      <div>└─ artifacts/</div>
                    </div>
                    <div class="mt-3 text-xs leading-5 text-[var(--color-text-secondary)]">
                      前端会把目录内每个文件直传到临时导入区，并在全部上传完成后调用 finalize 完成 Session 收口。
                    </div>
                  </div>
                </div>
              </template>

              <template v-else>
                <div class="space-y-3">
                  <div class="text-sm font-semibold text-[var(--color-text-primary)]">外部资产登记</div>
                  <div class="grid gap-3 md:grid-cols-2">
                    <label class="block md:col-span-2">
                      <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">外部路径 / Object Key</span>
                      <input v-model="externalDraftForm.path" class="app-input app-input-compact" placeholder="例如：oss://bucket/session_001/video/main.mp4" />
                    </label>

                    <label class="block">
                      <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">存储类型</span>
                      <select v-model="externalDraftForm.storageType" class="app-input app-input-compact">
                        <option v-for="item in storageTypeOptions" :key="item" :value="item">{{ item }}</option>
                      </select>
                    </label>

                    <label class="block">
                      <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">显示名称</span>
                      <input v-model="externalDraftForm.displayName" class="app-input app-input-compact" placeholder="例如：session_001_result" />
                    </label>

                    <label class="block md:col-span-2">
                      <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">备注</span>
                      <textarea v-model="externalDraftForm.remark" rows="2" class="app-input resize-y" placeholder="记录来源、结构说明或补充信息" />
                    </label>
                  </div>

                  <div class="flex flex-wrap items-center gap-3">
                    <BaseButton variant="soft" tone="upload" type="button" :disabled="!canAddExternalPreview" @click="addExternalPreviewItem">
                      添加到预览
                    </BaseButton>
                    <span class="text-xs text-[var(--color-text-secondary)]">当前仅保留页面入口和预览，不接真实提交。</span>
                  </div>
                </div>
              </template>
            </section>

            <section class="overflow-hidden rounded-[16px] border border-[var(--color-border-soft)] bg-white">
              <div class="flex items-center justify-between gap-3 border-b border-[var(--color-border-soft)] px-4 py-3">
                <div>
                  <h3 class="text-sm font-semibold text-[var(--color-text-primary)]">待处理清单</h3>
                  <p class="mt-1 text-xs text-[var(--color-text-secondary)]">这里会展示当前模式下待上传或待导入的内容。</p>
                </div>
                <div class="flex flex-wrap gap-2 text-xs">
                  <span class="app-pill border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] text-[var(--color-text-secondary)]">
                    {{ previewItemCount }} 项
                  </span>
                  <span class="app-pill border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] text-[var(--color-text-secondary)]">
                    {{ previewTotalSizeLabel }}
                  </span>
                </div>
              </div>

              <div v-if="pendingAssetItems.length" class="overflow-x-auto">
                <table class="min-w-full text-left text-sm">
                  <thead class="app-table-head text-[11px] tracking-[0.08em] text-[var(--color-text-tertiary)] uppercase">
                    <tr>
                      <th class="app-table-th">文件 / 路径</th>
                      <th class="app-table-th">模式</th>
                      <th class="app-table-th">状态</th>
                      <th class="app-table-th">大小</th>
                      <th class="app-table-th text-right">操作</th>
                    </tr>
                  </thead>
                  <tbody class="divide-y divide-[var(--color-border-soft)] bg-white">
                    <tr v-for="item in pendingAssetItems" :key="item.key">
                      <td class="px-4 py-3 align-top">
                        <div class="max-w-[360px] truncate font-medium text-[var(--color-text-primary)]" :title="item.name">{{ item.name }}</div>
                        <div v-if="item.secondaryText" class="mt-1 text-xs text-[var(--color-text-secondary)]">{{ item.secondaryText }}</div>
                      </td>
                      <td class="px-4 py-3 align-top text-[var(--color-text-secondary)]">{{ item.ingestModeLabel }}</td>
                      <td class="px-4 py-3 align-top">
                        <div class="min-w-[180px] space-y-2">
                          <div class="flex items-center gap-2">
                            <span class="app-pill" :class="item.stateBadgeClass">{{ item.stateLabel }}</span>
                            <span v-if="item.showPercent" class="text-xs font-medium text-[var(--color-text-secondary)]">{{ item.progress }}%</span>
                          </div>
                          <div v-if="item.showProgressBar" class="space-y-1">
                            <div class="h-1.5 overflow-hidden rounded-full bg-[var(--color-surface-muted)]">
                              <div class="h-full rounded-full transition-all duration-300" :class="item.progressBarClass" :style="{ width: `${item.progress}%` }" />
                            </div>
                            <div class="text-[11px] text-[var(--color-text-tertiary)]">{{ item.progressHint }}</div>
                          </div>
                        </div>
                      </td>
                      <td class="px-4 py-3 align-top text-[var(--color-text-secondary)]">{{ item.sizeLabel }}</td>
                      <td class="px-4 py-3 align-top text-right">
                        <button
                          v-if="item.canRemove"
                          type="button"
                          class="text-xs font-medium text-[var(--color-text-tertiary)] hover:text-[var(--color-danger-700)]"
                          @click="item.remove()"
                        >
                          移除
                        </button>
                        <span v-else class="text-[11px] text-[var(--color-text-tertiary)]">{{ item.removeHint }}</span>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <div v-else class="px-4 py-8 text-center text-sm text-[var(--color-text-secondary)]">
                暂无待处理内容。
              </div>

              <div class="border-t border-[var(--color-border-soft)] px-4 py-3">
                <label class="block">
                  <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">补充说明</span>
                  <input v-model="form.remark" class="app-input app-input-compact" placeholder="可填写本次上传或导入的说明" />
                </label>
              </div>

              <div class="flex flex-col gap-3 border-t border-[var(--color-border-soft)] bg-[rgba(255,255,255,0.97)] px-4 py-4 md:flex-row md:items-center md:justify-between">
                <div class="space-y-2">
                  <div v-if="message" :class="messageBannerClass" class="rounded-[12px] px-3 py-2 text-xs font-medium">
                    {{ message }}
                  </div>
                  <div v-else class="text-xs text-[var(--color-text-secondary)]">
                    {{ footerSummaryText }}
                  </div>
                </div>

                <div class="flex flex-wrap items-center justify-end gap-2">
                  <BaseButton variant="ghost" type="button" :disabled="!canClearCurrentMode" @click="requestClearWorkspace">
                    清空当前工作台
                  </BaseButton>
                  <BaseButton variant="primary" type="submit" :disabled="!canSubmitCurrentMode">
                    {{ submitButtonText }}
                  </BaseButton>
                </div>
              </div>
            </section>
          </div>
        </PageCard>
      </form>

      <div class="space-y-4">
        <PageCard title="归属信息" description="Task 是业务容器；标准 Session 目录导入不允许手选已有 Session。">
          <div v-if="selectedTask" class="space-y-2 text-sm">
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">Task</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ selectedTask.taskName }}</span>
            </div>
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">Task Code</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ selectedTask.taskCode }}</span>
            </div>
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">被试编号</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ selectedTask.subjectCode || "-" }}</span>
            </div>
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">Profile</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ selectedTask.profileName || "-" }}</span>
            </div>
            <div v-if="batchSessionCode" class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">最近 Session</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ batchSessionCode }}</span>
            </div>
          </div>
          <div v-else class="text-xs leading-5 text-[var(--color-text-secondary)]">请先选择 Task。</div>
        </PageCard>

        <PageCard title="导入结果" description="目录导入完成后，会展示 manifest 解析与平台 Session 信息。">
          <div class="space-y-2 text-sm">
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">模式</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ activeIngestModeOption.title }}</span>
            </div>
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">localSessionId</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ importResult?.localSessionId ?? "-" }}</span>
            </div>
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">platformSessionCode</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ importResult?.platformSessionCode ?? (batchSessionCode || "-") }}</span>
            </div>
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">状态</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ importResult?.status ?? "-" }}</span>
            </div>
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">幂等命中</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ importResult?.existing ? "是" : "否" }}</span>
            </div>
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">创建文件数</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ importResult?.createdFileCount ?? "-" }}</span>
            </div>
            <div class="flex items-start justify-between gap-3">
              <span class="text-[var(--color-text-tertiary)]">创建资产数</span>
              <span class="text-right font-semibold text-[var(--color-text-primary)]">{{ importResult?.createdAssetCount ?? "-" }}</span>
            </div>
          </div>
        </PageCard>
      </div>
    </div>

    <AppDialog
      :open="clearDialogOpen"
      size="sm"
      title="清空当前工作台"
      :description="clearDialogDescription"
      confirm-text="确认清空"
      @close="clearDialogOpen = false"
      @confirm="confirmClearWorkspace"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { fetchAcquisitionList } from "@/api/platform";
import {
  fetchTaskSessions,
  finalizeSessionImport,
  type FinalizeSessionImportResponse,
  type FinalizeSessionImportUploadedFile,
  type SessionResponse,
} from "@/api/sessions";
import AppDialog from "@/components/AppDialog.vue";
import BaseButton from "@/components/BaseButton.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import type { TaskResponse } from "@/types/task";
import {
  DirectUploadCompletionError,
  directUploadSessionImportFileToOss,
  directUploadToOss,
  inferAssetTypeForFile,
  retryDirectUploadCompletion,
  type DirectUploadStage,
} from "@/utils/ossDirectUpload";
import {
  clearWorkspace,
  hasActiveItems,
  hasWorkspaceEntries,
  type IngestModeKey,
  type UploadItemState,
  type UploadPhase,
  type UploadStateRecord,
  uploadWorkspaceState,
} from "@/views/upload/uploadWorkspaceState";

interface PendingAssetItem {
  key: string;
  name: string;
  secondaryText?: string;
  ingestModeLabel: string;
  sizeLabel: string;
  stateLabel: string;
  stateBadgeClass: string;
  progress: number;
  showPercent: boolean;
  showProgressBar: boolean;
  progressBarClass: string;
  progressHint: string;
  canRemove: boolean;
  removeHint?: string;
  remove: () => void;
}

interface UploadEntryProgressItem {
  key: string;
  name: string;
  size: number;
  state?: UploadStateRecord;
}

interface DirectoryManifestSummary {
  localSessionId: string | null;
  profileCode: string | null;
  subjectCode: string | null;
  actionName: string | null;
  sourceCount: number;
}

const tasks = ref<TaskResponse[]>([]);
const sessions = ref<SessionResponse[]>([]);
const fileInputRef = ref<HTMLInputElement | null>(null);
const directoryInputRef = ref<HTMLInputElement | null>(null);
const abortControllers = new Map<string, AbortController>();
const clearDialogOpen = ref(false);
const clearDialogVariant = ref<"active" | "normal">("normal");
const importResult = ref<FinalizeSessionImportResponse | null>(null);
const manifestSummary = ref<DirectoryManifestSummary | null>(null);

const {
  submitting,
  message,
  uploadError,
  batchSessionId,
  batchSessionCode,
  activeIngestMode,
  externalPreviewItems,
  form,
  externalDraftForm,
  uploadStates,
} = uploadWorkspaceState;

const ingestModeOptions = [
  {
    key: "files" as IngestModeKey,
    title: "普通文件上传",
    activeClass: "border-[var(--color-brand-500)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]",
    inactiveClass: "border-[var(--color-border-default)] bg-white text-[var(--color-text-secondary)] hover:border-[var(--color-brand-200)]",
  },
  {
    key: "archive" as IngestModeKey,
    title: "标准 Session 目录导入",
    activeClass: "border-amber-400 bg-amber-50 text-amber-800",
    inactiveClass: "border-[var(--color-border-default)] bg-white text-[var(--color-text-secondary)] hover:border-amber-200",
  },
  {
    key: "external" as IngestModeKey,
    title: "外部资产登记",
    activeClass: "border-emerald-400 bg-emerald-50 text-emerald-800",
    inactiveClass: "border-[var(--color-border-default)] bg-white text-[var(--color-text-secondary)] hover:border-emerald-200",
  },
];

const storageTypeOptions = ["OSS", "MinIO", "NAS", "Server Path", "Other"];

const activeIngestModeOption = computed(
  () => ingestModeOptions.find((item) => item.key === activeIngestMode.value) ?? ingestModeOptions[0],
);
const selectedTask = computed(() => tasks.value.find((task) => String(task.id) === form.taskId) ?? null);
const selectedSession = computed(() => sessions.value.find((session) => String(session.id) === form.sessionId) ?? null);
const directoryUploadSupported = computed(() => typeof HTMLInputElement !== "undefined" && "webkitdirectory" in document.createElement("input"));

const archiveEntries = computed<UploadEntryProgressItem[]>(() =>
  form.archiveFiles.map((file, index) => {
    const relativePath = getDirectoryRelativePath(file);
    const key = buildArchiveKey(file, index);
    return { key, name: relativePath, size: file.size, state: uploadStates[key] };
  }),
);

const uploadEntryItems = computed<UploadEntryProgressItem[]>(() => {
  if (activeIngestMode.value === "archive") return archiveEntries.value;
  if (activeIngestMode.value !== "files") return [];
  return form.selectedFiles.map((file, index) => {
    const key = buildFileKey(file, index);
    return { key, name: file.name, size: file.size, state: uploadStates[key] };
  });
});

const previewItemCount = computed(() => {
  if (activeIngestMode.value === "external") return externalPreviewItems.value.length;
  return uploadEntryItems.value.length;
});

const previewTotalBytes = computed(() => uploadEntryItems.value.reduce((total, item) => total + item.size, 0));
const previewTotalSizeLabel = computed(() => activeIngestMode.value === "external" ? "外部路径预览" : formatSize(previewTotalBytes.value));

const submittableEntryItems = computed(() =>
  uploadEntryItems.value.filter((item) => {
    const stateName = item.state?.state;
    return stateName == null || stateName === "idle" || stateName === "error";
  }),
);

const canSubmit = computed(() => {
  if (!form.taskId || activeIngestMode.value === "external") return false;
  if (activeIngestMode.value === "archive" && !directoryUploadSupported.value) return false;
  return submittableEntryItems.value.length > 0;
});

const modeUploadingCount = computed(() =>
  uploadEntryItems.value.filter((item) => item.state && ["initiating", "uploading"].includes(item.state.state)).length,
);
const modeCompletingCount = computed(() =>
  uploadEntryItems.value.filter((item) => item.state && ["uploaded", "completing"].includes(item.state.state)).length,
);

const totalProgressBytes = computed(() => uploadEntryItems.value.reduce((total, item) => total + item.size, 0));
const uploadedBytes = computed(() =>
  uploadEntryItems.value.reduce((total, item) => total + (item.size * (item.state?.progress ?? 0)) / 100, 0),
);
const totalProgressPercent = computed(() => {
  if (!totalProgressBytes.value) return 0;
  return Math.max(0, Math.min(100, Math.floor((uploadedBytes.value / totalProgressBytes.value) * 100)));
});

const headerMeta = computed(() => [
  { label: "模式", value: activeIngestModeOption.value.title },
  { label: "Task", value: selectedTask.value?.taskCode ?? "未选择" },
  { label: "待处理", value: `${previewItemCount.value} 项` },
]);

const showOverallProgress = computed(() =>
  activeIngestMode.value !== "external" && uploadEntryItems.value.some((item) => item.state && item.state.state !== "idle"),
);

const footerSummaryText = computed(() => {
  if (activeIngestMode.value === "archive") {
    if (manifestSummary.value) {
      return `目录导入已识别 localSessionId=${manifestSummary.value.localSessionId ?? "-"}，sources=${manifestSummary.value.sourceCount}。`;
    }
    return "目录导入会先直传到临时导入区，再调用 finalize 完成 Session 收口。";
  }
  if (activeIngestMode.value === "files") {
    return "普通文件上传会沿用现有 OSS 直传与文件登记能力。";
  }
  return "外部资产登记当前仅保留预览入口。";
});

const submitButtonText = computed(() => {
  if (activeIngestMode.value === "external") return "暂不提交";
  if (modeUploadingCount.value > 0) return "上传中...";
  if (modeCompletingCount.value > 0 || submitting.value) return "处理中...";
  return activeIngestMode.value === "archive" ? "开始目录导入" : "提交上传";
});

const canSubmitCurrentMode = computed(() => !submitting.value && canSubmit.value);
const canAddExternalPreview = computed(() => Boolean(externalDraftForm.path.trim() && externalDraftForm.displayName.trim()));
const canClearCurrentMode = computed(() => hasWorkspaceEntries());
const messageBannerClass = computed(() =>
  uploadError.value
    ? "border border-[var(--color-danger-100)] bg-[var(--color-danger-100)] text-[var(--color-danger-700)]"
    : "border border-[var(--color-success-100)] bg-[var(--color-success-100)] text-[var(--color-success-700)]",
);

const clearDialogDescription = computed(() =>
  clearDialogVariant.value === "active"
    ? "当前仍有文件正在上传或收口，清空后将无法继续跟踪本轮进度。确定要清空吗？"
    : "确定清空当前上传工作台吗？",
);

const pendingAssetItems = computed<PendingAssetItem[]>(() => {
  if (activeIngestMode.value === "external") {
    return externalPreviewItems.value.map((item) => ({
      key: item.key,
      name: item.displayName,
      secondaryText: `${item.storageType} / ${item.path}`,
      ingestModeLabel: "外部资产登记",
      sizeLabel: "外部路径",
      stateLabel: "仅预览",
      stateBadgeClass: "border-amber-200 bg-amber-50 text-amber-700",
      progress: 0,
      showPercent: false,
      showProgressBar: false,
      progressBarClass: "bg-slate-300",
      progressHint: "",
      canRemove: true,
      remove: () => removeExternalPreviewItem(item.key),
    }));
  }

  return uploadEntryItems.value.map((item) => buildPendingAssetItem({
    key: item.key,
    name: item.name,
    secondaryText: activeIngestMode.value === "archive" ? "标准 Session 目录文件" : "普通上传文件",
    ingestModeLabel: activeIngestMode.value === "archive" ? "目录导入" : "普通上传",
    sizeLabel: formatSize(item.size),
    state: item.state,
    remove: () => {
      if (activeIngestMode.value === "archive") {
        removeArchiveFileByKey(item.key);
        return;
      }
      removeFileByKey(item.key);
    },
  }));
});

function buildPendingAssetItem(input: {
  key: string;
  name: string;
  secondaryText?: string;
  ingestModeLabel: string;
  sizeLabel: string;
  state?: UploadStateRecord;
  remove: () => void;
}): PendingAssetItem {
  const progress = input.state?.progress ?? 0;
  const isBusy = input.state ? ["initiating", "uploading", "uploaded", "completing"].includes(input.state.state) : false;
  return {
    key: input.key,
    name: input.name,
    secondaryText: input.secondaryText,
    ingestModeLabel: input.ingestModeLabel,
    sizeLabel: input.sizeLabel,
    stateLabel: resolveUploadStateLabel(input.state),
    stateBadgeClass: resolveUploadStateBadgeClass(input.state),
    progress,
    showPercent: Boolean(input.state && input.state.state !== "idle"),
    showProgressBar: Boolean(input.state && input.state.state !== "idle"),
    progressBarClass: resolveProgressBarClass(input.state),
    progressHint: resolveProgressHint(input.state),
    canRemove: !isBusy,
    removeHint: isBusy ? "上传中不可移除" : undefined,
    remove: input.remove,
  };
}

function setActiveIngestMode(mode: IngestModeKey) {
  activeIngestMode.value = mode;
  form.uploadMode = mode === "archive" ? "archive" : "files";
  importResult.value = null;
  message.value = "";
  uploadError.value = false;
  if (mode === "archive") {
    form.sessionId = "";
  }
}

function triggerFileInput() {
  fileInputRef.value?.click();
}

function requestClearWorkspace() {
  if (!canClearCurrentMode.value) return;
  clearDialogVariant.value = hasActiveItems() ? "active" : "normal";
  clearDialogOpen.value = true;
}

function confirmClearWorkspace() {
  clearDialogOpen.value = false;
  clearWorkspace({ preserveTask: true, preserveSession: activeIngestMode.value !== "archive", showMessage: true });
  importResult.value = null;
  manifestSummary.value = null;
}

function addExternalPreviewItem() {
  if (!canAddExternalPreview.value) return;
  externalPreviewItems.value.unshift({
    key: `${externalDraftForm.path}-${Date.now()}`,
    path: externalDraftForm.path.trim(),
    storageType: externalDraftForm.storageType,
    displayName: externalDraftForm.displayName.trim(),
    remark: externalDraftForm.remark.trim(),
    assetTypeKey: "external-path",
  });
  externalDraftForm.path = "";
  externalDraftForm.displayName = "";
  externalDraftForm.remark = "";
}

function removeExternalPreviewItem(key: string) {
  externalPreviewItems.value = externalPreviewItems.value.filter((item) => item.key !== key);
}

function buildFileKey(file: File, index: number) {
  return `${file.name}-${file.size}-${index}`;
}

function buildArchiveKey(file: File, index: number) {
  return `${getDirectoryRelativePath(file)}-${file.size}-${index}`;
}

function removeFileByKey(key: string) {
  const index = form.selectedFiles.findIndex((file, fileIndex) => buildFileKey(file, fileIndex) === key);
  if (index >= 0) {
    delete uploadStates[key];
    form.selectedFiles.splice(index, 1);
  }
}

function removeArchiveFileByKey(key: string) {
  const index = form.archiveFiles.findIndex((file, fileIndex) => buildArchiveKey(file, fileIndex) === key);
  if (index >= 0) {
    delete uploadStates[key];
    form.archiveFiles.splice(index, 1);
  }
  if (form.archiveFiles.length === 0) {
    manifestSummary.value = null;
  }
}

function onDropFiles(event: DragEvent) {
  const files = event.dataTransfer?.files;
  if (files) {
    addFilesWithValidation(Array.from(files), form.selectedFiles, buildFileKey);
  }
}

function onFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  if (input.files) {
    addFilesWithValidation(Array.from(input.files), form.selectedFiles, buildFileKey);
  }
  input.value = "";
}

async function onDirectorySelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const files = input.files ? Array.from(input.files) : [];
  if (!files.length) return;

  form.archiveFiles = [];
  Object.keys(uploadStates).forEach((key) => {
    if (!form.selectedFiles.some((file, index) => buildFileKey(file, index) === key)) {
      delete uploadStates[key];
    }
  });

  const validFiles = addFilesWithValidation(files, form.archiveFiles, buildArchiveKey);
  if (validFiles.length) {
    try {
      manifestSummary.value = summarizeManifest(await parseManifestFromFiles(validFiles));
      message.value = `目录预检通过：识别到 ${validFiles.length} 个文件。`;
      uploadError.value = false;
    } catch (error) {
      manifestSummary.value = null;
      message.value = error instanceof Error ? error.message : "目录预检失败";
      uploadError.value = true;
    }
  }
  input.value = "";
}

function addFilesWithValidation(
  files: File[],
  target: File[],
  buildKey: (file: File, index: number) => string,
) {
  message.value = "";
  uploadError.value = false;
  const accepted: File[] = [];
  for (const file of files) {
    if (!validateFileSize(file)) {
      break;
    }
    target.push(file);
    accepted.push(file);
    const key = buildKey(file, target.length - 1);
    uploadStates[key] = { progress: 0, state: "idle", phase: "upload" };
  }
  return accepted;
}

const MAX_FILE_SIZE = 1024 * 1024 * 1024;

function validateFileSize(file: File): boolean {
  if (file.size > MAX_FILE_SIZE) {
    message.value = `文件 "${file.name}" 大小为 ${formatSize(file.size)}，超过单文件上限 ${formatSize(MAX_FILE_SIZE)}。`;
    uploadError.value = true;
    return false;
  }
  return true;
}

function applyUploadStage(key: string, stage: DirectUploadStage) {
  const current = uploadStates[key] ?? { progress: 0, state: "idle" as UploadItemState, phase: "upload" as UploadPhase };
  if (stage === "uploaded") {
    uploadStates[key] = { ...current, progress: 100, state: "uploaded", phase: "upload" };
  } else if (stage === "completing") {
    uploadStates[key] = { ...current, progress: 100, state: "completing", phase: "complete" };
  }
}

async function uploadSingleFile(taskId: number, file: File, sessionId?: number) {
  const key = buildFileKey(file, form.selectedFiles.indexOf(file));
  const controller = new AbortController();
  abortControllers.set(key, controller);
  uploadStates[key] = { progress: 0, state: "initiating", phase: "upload" };
  try {
    const result = await directUploadToOss({
      taskId,
      file,
      sessionId,
      assetType: inferAssetTypeForFile(file.name),
      signal: controller.signal,
      onProgress: (progress) => {
        uploadStates[key] = { ...uploadStates[key], progress, state: progress >= 100 ? "uploaded" : "uploading", phase: "upload" };
      },
      onStageChange: (stage) => applyUploadStage(key, stage),
    });
    batchSessionId.value = result.sessionId;
    batchSessionCode.value = result.sessionCode;
    uploadStates[key] = { ...uploadStates[key], progress: 100, state: "success", phase: "complete" };
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      uploadStates[key] = { ...uploadStates[key], state: "error", phase: "upload", message: "上传已中止" };
      return;
    }
    if (error instanceof DirectUploadCompletionError) {
      if (error.sessionId && batchSessionId.value == null) batchSessionId.value = error.sessionId;
      uploadStates[key] = { ...uploadStates[key], progress: 100, state: "complete_failed", phase: "complete", fileId: error.fileId, message: error.message };
    } else {
      uploadStates[key] = { ...uploadStates[key], state: "error", phase: "upload", message: error instanceof Error ? error.message : "上传失败" };
    }
    throw error;
  } finally {
    abortControllers.delete(key);
  }
}

async function submitDirectoryImport(taskId: number) {
  const files = [...form.archiveFiles];
  const manifest = await parseManifestFromFiles(files);
  manifestSummary.value = summarizeManifest(manifest);

  const importKey = `dirimp-${taskId}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const uploadedFiles: FinalizeSessionImportUploadedFile[] = [];

  for (let index = 0; index < files.length; index += 1) {
    const file = files[index];
    const relativePath = getDirectoryRelativePath(file);
    const key = buildArchiveKey(file, index);
    const controller = new AbortController();
    abortControllers.set(key, controller);
    uploadStates[key] = { progress: 0, state: "initiating", phase: "upload" };
    try {
      const uploaded = await directUploadSessionImportFileToOss({
        taskId,
        importKey,
        file,
        relativePath,
        signal: controller.signal,
        onProgress: (progress) => {
          uploadStates[key] = { ...uploadStates[key], progress, state: progress >= 100 ? "uploaded" : "uploading", phase: "upload" };
        },
      });
      uploadStates[key] = { ...uploadStates[key], progress: 100, state: "uploaded", phase: "upload" };
      uploadedFiles.push(uploaded);
    } catch (error) {
      uploadStates[key] = {
        ...uploadStates[key],
        state: "error",
        phase: "upload",
        message: error instanceof Error ? error.message : "目录文件上传失败",
      };
      throw error;
    } finally {
      abortControllers.delete(key);
    }
  }

  for (let index = 0; index < files.length; index += 1) {
    const key = buildArchiveKey(files[index], index);
    uploadStates[key] = { ...uploadStates[key], progress: 100, state: "completing", phase: "complete" };
  }

  const result = await finalizeSessionImport({
    taskId,
    importKey,
    requestId: importKey,
    manifest,
    uploadedFiles,
  });

  importResult.value = result;
  batchSessionId.value = result.platformSessionId;
  batchSessionCode.value = result.platformSessionCode ?? "";

  for (let index = 0; index < files.length; index += 1) {
    const key = buildArchiveKey(files[index], index);
    uploadStates[key] = { ...uploadStates[key], progress: 100, state: "success", phase: "complete" };
  }

  message.value = result.existing
    ? `目录导入已幂等命中，Session ${result.platformSessionCode ?? result.platformSessionId ?? "-"} 已存在。`
    : `目录导入完成，平台 Session 为 ${result.platformSessionCode ?? result.platformSessionId ?? "-" }。`;
  uploadError.value = false;
}

async function submitUpload() {
  submitting.value = true;
  message.value = "";
  uploadError.value = false;
  importResult.value = null;
  batchSessionId.value = form.sessionId ? Number(form.sessionId) : null;
  batchSessionCode.value = selectedSession.value?.sessionCode ?? selectedSession.value?.sessionId ?? "";
  const taskId = Number(form.taskId);

  try {
    if (activeIngestMode.value === "archive") {
      await submitDirectoryImport(taskId);
    } else {
      let successCount = 0;
      let failCount = 0;
      for (const file of form.selectedFiles.filter((candidate, index) => {
        const stateName = uploadStates[buildFileKey(candidate, index)]?.state;
        return stateName == null || stateName === "idle" || stateName === "error";
      })) {
        try {
          await uploadSingleFile(taskId, file, batchSessionId.value ?? undefined);
          successCount += 1;
        } catch {
          failCount += 1;
        }
      }
      if (failCount === 0) {
        message.value = `普通文件上传完成，共 ${successCount} 个文件。`;
        uploadError.value = false;
      } else {
        message.value = `${successCount} 个成功，${failCount} 个失败。`;
        uploadError.value = true;
      }
    }

    if (form.taskId) {
      sessions.value = await fetchTaskSessions(Number(form.taskId));
    }
  } catch (error) {
    uploadError.value = true;
    message.value = error instanceof Error ? error.message : "上传失败";
  } finally {
    submitting.value = false;
  }
}

function handleSubmit() {
  if (activeIngestMode.value === "external") return;
  void submitUpload();
}

async function onTaskChange() {
  sessions.value = [];
  form.sessionId = "";
  importResult.value = null;
  if (!form.taskId) return;
  try {
    sessions.value = await fetchTaskSessions(Number(form.taskId));
  } catch {
    // 忽略 Session 加载失败，不阻断上传
  }
}

function resolveUploadStateLabel(state?: UploadStateRecord) {
  if (!state) return "等待上传";
  switch (state.state) {
    case "initiating":
    case "uploading":
      return "上传中";
    case "uploaded":
      return "已上传";
    case "completing":
      return "收口中";
    case "success":
      return "完成";
    case "complete_failed":
      return "登记失败";
    case "error":
      return state.phase === "complete" ? "收口失败" : "上传失败";
    default:
      return "等待上传";
  }
}

function resolveUploadStateBadgeClass(state?: UploadStateRecord) {
  if (!state) return "border-slate-200 bg-slate-50 text-slate-600";
  switch (state.state) {
    case "success":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
    case "complete_failed":
    case "error":
      return "border-rose-200 bg-rose-50 text-rose-700";
    case "uploaded":
      return "border-sky-200 bg-sky-50 text-sky-700";
    case "completing":
      return "border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]";
    default:
      return "border-sky-200 bg-sky-50 text-sky-700";
  }
}

function resolveProgressBarClass(state?: UploadStateRecord) {
  if (!state) return "bg-slate-300";
  switch (state.state) {
    case "success":
      return "bg-emerald-500";
    case "complete_failed":
    case "error":
      return "bg-rose-500";
    case "completing":
      return "bg-[var(--color-brand-500)]";
    default:
      return "bg-sky-500";
  }
}

function resolveProgressHint(state?: UploadStateRecord) {
  if (!state) return "等待开始";
  switch (state.state) {
    case "initiating":
    case "uploading":
      return "文件正在上传到 OSS 临时导入区";
    case "uploaded":
      return "文件上传完成，等待 finalize";
    case "completing":
      return "平台正在创建 Session、data_file、data_asset";
    case "success":
      return "上传与登记已完成";
    case "complete_failed":
      return "文件已上传，但登记失败";
    case "error":
      return state.phase === "complete" ? "收口失败" : "上传失败";
    default:
      return "等待开始";
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function getDirectoryRelativePath(file: File) {
  const webkitRelativePath = (file as File & { webkitRelativePath?: string }).webkitRelativePath ?? file.name;
  const normalized = webkitRelativePath.replace(/\\/g, "/");
  const firstSlash = normalized.indexOf("/");
  return firstSlash >= 0 ? normalized.slice(firstSlash + 1) : normalized;
}

async function parseManifestFromFiles(files: File[]) {
  const manifestFile = files.find((file) => getDirectoryRelativePath(file) === "manifest.json");
  if (!manifestFile) {
    throw new Error("目录根路径缺少 manifest.json");
  }
  const manifestText = await manifestFile.text();
  let manifest: Record<string, unknown>;
  try {
    manifest = JSON.parse(manifestText) as Record<string, unknown>;
  } catch {
    throw new Error("manifest.json 不是合法 JSON");
  }
  return manifest;
}

function summarizeManifest(manifest: Record<string, unknown>): DirectoryManifestSummary {
  const localRefs = asObject(manifest.localRefs);
  const task = asObject(manifest.task);
  const subject = asObject(manifest.subject);
  const action = asObject(manifest.action);
  const sources = asObject(manifest.sources);
  return {
    localSessionId: asString(localRefs.localSessionId) ?? asString(manifest.sessionId),
    profileCode: asString(task.profileCode) ?? asString(manifest.profileCode),
    subjectCode: asString(subject.code) ?? asString(manifest.subjectCode),
    actionName: asString(action.name) ?? asString(manifest.actionName),
    sourceCount: Object.keys(sources).length,
  };
}

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function asString(value: unknown) {
  return typeof value === "string" && value.trim() ? value : null;
}

onMounted(async () => {
  try {
    const page = await fetchAcquisitionList();
    tasks.value = page.records;
  } catch {
    // 忽略列表初始化失败
  }
  if (form.taskId) {
    try {
      sessions.value = await fetchTaskSessions(Number(form.taskId));
    } catch {
      // 忽略
    }
  }
});
</script>
