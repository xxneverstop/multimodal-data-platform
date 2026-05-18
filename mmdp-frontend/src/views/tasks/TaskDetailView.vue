<template>
  <div class="space-y-5">
    <PageHeader
      eyebrow="Task Detail"
      title="任务详情"
      description="围绕单个采集任务管理上传资产、外部资产、可用处理流程和处理任务。"
      :meta="headerMeta"
    >
      <template #actions>
        <BaseButton to="/tasks">返回任务列表</BaseButton>
      </template>
    </PageHeader>

    <PageCard eyebrow="Primary Panel" title="任务摘要" description="当前任务的核心元数据与整体状态。">
      <div v-if="taskLoading" class="py-10 text-center text-sm text-slate-500">正在加载任务详情...</div>
      <div v-else-if="task" class="grid gap-x-4 gap-y-3 md:grid-cols-2 xl:grid-cols-4">
        <div v-for="item in taskSummary" :key="item.label" class="border-b border-slate-200 pb-3">
          <div class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">{{ item.label }}</div>
          <div class="mt-1 text-sm font-medium leading-6 text-slate-900">{{ item.value }}</div>
        </div>
      </div>
      <p v-if="taskError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ taskError }}</p>
    </PageCard>

    <PageCard eyebrow="Primary Panel" title="上传平台内资产" description="上传原始文件并声明其资产类型，上传后会继续走现有 MinIO 与基础 QC 主链路。">
      <div class="grid gap-5 xl:grid-cols-[minmax(0,1.2fr)_280px]">
        <div class="app-upload-zone rounded-[12px] p-4">
          <div class="grid gap-4 md:grid-cols-2">
            <label class="block md:col-span-2">
              <span class="mb-1.5 block text-sm font-medium text-slate-700">选择文件</span>
              <input
                type="file"
                class="app-input border-dashed bg-white file:mr-3 file:rounded-[8px] file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-slate-700"
                @change="handleFileChange"
              />
            </label>
            <label class="block">
              <span class="mb-1.5 block text-sm font-medium text-slate-700">资产类型</span>
              <select v-model="selectedUploadAssetType" class="app-input">
                <option v-for="option in assetTypeOptions" :key="option" :value="option">{{ option }}</option>
              </select>
            </label>
            <div class="rounded-[10px] border border-slate-200 bg-white px-3 py-3 text-xs text-slate-500">
              <div class="font-medium uppercase tracking-[0.14em] text-slate-400">Current Selection</div>
              <div class="mt-1 text-sm font-medium text-slate-900">{{ selectedFile?.name ?? "尚未选择文件" }}</div>
              <div class="mt-1 leading-5">{{ selectedFile ? formatFileSize(selectedFile.size) : "上传后会自动刷新资产列表、文件列表与 pipeline readiness。" }}</div>
            </div>
          </div>
        </div>

        <PageCard eyebrow="Secondary Panel" title="上传说明" description="上传成功后会自动创建 `UPLOADED_FILE` 类型资产。" secondary>
          <ul class="space-y-2.5 text-xs leading-5 text-slate-500">
            <li>任务仍然是容器，真正影响 pipeline 的是资产类型。</li>
            <li>未指定资产类型时后端会默认按 `OTHER` 处理。</li>
            <li>上传资产仍然保留原有 QC 报告入口，不影响旧流程。</li>
          </ul>

          <BaseButton variant="primary" :disabled="!selectedFile || uploading" block class="mt-4" @click="submitUpload">
            {{ uploading ? "正在上传并执行质检..." : "上传资产" }}
          </BaseButton>
        </PageCard>
      </div>

      <p
        v-if="uploadMessage"
        class="mt-3 rounded-[10px] px-3 py-2 text-xs"
        :class="uploadSuccess ? 'border border-emerald-200 bg-emerald-50 text-emerald-700' : 'border border-rose-200 bg-rose-50 text-rose-700'"
      >
        {{ uploadMessage }}
      </p>
    </PageCard>

    <PageCard eyebrow="Primary Panel" title="登记外部资产" description="只登记路径和说明，不上传文件，适合 seq 原始目录、NAS、移动硬盘等外部资产。">
      <form class="grid gap-4 md:grid-cols-2" @submit.prevent="submitExternalAsset">
        <label class="block">
          <span class="mb-1.5 block text-sm font-medium text-slate-700">资产类型</span>
          <select v-model="externalAssetForm.assetType" class="app-input">
            <option v-for="option in assetTypeOptions" :key="option" :value="option">{{ option }}</option>
          </select>
        </label>
        <label class="block">
          <span class="mb-1.5 block text-sm font-medium text-slate-700">显示名称</span>
          <input v-model="externalAssetForm.displayName" required class="app-input" placeholder="例如：SMPL 输出结果 v1" />
        </label>
        <label class="block md:col-span-2">
          <span class="mb-1.5 block text-sm font-medium text-slate-700">外部路径</span>
          <input v-model="externalAssetForm.externalPath" required class="app-input" placeholder="例如：\\\\nas\\lab\\capture\\subject01\\smpl" />
        </label>
        <label class="block">
          <span class="mb-1.5 block text-sm font-medium text-slate-700">文件格式</span>
          <input v-model="externalAssetForm.fileFormat" class="app-input" placeholder="例如：npz / seq / folder" />
        </label>
        <label class="block">
          <span class="mb-1.5 block text-sm font-medium text-slate-700">大小说明</span>
          <input v-model="externalAssetForm.sizeRemark" class="app-input" placeholder="例如：约 12 GB / 3 folders" />
        </label>
        <label class="block md:col-span-2">
          <span class="mb-1.5 block text-sm font-medium text-slate-700">描述</span>
          <textarea v-model="externalAssetForm.description" rows="3" class="app-input resize-y" placeholder="补充资产内容、采集上下文或后续用途"></textarea>
        </label>
        <label class="block md:col-span-2">
          <span class="mb-1.5 block text-sm font-medium text-slate-700">操作备注</span>
          <textarea v-model="externalAssetForm.operatorRemark" rows="3" class="app-input resize-y" placeholder="记录路径来源、操作说明或注意事项"></textarea>
        </label>
        <div class="flex items-center gap-3 md:col-span-2">
          <BaseButton variant="primary" type="submit" :disabled="submittingExternalAsset">
            {{ submittingExternalAsset ? "正在登记..." : "登记外部资产" }}
          </BaseButton>
          <span v-if="externalAssetMessage" :class="externalAssetSuccess ? 'text-emerald-700' : 'text-rose-700'" class="text-xs">
            {{ externalAssetMessage }}
          </span>
        </div>
      </form>
    </PageCard>

    <PageCard eyebrow="Primary Panel" title="任务资产" description="统一查看平台内上传资产和外部路径资产。">
      <DataTableShell>
        <div v-if="assetsLoading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载资产列表...</div>
        <EmptyState
          v-else-if="!assets.length"
          title="暂无资产"
          description="可以先上传一个文件资产，或登记一个外部路径资产，再查看可用 pipeline。"
          icon="A"
        />
        <table v-else class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs uppercase tracking-[0.12em] text-slate-500">
            <tr>
              <th class="px-4 py-3 font-medium">资产</th>
              <th class="px-4 py-3 font-medium">来源</th>
              <th class="px-4 py-3 font-medium">类型</th>
              <th class="px-4 py-3 font-medium">详情</th>
              <th class="px-4 py-3 font-medium">创建时间</th>
              <th class="px-4 py-3 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="asset in assets" :key="asset.id" class="hover:bg-slate-50/80">
              <td class="px-4 py-3">
                <div class="font-medium text-slate-900">{{ asset.displayName }}</div>
                <div class="mt-0.5 text-xs text-slate-500">Asset ID #{{ asset.id }}</div>
              </td>
              <td class="px-4 py-3 text-slate-700">{{ formatEnumLabel(asset.sourceType) }}</td>
              <td class="px-4 py-3">
                <span class="rounded-[8px] bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700">{{ asset.assetType }}</span>
              </td>
              <td class="px-4 py-3 text-slate-500">
                <div v-if="asset.sourceType === 'UPLOADED_FILE'">
                  <div>{{ asset.originalFilename || "-" }}</div>
                  <div class="text-xs">{{ formatFileSize(asset.fileSize) }} / {{ asset.fileExt || "-" }}</div>
                </div>
                <div v-else>
                  <div class="truncate max-w-[280px]">{{ asset.externalPath || "-" }}</div>
                  <div class="text-xs">{{ asset.fileFormat || "-" }} / {{ asset.sizeRemark || "-" }}</div>
                </div>
              </td>
              <td class="px-4 py-3 text-slate-500">{{ formatDateTime(asset.createdAt) }}</td>
              <td class="px-4 py-3 text-right">
                <RouterLink
                  v-if="asset.fileId"
                  :to="`/tasks/${taskId}/qc-report?fileId=${asset.fileId}`"
                  class="font-medium text-slate-700 hover:text-slate-900"
                >
                  查看 QC
                </RouterLink>
                <span v-else class="text-xs text-slate-400">外部资产无 QC</span>
              </td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>

      <p v-if="assetsError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ assetsError }}</p>
    </PageCard>

    <PageCard eyebrow="Primary Panel" title="可用处理流程" description="根据当前任务下资产判断哪些 pipeline 可以执行。">
      <div v-if="pipelinesLoading" class="py-10 text-center text-sm text-slate-500">正在加载 pipeline...</div>
      <div v-else class="space-y-4">
        <article v-for="pipeline in pipelines" :key="pipeline.pipelineId" class="rounded-[12px] border border-slate-200 bg-white p-4 shadow-[var(--shadow-card)]">
          <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
            <div class="space-y-2">
              <div class="flex flex-wrap items-center gap-2">
                <h3 class="text-base font-semibold text-slate-900">{{ pipeline.displayName }}</h3>
                <StatusBadge :status="pipeline.readinessStatus" />
              </div>
              <p class="text-sm leading-5 text-slate-500">{{ pipeline.description }}</p>
              <div class="text-xs text-slate-500">
                <div>已有资产：{{ pipeline.existingAssets.length ? pipeline.existingAssets.join(", ") : "-" }}</div>
                <div>缺失资产：{{ pipeline.missingRequiredAssets.length ? pipeline.missingRequiredAssets.join(", ") : "无" }}</div>
              </div>
              <ul v-if="pipeline.suggestedNextActions.length" class="space-y-1 text-xs text-slate-500">
                <li v-for="action in pipeline.suggestedNextActions" :key="action">{{ action }}</li>
              </ul>
            </div>

            <BaseButton
              variant="primary"
              :disabled="pipeline.readinessStatus !== 'READY' || creatingPipelineId === pipeline.pipelineId"
              @click="submitProcessingJob(pipeline.pipelineId)"
            >
              {{ creatingPipelineId === pipeline.pipelineId ? "正在创建..." : "创建处理任务" }}
            </BaseButton>
          </div>
        </article>
      </div>
      <p v-if="pipelinesError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ pipelinesError }}</p>
    </PageCard>

    <PageCard eyebrow="Primary Panel" title="处理任务" description="展示 mock processing job 的状态与占位结果。">
      <DataTableShell>
        <div v-if="jobsLoading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载处理任务...</div>
        <EmptyState
          v-else-if="!jobs.length"
          title="暂无处理任务"
          description="当必需资产齐全后，可以从上方可用 pipeline 区创建 mock processing job。"
          icon="J"
        />
        <div v-else class="space-y-4">
          <article v-for="job in jobs" :key="job.id" class="rounded-[12px] border border-slate-200 bg-white p-4 shadow-[var(--shadow-card)]">
            <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
              <div class="space-y-2">
                <div class="flex flex-wrap items-center gap-2">
                  <h3 class="text-base font-semibold text-slate-900">Job #{{ job.id }}</h3>
                  <StatusBadge :status="job.status" />
                </div>
                <p class="text-sm text-slate-500">{{ job.pipelineId }}</p>
                <div class="text-xs text-slate-500">
                  <div>创建时间：{{ formatDateTime(job.createdAt) }}</div>
                  <div>更新时间：{{ formatDateTime(job.updatedAt) }}</div>
                </div>
              </div>
              <BaseButton @click="selectJob(job.id)">查看详情</BaseButton>
            </div>

            <div v-if="selectedJob?.id === job.id" class="mt-4 space-y-3">
              <div v-if="selectedJob.errorMessage" class="rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
                {{ selectedJob.errorMessage }}
              </div>
              <div class="rounded-[10px] border border-slate-200 bg-slate-50 px-4 py-3">
                <div class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">Result JSON</div>
                <pre class="mt-2 overflow-x-auto rounded-[10px] bg-slate-900 px-3 py-3 text-xs leading-5 text-slate-100">{{ stringifyJson(selectedJob.resultJson) }}</pre>
              </div>
            </div>
          </article>
        </div>
      </DataTableShell>
      <p v-if="jobsError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ jobsError }}</p>
    </PageCard>

    <PageCard eyebrow="Primary Panel" title="已上传文件" description="保留原有文件级视图与 QC 报告入口，兼容旧流程。">
      <DataTableShell>
        <div v-if="filesLoading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载文件列表...</div>
        <EmptyState
          v-else-if="!files.length"
          title="尚未上传文件"
          description="上传成功后，这里会继续保留原有文件元数据和 QC 报告入口。"
          icon="F"
        />
        <table v-else class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs uppercase tracking-[0.12em] text-slate-500">
            <tr>
              <th class="px-4 py-3 font-medium">文件名</th>
              <th class="px-4 py-3 font-medium">格式</th>
              <th class="px-4 py-3 font-medium">大小</th>
              <th class="px-4 py-3 font-medium">状态</th>
              <th class="px-4 py-3 font-medium">创建时间</th>
              <th class="px-4 py-3 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="file in files" :key="file.id" class="hover:bg-slate-50/80">
              <td class="px-4 py-3">
                <div class="font-medium text-slate-900">{{ file.originalFilename }}</div>
                <div class="mt-0.5 text-xs text-slate-500">File ID #{{ file.id }}</div>
              </td>
              <td class="px-4 py-3 text-slate-700">{{ file.fileExt }}</td>
              <td class="px-4 py-3 text-slate-700">{{ formatFileSize(file.fileSize) }}</td>
              <td class="px-4 py-3"><StatusBadge :status="file.uploadStatus" /></td>
              <td class="px-4 py-3 text-slate-500">{{ formatDateTime(file.createdAt) }}</td>
              <td class="px-4 py-3 text-right">
                <RouterLink :to="`/tasks/${taskId}/qc-report?fileId=${file.id}`" class="font-medium text-slate-700 hover:text-slate-900">
                  查看 QC 报告
                </RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
      <p v-if="filesError" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ filesError }}</p>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { createExternalAsset, fetchTaskAssets } from "@/api/assets";
