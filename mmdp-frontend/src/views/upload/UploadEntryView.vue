<template>
  <div class="space-y-4">
    <PageHeader
      eyebrow="功能 / 上传"
      title="资产登记上传"
      description="将文件登记到当前数据平台，并归属到指定 Task / Session。"
      :meta="headerMeta"
    />

    <div class="grid gap-4 xl:grid-cols-[minmax(0,1.45fr)_minmax(320px,0.75fr)]">
      <PageCard
        title="资产登记区"
        description="先确定上传目标，再选择文件。文件进入列表后会以待登记资产的形式提交到平台。"
      >
        <form class="space-y-5" @submit.prevent="submitUpload">
          <div class="grid gap-4 md:grid-cols-2">
            <label class="block">
              <span class="mb-1 block text-[13px] text-[var(--color-text-secondary)]">
                归属 Task <span class="text-[var(--color-danger-700)]">*</span>
              </span>
              <select v-model="form.taskId" required class="app-input app-input-compact" @change="onTaskChange">
                <option value="" disabled>请选择 Task</option>
                <option v-for="task in tasks" :key="task.id" :value="String(task.id)">
                  {{ task.taskName }} ({{ task.taskCode }})
                </option>
              </select>
            </label>

            <label class="block">
              <span class="mb-1 block text-[13px] text-[var(--color-text-secondary)]">关联 Session</span>
              <select v-model="form.sessionId" class="app-input app-input-compact">
                <option value="">自动创建新 Session</option>
                <option v-for="sess in sessions" :key="sess.id" :value="String(sess.id)">
                  {{ sess.sessionCode ?? sess.sessionId }} - {{ sess.actionName }}
                </option>
              </select>
            </label>
          </div>

          <div class="rounded-[var(--radius-lg)] border border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] px-4 py-3">
            <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div class="space-y-1">
                <p class="text-[13px] font-medium text-[var(--color-text-primary)]">当前上传目标</p>
                <p class="text-xs leading-5 text-[var(--color-text-secondary)]">
                  {{ uploadTargetDescription }}
                </p>
              </div>
              <div class="flex flex-wrap gap-2 text-xs">
                <span class="rounded-full border border-[var(--color-border-default)] bg-white px-2.5 py-1 text-[var(--color-text-secondary)]">
                  {{ sessionHandlingLabel }}
                </span>
                <span class="rounded-full border border-[var(--color-border-default)] bg-white px-2.5 py-1 text-[var(--color-text-secondary)]">
                  {{ uploadModeLabel }}
                </span>
              </div>
            </div>
          </div>

          <fieldset class="space-y-3">
            <legend class="text-[13px] font-medium text-[var(--color-text-primary)]">上传方式</legend>
            <div class="flex flex-wrap gap-2">
              <label
                class="inline-flex cursor-pointer items-center gap-2 rounded-[var(--radius-md)] border px-3 py-2 text-[13px] transition"
                :class="
                  form.uploadMode === 'files'
                    ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-50)]/40 text-[var(--color-text-primary)]'
                    : 'border-[var(--color-border-default)] bg-white text-[var(--color-text-secondary)] hover:bg-slate-50'
                "
              >
                <input v-model="form.uploadMode" type="radio" value="files" />
                多文件登记
              </label>
              <label
                class="inline-flex cursor-pointer items-center gap-2 rounded-[var(--color-border-default)] border px-3 py-2 text-[13px] transition"
                :class="
                  form.uploadMode === 'archive'
                    ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-50)]/40 text-[var(--color-text-primary)]'
                    : 'border-[var(--color-border-default)] bg-white text-[var(--color-text-secondary)] hover:bg-slate-50'
                "
              >
                <input v-model="form.uploadMode" type="radio" value="archive" />
                ZIP 批次导入
              </label>
            </div>
          </fieldset>

          <div class="space-y-3">
            <div class="flex items-center justify-between gap-3">
              <div>
                <h3 class="text-sm font-semibold text-[var(--color-text-primary)]">待登记资产</h3>
                <p class="text-xs text-[var(--color-text-secondary)]">
                  选择文件后将以平台资产的形式登记，并归属到当前 Task / Session。
                </p>
              </div>
              <div class="text-right text-xs text-[var(--color-text-secondary)]">
                <div>{{ selectedFileCount }} 项待登记</div>
                <div>{{ selectedTotalSizeLabel }}</div>
              </div>
            </div>

            <div
              v-if="form.uploadMode === 'files'"
              class="rounded-[var(--radius-lg)] border border-dashed border-[var(--color-border-default)] bg-white px-5 py-5 transition hover:border-[var(--color-brand-500)] hover:bg-[var(--color-brand-50)]/10"
              @dragover.prevent
              @drop.prevent="onDropFiles"
              @click="triggerFileInput"
            >
              <div class="flex flex-col items-center justify-center text-center">
                <div class="mb-2 text-2xl">📤</div>
                <p class="text-sm font-medium text-[var(--color-text-primary)]">拖拽文件到此处，或点击选择文件</p>
                <p class="mt-1 text-xs leading-5 text-[var(--color-text-secondary)]">
                  支持图片、视频、音频、文档、CSV 等多模态文件，提交后将登记为平台资产。
                </p>
                <div class="mt-3 inline-flex rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-white px-3 py-1.5 text-xs font-medium text-[var(--color-text-primary)]">
                  选择文件
                </div>
              </div>
              <input ref="fileInputRef" type="file" multiple class="hidden" @change="onFilesSelected" />
            </div>

            <div
              v-else
              class="rounded-[var(--radius-lg)] border border-dashed border-[var(--color-border-default)] bg-white px-5 py-5"
            >
              <div class="flex flex-col items-center justify-center text-center">
                <div class="mb-2 text-2xl">🗂️</div>
                <p class="text-sm font-medium text-[var(--color-text-primary)]">选择 ZIP 归档文件</p>
                <p class="mt-1 text-xs leading-5 text-[var(--color-text-secondary)]">
                  归档会作为一次上传批次导入，并在平台中完成登记与关联。
                </p>
                <label class="mt-3 inline-flex cursor-pointer rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-white px-3 py-1.5 text-xs font-medium text-[var(--color-text-primary)] hover:bg-slate-50">
                  选择 ZIP
                  <input type="file" accept=".zip" class="hidden" @change="onArchiveSelected" />
                </label>
              </div>
            </div>

            <div
              v-if="form.uploadMode === 'files' && filePreviewItems.length"
              class="overflow-hidden rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-white"
            >
              <div class="flex items-center justify-between gap-3 border-b border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] px-4 py-2.5">
                <div>
                  <p class="text-sm font-medium text-[var(--color-text-primary)]">资产清单</p>
                  <p class="text-xs text-[var(--color-text-secondary)]">这些文件会以待登记资产的形式提交到平台。</p>
                </div>
                <div class="text-right text-xs text-[var(--color-text-secondary)]">
                  <div>{{ filePreviewItems.length }} 个文件</div>
                  <div>{{ selectedTotalSizeLabel }}</div>
                </div>
              </div>

              <div class="divide-y divide-[var(--color-border-soft)]">
                <div v-for="item in filePreviewItems" :key="item.key" class="px-4 py-3">
                  <div class="flex items-start gap-3">
                    <div class="mt-0.5 shrink-0 rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-[var(--color-surface-muted)] px-2 py-1 text-xs text-[var(--color-text-secondary)]">
                      {{ item.predictedKindLabel }}
                    </div>
                    <div class="min-w-0 flex-1">
                      <div class="truncate text-sm font-medium text-[var(--color-text-primary)]" :title="item.name">
                        {{ item.name }}
                      </div>
                      <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                        <span class="text-[var(--color-text-secondary)]">{{ item.sizeLabel }}</span>
                        <span :class="item.stateTone" class="font-medium">{{ item.stateLabel }}</span>
                      </div>
                    </div>
                    <div class="flex shrink-0 flex-col items-end gap-1.5">
                      <button
                        v-if="item.canRetryComplete"
                        type="button"
                        class="whitespace-nowrap rounded-[var(--radius-md)] border border-[var(--color-brand-300)] bg-[var(--color-brand-50)]/60 px-2.5 py-1 text-xs font-medium text-[var(--color-brand-700)] transition-colors hover:bg-[var(--color-brand-100)]"
                        @click="retryComplete(item.key)"
                      >
                        重试完成登记
                      </button>
                      <button
                        v-if="item.uploading"
                        type="button"
                        class="whitespace-nowrap rounded-[var(--radius-md)] border border-[var(--color-danger-300)] bg-[var(--color-danger-50)]/60 px-2.5 py-1 text-xs font-medium text-[var(--color-danger-700)] transition-colors hover:bg-[var(--color-danger-100)]"
                        @click="cancelUpload(item.key)"
                      >
                        取消上传
                      </button>
                      <button
                        v-else-if="!item.uploading"
                        type="button"
                        class="whitespace-nowrap text-xs text-[var(--color-text-tertiary)] transition-colors hover:text-[var(--color-danger-700)]"
                        @click="removeFile(item.index)"
                      >
                        删除
                      </button>
                    </div>
                  </div>
                  <!-- Progress bar -->
                  <div v-if="item.uploading" class="mt-2">
                    <div class="h-1.5 overflow-hidden rounded-full bg-[var(--color-border-soft)]">
                      <div
                        class="h-full rounded-full bg-[var(--color-brand-500)] transition-all duration-300"
                        :style="{ width: `${item.progress}%` }"
                      />
                    </div>
                    <div class="mt-0.5 text-right text-[11px] text-[var(--color-text-tertiary)]">{{ item.progress }}%</div>
                  </div>
                </div>
              </div>
            </div>

            <div
              v-else-if="form.uploadMode === 'archive' && archivePreview"
              class="rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-white px-4 py-3"
            >
              <div class="flex items-start gap-3">
                <div class="mt-0.5 shrink-0 rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-[var(--color-surface-muted)] px-2 py-1 text-xs text-[var(--color-text-secondary)]">
                  归档
                </div>
                <div class="min-w-0 flex-1">
                  <div class="truncate text-sm font-medium text-[var(--color-text-primary)]" :title="archivePreview.name">
                    {{ archivePreview.name }}
                  </div>
                  <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
                    <span class="text-[var(--color-text-secondary)]">{{ archivePreview.sizeLabel }}</span>
                    <span :class="archivePreview.stateTone" class="font-medium">{{ archivePreview.stateLabel }}</span>
                  </div>
                </div>
                <div class="flex shrink-0 flex-col items-end gap-1.5">
                  <button
                    v-if="archivePreview.canRetryComplete && archivePreview.fileId"
                    type="button"
                    class="whitespace-nowrap rounded-[var(--radius-md)] border border-[var(--color-brand-300)] bg-[var(--color-brand-50)]/60 px-2.5 py-1 text-xs font-medium text-[var(--color-brand-700)] transition-colors hover:bg-[var(--color-brand-100)]"
                    @click="retryComplete(archivePreview.key)"
                  >
                    重试完成登记
                  </button>
                  <button
                    v-if="archivePreview.uploading"
                    type="button"
                    class="whitespace-nowrap rounded-[var(--radius-md)] border border-[var(--color-danger-300)] bg-[var(--color-danger-50)]/60 px-2.5 py-1 text-xs font-medium text-[var(--color-danger-700)] transition-colors hover:bg-[var(--color-danger-100)]"
                    @click="cancelUpload(archivePreview.key)"
                  >
                    取消上传
                  </button>
                  <button
                    v-else-if="!archivePreview.uploading"
                    type="button"
                    class="whitespace-nowrap text-xs text-[var(--color-text-tertiary)] transition-colors hover:text-[var(--color-danger-700)]"
                    @click="form.archiveFile = null"
                  >
                    移除
                  </button>
                </div>
              </div>
              <!-- Progress bar for ZIP upload -->
              <div v-if="archivePreview.uploading" class="mt-2">
                <div class="h-1.5 overflow-hidden rounded-full bg-[var(--color-border-soft)]">
                  <div
                    class="h-full rounded-full bg-[var(--color-brand-500)] transition-all duration-300"
                    :style="{ width: `${archivePreview.progress}%` }"
                  />
                </div>
                <div class="mt-0.5 text-right text-[11px] text-[var(--color-text-tertiary)]">{{ archivePreview.progress }}%</div>
              </div>
            </div>

            <div
              v-else
              class="rounded-[var(--radius-lg)] border border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] px-4 py-4 text-sm text-[var(--color-text-secondary)]"
            >
              还没有待登记内容。选择文件或归档后，平台会在这里显示本次要登记的资产清单。
            </div>
          </div>

          <label class="block">
            <span class="mb-1 block text-[13px] text-[var(--color-text-secondary)]">备注（可选）</span>
            <input v-model="form.remark" class="app-input app-input-compact" placeholder="可填写本次资产上传的补充说明" />
          </label>

          <div class="flex flex-col gap-3 border-t border-[var(--color-border-soft)] pt-4 md:flex-row md:items-center md:justify-between">
            <div v-if="message" :class="[
              'flex-1 rounded-[var(--radius-md)] px-3 py-2 text-xs font-medium',
              uploadError ? 'border border-[var(--color-danger-200)] bg-[var(--color-danger-50)] text-[var(--color-danger-700)]' : 'border border-[var(--color-success-200)] bg-[var(--color-success-50)] text-[var(--color-success-700)]'
            ]">
              {{ message }}
            </div>
            <div v-else class="flex-1 text-xs text-[var(--color-text-secondary)]">
              <p>{{ submitSummaryText }}</p>
            </div>
            <BaseButton variant="primary" type="submit" :disabled="submitting || !canSubmit">
              {{ submitting ? "正在登记..." : "提交登记" }}
            </BaseButton>
          </div>
        </form>
      </PageCard>

      <div class="space-y-4">
        <PageCard title="上传摘要" description="当前上传目标、登记方式与待提交内容的摘要视图。">
          <div class="space-y-4 text-[13px]">
            <section class="space-y-2">
              <h3 class="text-sm font-semibold text-[var(--color-text-primary)]">目标归属</h3>
              <div v-if="selectedTask" class="rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-[var(--color-surface-muted)] px-3 py-3">
                <div class="text-sm font-medium text-[var(--color-text-primary)]">
                  {{ selectedTask.taskName }}
                </div>
                <div class="mt-2 grid gap-2 text-xs text-[var(--color-text-secondary)]">
                  <div>Task：{{ selectedTask.taskCode || `#${selectedTask.id}` }}</div>
                  <div>被试：{{ selectedTask.subjectCode }}</div>
                  <div>动作：{{ selectedTask.actionName }}</div>
                  <div>Profile：{{ selectedTask.profileName || "-" }}</div>
                  <div>采集日期：{{ selectedTask.collectDate }}</div>
                  <div>状态：{{ selectedTask.status }}</div>
                </div>
              </div>
              <div v-else class="rounded-[var(--radius-lg)] border border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] px-3 py-3 text-xs text-[var(--color-text-secondary)]">
                尚未选择 Task。平台需要先确定资产归属目标后才能登记。
              </div>
            </section>

            <section class="space-y-2">
              <h3 class="text-sm font-semibold text-[var(--color-text-primary)]">Session 处理方式</h3>
              <div class="rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-white px-3 py-3 text-xs text-[var(--color-text-secondary)]">
                <template v-if="selectedSession">
                  <div class="text-sm font-medium text-[var(--color-text-primary)]">
                    {{ selectedSession.sessionCode ?? selectedSession.sessionId }}
                  </div>
                  <div class="mt-2 space-y-1">
                    <div>动作：{{ selectedSession.actionName }}</div>
                    <div>被试：{{ selectedSession.subjectCode }}</div>
                    <div>上传状态：{{ selectedSession.uploadStatus }}</div>
                  </div>
                </template>
                <template v-else>
                  <div class="text-sm font-medium text-[var(--color-text-primary)]">自动创建新 Session</div>
                  <p class="mt-2 leading-5">如果不显式选择 Session，本次上传将自动创建新的归属 Session。</p>
                </template>
              </div>
            </section>

            <section class="space-y-2">
              <h3 class="text-sm font-semibold text-[var(--color-text-primary)]">登记结果预览</h3>
              <div class="rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-white px-3 py-3 text-xs text-[var(--color-text-secondary)]">
                <div class="space-y-1">
                  <div>上传模式：{{ uploadModeLabel }}</div>
                  <div>待登记项数：{{ selectedFileCount }}</div>
                  <div>总大小：{{ selectedTotalSizeLabel }}</div>
                  <div>{{ previewSummaryText }}</div>
                </div>
                <div class="mt-3 rounded-[var(--radius-md)] bg-[var(--color-surface-muted)] px-3 py-2 leading-5">
                  <template v-if="message">
                    <p :class="uploadError ? 'text-[var(--color-danger-700)]' : 'text-[var(--color-success-700)]'">
                      {{ message }}
                    </p>
                  </template>
                  <template v-else>
                    <p>{{ resultPreviewText }}</p>
                  </template>
                </div>
              </div>
            </section>
          </div>
        </PageCard>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchAcquisitionList } from "@/api/platform";
import { fetchTaskSessions, type SessionResponse } from "@/api/sessions";
import BaseButton from "@/components/BaseButton.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import type { AssetType } from "@/types/asset";
import type { TaskResponse } from "@/types/task";
import { DirectUploadCompletionError, directUploadToOss, inferAssetTypeForFile, retryDirectUploadCompletion } from "@/utils/ossDirectUpload";

type FileKind = "video" | "image" | "audio" | "csv" | "document" | "archive" | "generic";
type UploadItemState = "idle" | "initiating" | "uploading" | "completing" | "success" | "complete_failed" | "error";

const tasks = ref<TaskResponse[]>([]);
const sessions = ref<SessionResponse[]>([]);
const submitting = ref(false);
const message = ref("");
const uploadError = ref(false);
const fileInputRef = ref<HTMLInputElement | null>(null);
const uploadStates = reactive<Record<string, { progress: number; state: UploadItemState; fileId?: number; message?: string }>>({});
const abortControllers = new Map<string, AbortController>();
const batchSessionId = ref<number | null>(null);
const batchSessionCode = ref("");

const form = reactive({
  taskId: "",
  sessionId: "",
  uploadMode: "files" as "files" | "archive",
  selectedFiles: [] as File[],
  archiveFile: null as File | null,
  remark: "",
});