import { createProcessingJob, fetchAvailablePipelines, fetchProcessingJob, fetchTaskProcessingJobs } from "@/api/processing";
import { fetchTask, fetchTaskFiles, uploadTaskFile } from "@/api/tasks";
import BaseButton from "@/components/BaseButton.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import EmptyState from "@/components/EmptyState.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { AssetType, CreateExternalAssetRequest, DataAssetResponse } from "@/types/asset";
import type { DataFileResponse } from "@/types/file";
import type { AvailablePipelineResponse, ProcessingJobResponse } from "@/types/processing";
import type { TaskResponse } from "@/types/task";
import { formatDateTime, formatEnumLabel, formatFileSize, formatStatusLabel } from "@/utils/format";

const assetTypeOptions: AssetType[] = ["RGB_SEQ_RAW", "RGB_VIDEO_MP4", "MOCAP_CSV", "SMPL_RESULT", "CAMERA_PARAM", "OTHER"];

const route = useRoute();
const taskId = Number(route.params.taskId);

const task = ref<TaskResponse | null>(null);
const taskLoading = ref(false);
const taskError = ref("");

const files = ref<DataFileResponse[]>([]);
const filesLoading = ref(false);
const filesError = ref("");

const assets = ref<DataAssetResponse[]>([]);
const assetsLoading = ref(false);
const assetsError = ref("");