const canSubmit = computed(() => {
  if (!form.taskId) return false;
  if (form.uploadMode === "archive") return form.archiveFile != null;
  return form.selectedFiles.length > 0;
});

const selectedTask = computed(() => tasks.value.find((task) => String(task.id) === form.taskId) ?? null);

const selectedSession = computed(() => sessions.value.find((session) => String(session.id) === form.sessionId) ?? null);

const selectedFileCount = computed(() => {
  if (form.uploadMode === "archive") {
    return form.archiveFile ? 1 : 0;
  }
  return form.selectedFiles.length;
});

const selectedTotalBytes = computed(() => {
  if (form.uploadMode === "archive") {
    return form.archiveFile?.size ?? 0;
  }
  return form.selectedFiles.reduce((total, file) => total + file.size, 0);
});

const selectedTotalSizeLabel = computed(() => formatSize(selectedTotalBytes.value));

const uploadModeLabel = computed(() => (form.uploadMode === "archive" ? "ZIP 批次导入" : "多文件登记"));

const sessionHandlingLabel = computed(() =>
  selectedSession.value ? `Session：${selectedSession.value.sessionCode ?? selectedSession.value.sessionId}` : "Session：自动创建新 Session",
);

const uploadTargetDescription = computed(() => {
  if (!selectedTask.value) {
    return "请选择 Task 作为本次资产登记的归属目标。";
  }
  const task = selectedTask.value;
  const sessionText = selectedSession.value
    ? `关联到 Session ${selectedSession.value.sessionCode ?? selectedSession.value.sessionId}`
    : "未指定 Session，提交时将自动创建新 Session";
  return `当前目标为 Task ${task.taskCode || `#${task.id}`} / ${task.taskName}，${sessionText}。`;
});

const headerMeta = computed(() => [
  { label: "目标 Task", value: selectedTask.value?.taskCode || selectedTask.value?.taskName || "未选择" },
  { label: "Session", value: selectedSession.value?.sessionCode ?? selectedSession.value?.sessionId ?? "自动创建" },
  { label: "上传模式", value: uploadModeLabel.value },
  { label: "待登记项", value: selectedFileCount.value || "-" },
]);

const filePreviewItems = computed(() =>
  form.selectedFiles.map((file, index) => {
    const kind = inferFileKind(file);
    const key = `${file.name}-${file.size}-${index}`;
    const state = uploadStates[key];
    return {
      key,
      index,
      name: file.name,
      sizeLabel: formatSize(file.size),
      predictedKindLabel: formatPredictedFileKind(kind),
      uploading: state?.state === "initiating" || state?.state === "uploading",
      progress: state?.progress ?? 0,
      stateLabel: resolveUploadStateLabel(state?.state),
      stateTone: resolveUploadStateTone(state?.state),
      canRetryComplete: state?.state === "complete_failed" && Boolean(state.fileId),
    };
  }),
);

const archiveKey = computed(() => {
  if (!form.archiveFile) return "";
  return `${form.archiveFile.name}-${form.archiveFile.size}-0`;
});