const pipelines = ref<AvailablePipelineResponse[]>([]);
const pipelinesLoading = ref(false);
const pipelinesError = ref("");

const jobs = ref<ProcessingJobResponse[]>([]);
const jobsLoading = ref(false);
const jobsError = ref("");
const selectedJob = ref<ProcessingJobResponse | null>(null);
const creatingPipelineId = ref("");

const selectedFile = ref<File | null>(null);
const selectedUploadAssetType = ref<AssetType>("OTHER");
const uploading = ref(false);
const uploadMessage = ref("");
const uploadSuccess = ref(false);

const submittingExternalAsset = ref(false);
const externalAssetMessage = ref("");
const externalAssetSuccess = ref(false);

const externalAssetForm = reactive<CreateExternalAssetRequest>({
  assetType: "SMPL_RESULT",
  displayName: "",
  externalPath: "",
  fileFormat: "",
  sizeRemark: "",
  description: "",
  operatorRemark: ""
});

const headerMeta = computed(() => [
  { label: "任务编号", value: task.value?.id ?? "-" },
  { label: "当前状态", value: formatStatusLabel(task.value?.status) },
  { label: "资产数量", value: assets.value.length }
]);

const taskSummary = computed(() => {
  if (!task.value) {
    return [];
  }
  return [
    { label: "任务名称", value: task.value.taskName },
    { label: "被试编号", value: task.value.subjectCode },
    { label: "动作名称", value: task.value.actionName },
    { label: "设备类型", value: task.value.deviceType },
    { label: "数据模态", value: task.value.modality },
    { label: "采集日期", value: task.value.collectDate || "-" },
    { label: "场景", value: task.value.scene || "-" },
    { label: "采集人员", value: task.value.operatorName || "-" },
    { label: "采集地点", value: task.value.captureLocation || "-" },
    { label: "任务状态", value: formatStatusLabel(task.value.status) },
    { label: "备注", value: task.value.remark || "-" }
  ];
});

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  selectedFile.value = target.files?.[0] ?? null;
}

function stringifyJson(value: unknown) {
  return JSON.stringify(value ?? {}, null, 2);
}

async function loadTask() {
  taskLoading.value = true;
  taskError.value = "";
  try {
    task.value = await fetchTask(taskId);
  } catch (error) {
    taskError.value = error instanceof Error ? error.message : "加载任务详情失败";
  } finally {
    taskLoading.value = false;
  }
}

async function loadFiles() {
  filesLoading.value = true;
  filesError.value = "";
  try {
    files.value = await fetchTaskFiles(taskId);
  } catch (error) {
    filesError.value = error instanceof Error ? error.message : "加载文件列表失败";
  } finally {
    filesLoading.value = false;
  }
}

async function loadAssets() {
  assetsLoading.value = true;
  assetsError.value = "";
  try {
    assets.value = await fetchTaskAssets(taskId);
  } catch (error) {
    assetsError.value = error instanceof Error ? error.message : "加载资产列表失败";
  } finally {
    assetsLoading.value = false;
  }
}

async function loadPipelines() {
  pipelinesLoading.value = true;
  pipelinesError.value = "";
  try {
    pipelines.value = await fetchAvailablePipelines(taskId);
  } catch (error) {
    pipelinesError.value = error instanceof Error ? error.message : "加载 pipeline 失败";
  } finally {
    pipelinesLoading.value = false;
  }
}