const archivePreview = computed(() => {
  if (!form.archiveFile) {
    return null;
  }
  const state = uploadStates[archiveKey.value];
  return {
    key: archiveKey.value,
    name: form.archiveFile.name,
    sizeLabel: formatSize(form.archiveFile.size),
    uploading: state?.state === "initiating" || state?.state === "uploading",
    progress: state?.progress ?? 0,
    stateLabel: resolveUploadStateLabel(state?.state),
    stateTone: resolveUploadStateTone(state?.state),
    canRetryComplete: state?.state === "complete_failed" && Boolean(state.fileId),
    fileId: state?.fileId,
  };
});

const previewSummaryText = computed(() => {
  if (form.uploadMode === "archive") {
    return form.archiveFile ? "本次将作为 1 个归档批次导入并完成资产登记。" : "尚未选择归档文件。";
  }
  return selectedFileCount.value
    ? `本次预计登记 ${selectedFileCount.value} 项平台资产。`
    : "尚未选择待登记文件。";
});

const resultPreviewText = computed(() => {
  if (!selectedTask.value) {
    return "选择 Task 后，平台会根据当前目标生成登记上下文并准备接收资产。";
  }
  if (form.uploadMode === "archive") {
    return "ZIP 归档会作为上传批次导入，并在平台中建立关联关系。";
  }
  return "列表中的每个文件都会以待登记资产的形式提交到平台，并归属到当前目标。";
});

const submitSummaryText = computed(() => {
  if (!selectedTask.value) {
    return "请先选择 Task，再提交资产登记。";
  }
  return `${uploadModeLabel.value} · ${selectedFileCount.value} 项内容 · ${selectedTotalSizeLabel.value}`;
});

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + " KB";
  return (bytes / (1024 * 1024)).toFixed(1) + " MB";
}

function triggerFileInput() {
  fileInputRef.value?.click();
}

function inferFileKind(file: File): FileKind {
  const fileName = file.name.toLowerCase();
  const type = file.type.toLowerCase();

  if (type.startsWith("video/") || /\.(mp4|mov|avi|mkv|webm)$/i.test(fileName)) return "video";
  if (type.startsWith("image/") || /\.(jpg|jpeg|png|bmp|gif|webp)$/i.test(fileName)) return "image";
  if (type.startsWith("audio/") || /\.(wav|mp3|aac|flac|m4a)$/i.test(fileName)) return "audio";
  if (/\.(csv|tsv)$/i.test(fileName)) return "csv";
  if (/\.(zip|rar|7z|tar|gz)$/i.test(fileName)) return "archive";
  if (/\.(json|jsonl|txt|pdf|doc|docx|xls|xlsx|ppt|pptx)$/i.test(fileName)) return "document";
  return "generic";
}

function formatPredictedFileKind(kind: FileKind): string {
  switch (kind) {
    case "video":
      return "视频";
    case "image":
      return "图片";
    case "audio":
      return "音频";
    case "csv":
      return "CSV";
    case "document":
      return "文档";
    case "archive":
      return "归档";
    default:
      return "通用文件";
  }
}

const MAX_FILE_SIZE = 1024 * 1024 * 1024; // 1GB，与后端 max-file-size 保持一致