async function loadJobs() {
  jobsLoading.value = true;
  jobsError.value = "";
  try {
    jobs.value = await fetchTaskProcessingJobs(taskId);
  } catch (error) {
    jobsError.value = error instanceof Error ? error.message : "加载处理任务失败";
  } finally {
    jobsLoading.value = false;
  }
}

async function refreshTaskWorkspace() {
  await Promise.all([loadTask(), loadFiles(), loadAssets(), loadPipelines(), loadJobs()]);
}

async function submitUpload() {
  if (!selectedFile.value) {
    return;
  }
  uploading.value = true;
  uploadMessage.value = "";
  try {
    const result = await uploadTaskFile(taskId, selectedFile.value, selectedUploadAssetType.value);
    uploadSuccess.value = true;
    uploadMessage.value = `上传成功：${result.summary}`;
    selectedFile.value = null;
    await refreshTaskWorkspace();
  } catch (error) {
    uploadSuccess.value = false;
    uploadMessage.value = error instanceof Error ? error.message : "上传文件失败";
  } finally {
    uploading.value = false;
  }
}

async function submitExternalAsset() {
  submittingExternalAsset.value = true;
  externalAssetMessage.value = "";
  try {
    await createExternalAsset(taskId, externalAssetForm);
    externalAssetSuccess.value = true;
    externalAssetMessage.value = "外部资产登记成功";
    Object.assign(externalAssetForm, {
      assetType: externalAssetForm.assetType,
      displayName: "",
      externalPath: "",
      fileFormat: "",
      sizeRemark: "",
      description: "",
      operatorRemark: ""
    });
    await Promise.all([loadAssets(), loadPipelines()]);
  } catch (error) {
    externalAssetSuccess.value = false;
    externalAssetMessage.value = error instanceof Error ? error.message : "登记外部资产失败";
  } finally {
    submittingExternalAsset.value = false;
  }
}

async function submitProcessingJob(pipelineId: string) {
  creatingPipelineId.value = pipelineId;
  jobsError.value = "";
  try {
    const createdJob = await createProcessingJob(taskId, { pipelineId });
    selectedJob.value = createdJob;
    await loadJobs();
  } catch (error) {
    jobsError.value = error instanceof Error ? error.message : "创建处理任务失败";
  } finally {
    creatingPipelineId.value = "";
  }
}

async function selectJob(jobId: number) {
  try {
    selectedJob.value = await fetchProcessingJob(jobId);
  } catch (error) {
    jobsError.value = error instanceof Error ? error.message : "加载处理任务详情失败";
  }
}

onMounted(refreshTaskWorkspace);
</script>