function validateFileSize(file: File): boolean {
  if (file.size > MAX_FILE_SIZE) {
    message.value = `文件 "${file.name}" 大小为 ${formatSize(file.size)}，超过单文件上限 ${formatSize(MAX_FILE_SIZE)}，请拆分后重试`;
    uploadError.value = true;
    return false;
  }
  return true;
}

function addFilesWithValidation(files: FileList | File[]) {
  message.value = "";
  uploadError.value = false;
  for (let i = 0; i < files.length; i++) {
    const file = files[i];
    if (!validateFileSize(file)) return;
    form.selectedFiles.push(file);
    const key = `${file.name}-${file.size}-${form.selectedFiles.length - 1}`;
    uploadStates[key] = { progress: 0, state: "idle" };
  }
}

function onDropFiles(event: DragEvent) {
  const files = event.dataTransfer?.files;
  if (files) {
    addFilesWithValidation(files);
  }
}

function onFilesSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  if (input.files) {
    addFilesWithValidation(input.files);
  }
  input.value = "";
}

function removeFile(index: number) {
  const file = form.selectedFiles[index];
  if (file) {
    const key = `${file.name}-${file.size}-${index}`;
    cancelUpload(key);
    delete uploadStates[key];
  }
  form.selectedFiles.splice(index, 1);
}

function resolveUploadStateLabel(state?: UploadItemState) {
  switch (state) {
    case "initiating":
      return "⏳ 申请凭证中";
    case "uploading":
      return "📤 上传中";
    case "completing":
      return "📝 登记中";
    case "success":
      return "✅ 已完成";
    case "complete_failed":
      return "⚠️ 待登记";
    case "error":
      return "❌ 上传失败";
    default:
      return "⬜ 待登记";
  }
}

function resolveUploadStateTone(state?: UploadItemState) {
  switch (state) {
    case "success":
      return "text-[var(--color-success-700)]";
    case "complete_failed":
      return "text-[var(--color-warning-700)]";
    case "error":
      return "text-[var(--color-danger-700)]";
    case "initiating":
    case "uploading":
    case "completing":
      return "text-[var(--color-brand-600)]";
    default:
      return "text-[var(--color-text-tertiary)]";
  }
}

async function uploadSingleFile(taskId: number, file: File, sessionId?: number) {
  const key = `${file.name}-${file.size}-${form.selectedFiles.indexOf(file)}`;
  const controller = new AbortController();
  abortControllers.set(key, controller);
  uploadStates[key] = { progress: 0, state: "initiating" };
  try {
    const result = await directUploadToOss({
      taskId,
      file,
      sessionId,
      assetType: inferAssetTypeForFile(file.name) as AssetType,
      signal: controller.signal,
      onProgress: (progress) => {
        uploadStates[key] = { ...uploadStates[key], progress, state: "uploading" };
      },
    });
    batchSessionId.value = result.sessionId;
    batchSessionCode.value = result.sessionCode;
    uploadStates[key] = { ...uploadStates[key], progress: 100, state: "success" };
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      uploadStates[key] = { progress: 0, state: "idle" };
      return;
    }
    if (error instanceof DirectUploadCompletionError) {
      // 即使 complete 失败，initiate 可能已创建 session
      if (error.sessionId && batchSessionId.value == null) {
        batchSessionId.value = error.sessionId;
      }
      uploadStates[key] = {
        ...uploadStates[key],
        progress: 100,
        state: "complete_failed",
        fileId: error.fileId,
        message: error.message,
      };
    } else {
      uploadStates[key] = { ...uploadStates[key], state: "error", message: error instanceof Error ? error.message : "上传失败" };
    }
    throw error;
  } finally {
    abortControllers.delete(key);
  }
}

function cancelUpload(key: string) {
  const controller = abortControllers.get(key);
  if (controller) {
    controller.abort();
    abortControllers.delete(key);
  }
}

function isFileUploading(state?: UploadItemState): boolean {
  return state === "initiating" || state === "uploading" || state === "completing";
}

async function retryComplete(key: string) {
  const state = uploadStates[key];
  if (!state?.fileId) {
    return;
  }
  uploadStates[key] = { ...state, state: "completing" };
  try {
    await retryDirectUploadCompletion(state.fileId);
    uploadStates[key] = { ...state, state: "success", progress: 100 };
    message.value = "平台登记已完成";
    uploadError.value = false;
  } catch (error) {
    uploadStates[key] = { ...state, state: "complete_failed", progress: 100, message: error instanceof Error ? error.message : "完成登记失败" };
    message.value = error instanceof Error ? error.message : "完成登记失败";
    uploadError.value = true;
  }
}

function onArchiveSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] ?? null;
  if (file && !validateFileSize(file)) {
    input.value = "";
    return;
  }
  form.archiveFile = file;
}

async function onTaskChange() {
  sessions.value = [];
  form.sessionId = "";
  if (!form.taskId) return;
  try {
    sessions.value = await fetchTaskSessions(Number(form.taskId));
  } catch {
    // session 加载失败不影响上传
  }
}

async function submitUpload() {
  submitting.value = true;
  message.value = "";
  uploadError.value = false;
  batchSessionId.value = form.sessionId ? Number(form.sessionId) : null;
  batchSessionCode.value = selectedSession.value?.sessionCode ?? selectedSession.value?.sessionId ?? "";
  const taskId = Number(form.taskId);
  try {
    if (form.uploadMode === "archive") {
      const file = form.archiveFile!;
      const key = `${file.name}-${file.size}-0`;
      const controller = new AbortController();
      abortControllers.set(key, controller);
      uploadStates[key] = { progress: 0, state: "initiating" };
      try {
        const sessionId = form.sessionId ? Number(form.sessionId) : undefined;
        await directUploadToOss({
          taskId,
          file,
          sessionId,
          assetType: "SESSION_ARCHIVE_ZIP",
          signal: controller.signal,
          onProgress: (progress) => {
            uploadStates[key] = { ...uploadStates[key], progress, state: "uploading" };
          },
        });
        uploadStates[key] = { progress: 100, state: "success" };
        message.value = "归档上传并登记完成";
        form.archiveFile = null;
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") {
          uploadStates[key] = { progress: 0, state: "idle" };
          return;
        }
        uploadError.value = true;
        if (error instanceof DirectUploadCompletionError) {
          uploadStates[key] = { ...uploadStates[key], progress: 100, state: "complete_failed", fileId: error.fileId };
          message.value = "归档已上传到 OSS，但平台登记失败，可重试完成登记";
        } else {
          uploadStates[key] = { ...uploadStates[key], state: "error", message: error instanceof Error ? error.message : "上传失败" };
          message.value = error instanceof Error ? error.message : "上传失败";
        }
      } finally {
        abortControllers.delete(key);
      }
    } else {
      let successCount = 0;
      let failCount = 0;
      for (const file of form.selectedFiles) {
        try {
          await uploadSingleFile(taskId, file, batchSessionId.value ?? undefined);
          successCount += 1;
        } catch {
          failCount += 1;
          // 不 return，继续上传剩余文件
        }
      }
      if (failCount === 0) {
        message.value = `直传完成：${successCount} 个文件已上传并登记`;
        // 全部成功，清空列表
        uploadStates && Object.keys(uploadStates).forEach((k) => delete uploadStates[k]);
        form.selectedFiles = [];
        // 刷新 session 列表
        if (form.taskId) {
          fetchTaskSessions(Number(form.taskId)).then((list) => { sessions.value = list; }).catch(() => {});
        }
      } else {
        uploadError.value = true;
        message.value = `${successCount} 个成功，${failCount} 个失败（OSS 已上传但登记失败可重试）`;
        // 移除成功的文件，保留失败的供重试
        form.selectedFiles = form.selectedFiles.filter((file) => {
          const key = `${file.name}-${file.size}-${form.selectedFiles.indexOf(file)}`;
          const state = uploadStates[key];
          return state?.state !== "success";
        });
      }
    }
  } catch (error) {
    uploadError.value = true;
    message.value = error instanceof Error ? error.message : "上传失败";
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  try {
    const page = await fetchAcquisitionList();
    tasks.value = page.records;
  } catch {
    // 忽略
  }
});
</script>
