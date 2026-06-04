<template>
  <div class="space-y-4">
    <PageHeader
      eyebrow="功能 / 原型"
      title="采集原型"
      description="文件回放模式：2 路双目 MP4 + HMD 录屏 + IMU CSV。"
    >
      <template #actions>
        <div class="flex items-center gap-3">
          <span
            class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
            :class="status.running
              ? 'bg-green-50 text-green-700 ring-1 ring-green-200'
              : 'bg-slate-100 text-slate-500 ring-1 ring-slate-200'"
          >
            <span class="inline-block h-2 w-2 rounded-full"
              :class="status.running ? 'bg-green-500 animate-pulse' : 'bg-slate-400'" />
            {{ status.running ? 'REC' : 'IDLE' }}
          </span>
          <span class="font-mono text-sm text-slate-600">{{ formatElapsed(status.elapsedMs) }}</span>
          <span class="inline-flex items-center gap-1 text-xs"
            :class="wsConnected ? 'text-green-600' : 'text-red-500'">
            <span class="inline-block h-1.5 w-1.5 rounded-full"
              :class="wsConnected ? 'bg-green-500' : 'bg-red-500'" />
            {{ wsConnected ? 'WS' : 'WS 断开' }}
          </span>
        </div>
      </template>
    </PageHeader>

    <!-- Control bar -->
    <PageCard title="采集控制">
      <div class="grid gap-4 md:grid-cols-3">
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">采集任务</span>
          <div class="flex gap-2">
            <select v-model="selectedTaskId" :disabled="status.running"
              class="app-input app-input-compact flex-1" @change="onTaskSelect">
              <option :value="null" disabled>-- 选择任务 --</option>
              <option v-for="t in tasks" :key="t.id" :value="t.id">
                {{ t.taskName }} (ID: {{ t.id }})
              </option>
            </select>
            <BaseButton variant="ghost" size="sm" :disabled="status.running" @click="showNewTaskDialog = true">
              + 新建
            </BaseButton>
          </div>
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">Subject Code</span>
          <input v-model="params.subjectCode" :disabled="status.running"
            class="app-input app-input-compact w-full" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">Action Name</span>
          <input v-model="params.actionName" :disabled="status.running"
            class="app-input app-input-compact w-full" />
        </label>
      </div>
      <div class="mt-4 flex items-center gap-2">
        <BaseButton variant="primary" :disabled="status.running || needsReviewAction || savingSession || discardingSession" @click="handleStart">
          &#9654; 开始采集
        </BaseButton>
        <BaseButton variant="danger" :disabled="!status.running" @click="handleStop">
          &#9632; 停止采集
        </BaseButton>
        <BaseButton
          v-if="status.sessionId && !status.running"
          variant="secondary"
          :disabled="uploading || !canUploadCurrentSession"
          @click="showUploadDialog = true; uploadStage = 'idle'; uploadProgress = 0; uploadedFiles = []; uploadMsg = ''"
        >
          {{ uploading ? '上传中...' : '&#9650; 上传到平台' }}
        </BaseButton>
        <BaseButton
          v-if="needsReviewAction"
          variant="secondary"
          :disabled="savingSession || discardingSession"
          @click="handleSavePendingSession"
        >
          {{ savingSession ? "保存中..." : "保存本次采集" }}
        </BaseButton>
        <BaseButton
          v-if="needsReviewAction"
          variant="ghost"
          :disabled="savingSession || discardingSession"
          @click="handleDiscardPendingSession"
        >
          {{ discardingSession ? "丢弃中..." : "丢弃本次采集" }}
        </BaseButton>
        <span v-if="errorMsg" class="text-sm text-red-500">{{ errorMsg }}</span>
        <span v-if="status.sessionId" class="ml-2 text-xs text-slate-400">
          Session: {{ status.sessionId }}
        </span>
        <span v-if="uploadBlockedReason" class="text-xs text-amber-600">{{ uploadBlockedReason }}</span>
        <span v-if="uploadMsg" class="text-sm" :class="uploadOk ? 'text-green-600' : 'text-red-500'">{{ uploadMsg }}</span>
      </div>

      <!-- Upload dialog -->
      <div v-if="showUploadDialog" class="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
        <div class="text-sm font-medium mb-3">上传 Session 到平台</div>

        <!-- Stage indicator -->
        <div v-if="uploadStage !== 'idle'" class="mb-3">
          <div class="flex items-center gap-2 mb-2">
            <span v-if="uploadStage === 'packaging' || uploadStage === 'uploading'"
              class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-blue-400 border-t-transparent" />
            <span v-else-if="uploadStage === 'done'"
              class="inline-flex h-4 w-4 items-center justify-center rounded-full bg-green-500 text-[10px] text-white">&#10003;</span>
            <span v-else-if="uploadStage === 'error'"
              class="inline-flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[10px] text-white">&#10007;</span>
            <span class="text-sm font-medium" :class="stageColor">
              {{ stageLabel }}
            </span>
            <span v-if="uploadStage === 'packaging' || uploadStage === 'uploading'" class="text-xs text-slate-400 ml-1">
              {{ uploadProgress }}%
            </span>
          </div>
          <!-- Progress bar -->
          <div v-if="uploadStage === 'packaging' || uploadStage === 'uploading'" class="mb-2">
            <div class="h-2 w-full rounded-full bg-slate-200 overflow-hidden">
              <div
                class="h-full rounded-full transition-all duration-500 ease-out"
                :class="uploadStage === 'packaging' ? 'bg-amber-400' : 'bg-blue-500'"
                :style="{ width: uploadProgress + '%' }"
              />
            </div>
          </div>
          <!-- OSS paths on success -->
          <div v-if="uploadStage === 'done' && uploadedFiles.length" class="mt-2 rounded bg-white p-2 text-xs">
            <div class="text-slate-500 mb-1 font-medium">OSS 存储路径：</div>
            <div v-for="f in uploadedFiles" :key="f.objectKey" class="text-slate-600 font-mono truncate mb-0.5">
              <span class="text-slate-400">{{ f.name }}:</span> {{ f.objectKey }}
            </div>
          </div>
          <!-- Error message -->
          <div v-if="uploadStage === 'error'" class="mt-2 text-xs text-red-500">{{ uploadMsg }}</div>
        </div>

        <div v-if="uploadStage === 'idle' || uploadStage === 'error'">
          <label class="block">
            <span class="mb-1 block text-xs text-slate-500">平台地址</span>
            <input v-model="uploadForm.platformUrl" class="app-input app-input-compact w-full" placeholder="http://localhost:19021" />
          </label>
          <div class="mt-2 text-xs text-slate-500">
            采集任务：<span class="font-medium text-slate-700">{{ selectedTaskName }}</span>
          </div>
        </div>
        <div class="mt-3 flex items-center gap-2">
          <BaseButton v-if="uploadStage === 'idle' || uploadStage === 'error'" variant="primary" size="sm" :disabled="uploading" @click="handleUpload">确认上传</BaseButton>
          <BaseButton v-if="uploadStage !== 'uploading' && uploadStage !== 'packaging'" variant="ghost" size="sm" @click="closeUploadDialog">关闭</BaseButton>
        </div>
      </div>

      <!-- New task dialog -->
      <div v-if="showNewTaskDialog" class="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-4">
        <div class="text-sm font-medium mb-3">新建采集任务</div>
        <div class="grid gap-3 md:grid-cols-2">
          <label class="block">
            <span class="mb-1 block text-xs text-slate-500">任务名 <span class="text-red-400">*</span></span>
            <input v-model="newTaskForm.taskName" class="app-input app-input-compact w-full" placeholder="例如：行走采集-001" />
          </label>
          <label class="block">
            <span class="mb-1 block text-xs text-slate-500">Subject Code <span class="text-red-400">*</span></span>
            <input v-model="newTaskForm.subjectCode" class="app-input app-input-compact w-full" placeholder="SUBJ-001" />
          </label>
          <label class="block">
            <span class="mb-1 block text-xs text-slate-500">Action Name <span class="text-red-400">*</span></span>
            <input v-model="newTaskForm.actionName" class="app-input app-input-compact w-full" placeholder="walking" />
          </label>
          <label class="block">
            <span class="mb-1 block text-xs text-slate-500">采集日期</span>
            <input v-model="newTaskForm.collectDate" type="date" class="app-input app-input-compact w-full" />
          </label>
          <label class="block md:col-span-2">
            <span class="mb-1 block text-xs text-slate-500">备注</span>
            <input v-model="newTaskForm.remark" class="app-input app-input-compact w-full" placeholder="可选备注" />
          </label>
        </div>
        <div class="mt-3 flex items-center gap-2">
          <BaseButton variant="primary" size="sm" :disabled="creatingTask" @click="handleCreateTask">
            {{ creatingTask ? '创建中...' : '确认创建' }}
          </BaseButton>
          <BaseButton variant="ghost" size="sm" @click="showNewTaskDialog = false">取消</BaseButton>
        </div>
      </div>
    </PageCard>

    <!-- Video panels (dark player area) -->
    <PageCard title="视频预览">
      <div class="rounded-xl bg-slate-900 p-4">
        <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">

          <!-- Left Camera -->
          <div class="flex flex-col">
            <div class="mb-2 flex items-center justify-between">
              <span class="text-xs font-medium text-slate-300">Left Camera</span>
              <span class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium"
                :class="badgeClass(vsLeft?.status)">
                <span class="inline-block h-1.5 w-1.5 rounded-full"
                  :class="dotClass(vsLeft?.status)" />
                {{ badgeLabel(vsLeft?.status) }}
              </span>
            </div>
            <div class="relative aspect-video rounded-lg border-2 flex items-center justify-center overflow-hidden"
              :class="devAvailable('left')
                ? 'border-slate-700 bg-black'
                : 'border-amber-800 bg-slate-800'">
              <img v-if="devAvailable('left')"
                :key="'left-' + streamKey"
                :src="mjpegUrl('left')"
                class="w-full h-full object-contain" alt="Left Camera" />
              <div v-else class="text-center">
                <div class="text-amber-400 text-lg mb-1">&#9888;</div>
                <div class="text-amber-500 text-xs">Source Missing</div>
                <div class="text-amber-700 text-[10px] mt-1">test_data/cam_left.mp4</div>
              </div>
            </div>
            <div class="mt-1.5 flex items-center gap-3 text-[11px] text-slate-400 font-mono">
              <span>FPS: {{ vsLeft?.fps?.toFixed(1) ?? '--' }}</span>
              <span>Frame: {{ vsLeft?.frameIndex ?? '--' }}</span>
              <span class="truncate">TS: {{ fmtTs(vsLeft?.hostReceiveTimestamp) }}</span>
            </div>
          </div>

          <!-- Right Camera -->
          <div class="flex flex-col">
            <div class="mb-2 flex items-center justify-between">
              <span class="text-xs font-medium text-slate-300">Right Camera</span>
              <span class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium"
                :class="badgeClass(vsRight?.status)">
                <span class="inline-block h-1.5 w-1.5 rounded-full"
                  :class="dotClass(vsRight?.status)" />
                {{ badgeLabel(vsRight?.status) }}
              </span>
            </div>
            <div class="relative aspect-video rounded-lg border-2 flex items-center justify-center overflow-hidden"
              :class="devAvailable('right')
                ? 'border-slate-700 bg-black'
                : 'border-amber-800 bg-slate-800'">
              <img v-if="devAvailable('right')"
                :key="'right-' + streamKey"
                :src="mjpegUrl('right')"
                class="w-full h-full object-contain" alt="Right Camera" />
              <div v-else class="text-center">
                <div class="text-amber-400 text-lg mb-1">&#9888;</div>
                <div class="text-amber-500 text-xs">Source Missing</div>
                <div class="text-amber-700 text-[10px] mt-1">test_data/cam_right.mp4</div>
              </div>
            </div>
            <div class="mt-1.5 flex items-center gap-3 text-[11px] text-slate-400 font-mono">
              <span>FPS: {{ vsRight?.fps?.toFixed(1) ?? '--' }}</span>
              <span>Frame: {{ vsRight?.frameIndex ?? '--' }}</span>
              <span class="truncate">TS: {{ fmtTs(vsRight?.hostReceiveTimestamp) }}</span>
            </div>
          </div>

          <!-- HMD View -->
          <div class="flex flex-col">
            <div class="mb-2 flex items-center justify-between">
              <span class="text-xs font-medium text-slate-300">HMD View</span>
              <span class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium"
                :class="badgeClass(vsHmd?.status)">
                <span class="inline-block h-1.5 w-1.5 rounded-full"
                  :class="dotClass(vsHmd?.status)" />
                {{ badgeLabel(vsHmd?.status) }}
              </span>
            </div>
            <div class="relative aspect-video rounded-lg border-2 flex items-center justify-center overflow-hidden"
              :class="devAvailable('hmd')
                ? 'border-slate-700 bg-black'
                : 'border-amber-800 bg-slate-800'">
              <img v-if="devAvailable('hmd')"
                :key="'hmd-' + streamKey"
                :src="mjpegUrl('hmd')"
                class="w-full h-full object-contain" alt="HMD View" />
              <div v-else class="text-center">
                <div class="text-amber-400 text-lg mb-1">&#9888;</div>
                <div class="text-amber-500 text-xs">Source Missing</div>
                <div class="text-amber-700 text-[10px] mt-1">test_data/hmd_view.mp4</div>
              </div>
            </div>
            <div class="mt-1.5 flex items-center gap-3 text-[11px] text-slate-400 font-mono">
              <span>FPS: {{ vsHmd?.fps?.toFixed(1) ?? '--' }}</span>
              <span>Frame: {{ vsHmd?.frameIndex ?? '--' }}</span>
              <span class="truncate">TS: {{ fmtTs(vsHmd?.hostReceiveTimestamp) }}</span>
            </div>
          </div>

        </div>
      </div>
    </PageCard>

    <!-- IMU data panel -->
    <PageCard title="IMU 数据">
      <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
        <div class="rounded-lg border border-slate-200 p-3">
          <div class="text-xs font-medium tracking-[0.08em] text-slate-500 mb-2">Accelerometer</div>
          <div class="space-y-1 font-mono text-sm">
            <div class="flex justify-between"><span class="text-slate-500">X</span><span class="text-slate-800">{{ fmtVal(imuSample?.acc?.x) }}</span></div>
            <div class="flex justify-between"><span class="text-slate-500">Y</span><span class="text-slate-800">{{ fmtVal(imuSample?.acc?.y) }}</span></div>
            <div class="flex justify-between"><span class="text-slate-500">Z</span><span class="text-slate-800">{{ fmtVal(imuSample?.acc?.z) }}</span></div>
          </div>
        </div>
        <div class="rounded-lg border border-slate-200 p-3">
          <div class="text-xs font-medium tracking-[0.08em] text-slate-500 mb-2">Gyroscope</div>
          <div class="space-y-1 font-mono text-sm">
            <div class="flex justify-between"><span class="text-slate-500">X</span><span class="text-slate-800">{{ fmtVal(imuSample?.gyro?.x) }}</span></div>
            <div class="flex justify-between"><span class="text-slate-500">Y</span><span class="text-slate-800">{{ fmtVal(imuSample?.gyro?.y) }}</span></div>
            <div class="flex justify-between"><span class="text-slate-500">Z</span><span class="text-slate-800">{{ fmtVal(imuSample?.gyro?.z) }}</span></div>
          </div>
        </div>
        <div class="rounded-lg border border-slate-200 p-3">
          <div class="text-xs font-medium tracking-[0.08em] text-slate-500 mb-2">Quaternion</div>
          <div class="space-y-1 font-mono text-sm">
            <div class="flex justify-between"><span class="text-slate-500">W</span><span class="text-slate-800">{{ fmtVal(imuSample?.quat?.w) }}</span></div>
            <div class="flex justify-between"><span class="text-slate-500">X</span><span class="text-slate-800">{{ fmtVal(imuSample?.quat?.x) }}</span></div>
            <div class="flex justify-between"><span class="text-slate-500">Y</span><span class="text-slate-800">{{ fmtVal(imuSample?.quat?.y) }}</span></div>
            <div class="flex justify-between"><span class="text-slate-500">Z</span><span class="text-slate-800">{{ fmtVal(imuSample?.quat?.z) }}</span></div>
          </div>
        </div>
      </div>
      <div class="mt-3 flex items-center gap-4 text-xs text-slate-500">
        <span>Sample: <span class="font-mono font-medium text-slate-800">{{ imuStatus?.sampleIndex ?? '--' }}</span></span>
        <span>Rate: <span class="font-mono font-medium text-slate-800">{{ imuStatus?.sampleRate ? imuStatus.sampleRate + ' Hz' : '--' }}</span></span>
        <span>Timestamp: <span class="font-mono font-medium text-slate-800">{{ fmtTs(imuStatus?.hostReceiveTimestamp) }}</span></span>
        <span class="inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium"
          :class="badgeClass(imuStatus?.status)">
          <span class="inline-block h-1.5 w-1.5 rounded-full"
            :class="dotClass(imuStatus?.status)" />
          {{ badgeLabel(imuStatus?.status) }}
        </span>
      </div>
    </PageCard>

    <!-- Event log -->
    <PageCard title="Event Log">
      <div ref="logContainer"
        class="h-48 overflow-y-auto rounded-lg bg-slate-900 p-3 font-mono text-xs text-green-400 leading-relaxed">
        <div v-if="!status.logs?.length" class="text-slate-500">等待采集事件...</div>
        <div v-for="(line, i) in status.logs" :key="i">{{ line }}</div>
        <div ref="logEnd" />
      </div>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import BaseButton from "@/components/BaseButton.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import {
  createRealtimeSocket,
  fetchCurrentSession,
  fetchDevices,
  fetchSessionHistory,
  discardSession,
  saveSession,
  startSession,
  stopSession,
  uploadSession,
  type CollectorDevice,
  type ImuSample,
  type ImuSourceStatus,
  type RealtimeStatus,
  type VideoSourceStatus
} from "@/api/collector";
import { createTask, fetchTasks } from "@/api/tasks";
import type { TaskResponse } from "@/types/task";

// ── Reactive state ─────────────────────────────────────────────────────

const params = reactive({
  taskId: "TASK-001",
  taskName: "默认采集任务",
  subjectCode: "SUBJ-001",
  subjectName: "被试-001",
  actionName: "walking",
  profileCode: "BINOCULAR_HMD_IMU_V1",
  profileName: "默认规则"
});
const status = reactive<RealtimeStatus>(emptyStatus());
const wsConnected = ref(false);
const errorMsg = ref("");
const streamKey = ref(0);
const devices = ref<CollectorDevice[]>([]);
const logEnd = ref<HTMLElement | null>(null);
const logContainer = ref<HTMLElement | null>(null);

// Task selector
const tasks = ref<TaskResponse[]>([]);
const selectedTaskId = ref<number | null>(null);
const savedSessionIds = ref<string[]>([]);
const savingSession = ref(false);
const discardingSession = ref(false);
const selectedTaskName = computed(() => {
  const t = tasks.value.find((t) => t.id === selectedTaskId.value);
  return t ? `${t.taskName} (ID: ${t.id})` : "未选择任务";
});
const isPendingReview = computed(() => status.sessionPhase === "pending_review" || status.pendingAction === "review");
const isCurrentSessionSaved = computed(() => !!status.sessionId && savedSessionIds.value.includes(status.sessionId));
const needsReviewAction = computed(() => !!status.sessionId && !status.running && isPendingReview.value && !isCurrentSessionSaved.value);
const canUploadCurrentSession = computed(() => !!selectedTaskId.value && !!status.sessionId && !status.running && (!isPendingReview.value || isCurrentSessionSaved.value));
/*
const uploadBlockedReason = computed(() => {
  if (status.running) {
    return "";
  }
  if (!selectedTaskId.value && status.sessionId) {
    return "请先选择采集任务";
  }
  if (needsReviewAction.value) {
    return "当前 Session 还在待确认状态，请先保存或丢弃，再继续采集或上传。";
  }
  if (!selectedTaskId.value && status.sessionId) {
    return "Please select a task first.";
  }
  if (needsReviewAction.value) {
    return "褰撳墠 Session 杩樺湪寰呯‘璁ょ姸鎬侊紝璇峰厛瀹屾垚淇濆瓨/褰掓。鍐嶄笂浼犮€?";
  }
  return "";
});
*/
const uploadBlockedReason = computed(() => {
  if (status.running) {
    return "";
  }
  if (!selectedTaskId.value && status.sessionId) {
    return "Please select a task first.";
  }
  if (needsReviewAction.value) {
    return "This session is pending review. Please save or discard it before continuing.";
  }
  return "";
});
const showNewTaskDialog = ref(false);
const newTaskForm = reactive({
  taskName: "",
  subjectCode: "",
  actionName: "",
  deviceType: "双目相机+HMD+IMU",
  modality: "多模态",
  collectDate: new Date().toISOString().slice(0, 10),
  remark: ""
});
const creatingTask = ref(false);

let ws: WebSocket | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let mounted = true;

const uploading = ref(false);
const uploadStage = ref<"idle" | "packaging" | "uploading" | "done" | "error">("idle");
const uploadProgress = ref(0);
const uploadMsg = ref("");
const uploadOk = ref(false);
const uploadedFiles = ref<{ name: string; objectKey: string }[]>([]);
const showUploadDialog = ref(false);
const uploadForm = reactive({ platformUrl: "http://localhost:19021" });

// ── Computed video status per source ───────────────────────────────────

const vsLeft  = computed(() => status.sources?.left as VideoSourceStatus | undefined);
const vsRight = computed(() => status.sources?.right as VideoSourceStatus | undefined);
const vsHmd   = computed(() => status.sources?.hmd as VideoSourceStatus | undefined);
const imuStatus = computed(() => status.sources?.imu as ImuSourceStatus | undefined);
const imuSample = computed<ImuSample | undefined>(() => imuStatus.value?.latest);

// ── Device helpers ─────────────────────────────────────────────────────

function devAvailable(key: string): boolean {
  return devices.value.find((d) => d.id === `source-${key}`)?.available ?? false;
}

function mjpegUrl(source: string): string {
  return `http://localhost:19022/stream/${source}?k=${streamKey.value}`;
}

// ── Badge helpers ──────────────────────────────────────────────────────

function badgeClass(s?: string) {
  if (s === "missing") return "bg-amber-50 text-amber-700 ring-1 ring-amber-200";
  if (s === "running") return "bg-green-50 text-green-700 ring-1 ring-green-200";
  return "bg-slate-100 text-slate-500 ring-1 ring-slate-200";
}
function dotClass(s?: string) {
  if (s === "missing") return "bg-amber-500";
  if (s === "running") return "bg-green-500";
  return "bg-slate-400";
}
function badgeLabel(s?: string) {
  if (s === "missing") return "MISSING";
  if (s === "running") return "RUNNING";
  return "STOPPED";
}

// ── WebSocket ──────────────────────────────────────────────────────────

function connectWs() {
  if (!mounted) return;
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;
  try { ws = createRealtimeSocket(); } catch { scheduleReconnect(); return; }
  ws.onopen = () => { wsConnected.value = true; errorMsg.value = ""; };
  ws.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data) as RealtimeStatus;
      if (data.type === "realtime_status") { Object.assign(status, data); scrollLogToBottom(); }
    } catch { /* ignore */ }
  };
  ws.onclose = () => { wsConnected.value = false; scheduleReconnect(); };
  ws.onerror = () => ws?.close();
}
function scheduleReconnect() {
  if (!mounted) return;
  if (reconnectTimer) clearTimeout(reconnectTimer);
  reconnectTimer = setTimeout(connectWs, 2000);
}
function disconnectWs() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
  if (ws) { ws.onclose = null; ws.onerror = null; ws.onmessage = null; ws.close(); ws = null; }
  wsConnected.value = false;
}

// ── Task management ─────────────────────────────────────────────────────

function onTaskSelect() {
  const task = tasks.value.find((t) => t.id === selectedTaskId.value);
  if (task) {
    params.taskId = String(task.id);
    params.taskName = task.taskName || params.taskName;
    params.subjectCode = task.subjectCode || params.subjectCode;
    params.subjectName = task.subjectName || params.subjectName;
    params.actionName = task.actionName || params.actionName;
    params.profileName = task.profileName || params.profileName;
  }
}

async function fetchTaskList() {
  try {
    const page = await fetchTasks(1, 100);
    tasks.value = page.records ?? [];
  } catch { /* offline */ }
}

async function refreshSavedSessions() {
  try {
    const history = await fetchSessionHistory();
    savedSessionIds.value = (history.sessions ?? [])
      .filter((session: any) => session?.archiveExists || session?.archiveFile)
      .map((session: any) => String(session.sessionId));
  } catch {
    savedSessionIds.value = [];
  }
}

async function handleCreateTask() {
  if (!newTaskForm.taskName.trim()) return;
  creatingTask.value = true;
  try {
    const created = await createTask({
      taskName: newTaskForm.taskName.trim(),
      subjectCode: newTaskForm.subjectCode.trim(),
      actionName: newTaskForm.actionName.trim(),
      deviceType: newTaskForm.deviceType,
      modality: newTaskForm.modality,
      collectDate: newTaskForm.collectDate,
      remark: newTaskForm.remark.trim() || "-"
    });
    await fetchTaskList();
    selectedTaskId.value = created.id;
    onTaskSelect();
    showNewTaskDialog.value = false;
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : "创建任务失败";
  }
  creatingTask.value = false;
}

// ── Actions ────────────────────────────────────────────────────────────

async function handleStart() {
  errorMsg.value = "";
  try {
    const result = await startSession({
      taskId: params.taskId,
      taskName: params.taskName,
      subjectCode: params.subjectCode,
      subjectName: params.subjectName,
      actionName: params.actionName,
      profileCode: params.profileCode,
      profileName: params.profileName
    });
    if (!result.success) { errorMsg.value = result.message || "启动失败"; return; }
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : "启动失败";
  }
}
async function handleStop() {
  errorMsg.value = "";
  try {
    const result = await stopSession();
    if (!result.success) errorMsg.value = result.message || "停止失败";
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : "停止失败";
  }
}

/*
async function handleSavePendingSession() {
  savingSession.value = true;
  errorMsg.value = "";
  try {
    const result = await saveSession();
    if (!result.success) {
      errorMsg.value = result.message || "保存采集失败";
      return;
    }
    const savedId = result.savedSession?.sessionId || status.sessionId;
    if (savedId) {
      savedSessionIds.value = Array.from(new Set([...savedSessionIds.value, String(savedId)]));
    }
    uploadMsg.value = result.message || "采集已保存，可继续上传";
    uploadOk.value = true;
    await refreshSavedSessions();
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : "保存采集失败";
  } finally {
    savingSession.value = false;
  }
}

async function handleDiscardPendingSession() {
  discardingSession.value = true;
  errorMsg.value = "";
  try {
    const result = await discardSession();
    if (!result.success) {
      errorMsg.value = result.message || "丢弃采集失败";
      return;
    }
    if (status.sessionId) {
      savedSessionIds.value = savedSessionIds.value.filter((sessionId) => sessionId !== status.sessionId);
    }
    uploadMsg.value = result.message || "已丢弃本次采集";
    uploadOk.value = true;
    showUploadDialog.value = false;
    Object.assign(status, emptyStatus());
    await refreshSavedSessions();
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : "丢弃采集失败";
  } finally {
    discardingSession.value = false;
  }
}

let uploadProgressTimer: ReturnType<typeof setInterval> | null = null;

async function handleUpload() {
  if (!canUploadCurrentSession.value) {
    uploadStage.value = "error";
    uploadMsg.value = uploadBlockedReason.value || "褰撳墠 Session 鏆傛椂涓嶅彲涓婁紶";
    uploadOk.value = false;
    return;
  }

  uploading.value = true;
  uploadStage.value = "packaging";
  uploadProgress.value = 0;
  uploadMsg.value = "";
  uploadOk.value = false;
  uploadedFiles.value = [];

  if (selectedTaskId.value == null) {
    uploadStage.value = "error";
    uploadMsg.value = "未选择采集任务";
    uploadOk.value = false;
    uploading.value = false;
    return;
  }

  // Drive progress during packaging (0→30%) and uploading (30→90%)
  uploadProgressTimer = setInterval(() => {
    if (uploadStage.value === "packaging") {
      uploadProgress.value = Math.min(uploadProgress.value + 3, 30);
    } else if (uploadStage.value === "uploading") {
      uploadProgress.value = Math.min(uploadProgress.value + 2, 90);
    }
  }, 200);

  try {
    await new Promise((r) => setTimeout(r, 600));
    uploadStage.value = "uploading";
    uploadProgress.value = Math.max(uploadProgress.value, 30);
    const result = await uploadSession({
      platformUrl: uploadForm.platformUrl,
      platformTaskId: selectedTaskId.value!,
      sessionId: status.sessionId ?? undefined
    });
    if (result.success) {
      uploadStage.value = "done";
      uploadProgress.value = 100;
      uploadMsg.value = result.message || "上传成功！";
      uploadOk.value = true;
      if (result.platformResponse?.assets) {
        uploadedFiles.value = result.platformResponse.assets.map((a: any) => ({
          name: a.displayName || a.originalFilename || "file",
          objectKey: a.objectKey || "-"
        }));
      }
    } else {
      uploadStage.value = "error";
      uploadMsg.value = result.message || "上传失败";
      uploadOk.value = false;
    }
  } catch (e) {
    uploadStage.value = "error";
    uploadMsg.value = e instanceof Error ? e.message : "上传失败";
    uploadOk.value = false;
  } finally {
    if (uploadProgressTimer) { clearInterval(uploadProgressTimer); uploadProgressTimer = null; }
    uploading.value = false;
  }
}

function closeUploadDialog() {
  if (uploadProgressTimer) { clearInterval(uploadProgressTimer); uploadProgressTimer = null; }
  showUploadDialog.value = false;
  uploadStage.value = "idle";
  uploadProgress.value = 0;
  uploadMsg.value = "";
  uploadedFiles.value = [];
}

*/
async function handleSavePendingSession() {
  savingSession.value = true;
  errorMsg.value = "";
  try {
    const result = await saveSession();
    if (!result.success) {
      errorMsg.value = result.message || "Failed to save the collection.";
      return;
    }
    const savedId = result.savedSession?.sessionId || status.sessionId;
    if (savedId) {
      savedSessionIds.value = Array.from(new Set([...savedSessionIds.value, String(savedId)]));
    }
    uploadMsg.value = result.message || "Collection saved. You can upload it now.";
    uploadOk.value = true;
    await refreshSavedSessions();
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : "Failed to save the collection.";
  } finally {
    savingSession.value = false;
  }
}

async function handleDiscardPendingSession() {
  discardingSession.value = true;
  errorMsg.value = "";
  try {
    const result = await discardSession();
    if (!result.success) {
      errorMsg.value = result.message || "Failed to discard the collection.";
      return;
    }
    if (status.sessionId) {
      savedSessionIds.value = savedSessionIds.value.filter((sessionId) => sessionId !== status.sessionId);
    }
    uploadMsg.value = result.message || "Collection discarded.";
    uploadOk.value = true;
    showUploadDialog.value = false;
    Object.assign(status, emptyStatus());
    await refreshSavedSessions();
  } catch (e) {
    errorMsg.value = e instanceof Error ? e.message : "Failed to discard the collection.";
  } finally {
    discardingSession.value = false;
  }
}

let uploadProgressTimer: ReturnType<typeof setInterval> | null = null;

async function handleUpload() {
  if (!canUploadCurrentSession.value) {
    uploadStage.value = "error";
    uploadMsg.value = uploadBlockedReason.value || "This session cannot be uploaded right now.";
    uploadOk.value = false;
    return;
  }

  uploading.value = true;
  uploadStage.value = "packaging";
  uploadProgress.value = 0;
  uploadMsg.value = "";
  uploadOk.value = false;
  uploadedFiles.value = [];

  if (selectedTaskId.value == null) {
    uploadStage.value = "error";
    uploadMsg.value = "Please select a task first.";
    uploadOk.value = false;
    uploading.value = false;
    return;
  }

  uploadProgressTimer = setInterval(() => {
    if (uploadStage.value === "packaging") {
      uploadProgress.value = Math.min(uploadProgress.value + 3, 30);
    } else if (uploadStage.value === "uploading") {
      uploadProgress.value = Math.min(uploadProgress.value + 2, 90);
    }
  }, 200);

  try {
    await new Promise((r) => setTimeout(r, 600));
    uploadStage.value = "uploading";
    uploadProgress.value = Math.max(uploadProgress.value, 30);
    const result = await uploadSession({
      platformUrl: uploadForm.platformUrl,
      platformTaskId: selectedTaskId.value!,
      sessionId: status.sessionId ?? undefined
    });
    if (result.success) {
      uploadStage.value = "done";
      uploadProgress.value = 100;
      uploadMsg.value = result.message || "Upload completed.";
      uploadOk.value = true;
      if (result.platformResponse?.assets) {
        uploadedFiles.value = result.platformResponse.assets.map((a: any) => ({
          name: a.displayName || a.originalFilename || "file",
          objectKey: a.objectKey || "-"
        }));
      }
    } else {
      uploadStage.value = "error";
      uploadMsg.value = result.message || "Upload failed.";
      uploadOk.value = false;
    }
  } catch (e) {
    uploadStage.value = "error";
    uploadMsg.value = e instanceof Error ? e.message : "Upload failed.";
    uploadOk.value = false;
  } finally {
    if (uploadProgressTimer) { clearInterval(uploadProgressTimer); uploadProgressTimer = null; }
    uploading.value = false;
  }
}

function closeUploadDialog() {
  if (uploadProgressTimer) { clearInterval(uploadProgressTimer); uploadProgressTimer = null; }
  showUploadDialog.value = false;
  uploadStage.value = "idle";
  uploadProgress.value = 0;
  uploadMsg.value = "";
  uploadedFiles.value = [];
}

const stageColor = computed(() => {
  if (uploadStage.value === "done") return "text-green-600";
  if (uploadStage.value === "error") return "text-red-500";
  return "text-blue-600";
});

/*
const stageLabel = computed(() => {
  switch (uploadStage.value) {
    case "packaging": return "正在打包 Session 文件...";
    case "uploading": return "正在上传到平台...";
    case "done": return "上传完成！";
    case "error": return "上传失败";
    default: return "";
  }
});

// ── Formatting ─────────────────────────────────────────────────────────

*/
const stageLabel = computed(() => {
  switch (uploadStage.value) {
    case "packaging": return "Packaging session files...";
    case "uploading": return "Uploading to the platform...";
    case "done": return "Upload completed.";
    case "error": return "Upload failed.";
    default: return "";
  }
});

function formatElapsed(ms: number): string {
  if (!ms || ms <= 0) return "00:00";
  const s = Math.floor(ms / 1000);
  return `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}
function fmtTs(ts?: number): string { return ts && ts > 0 ? String(ts) : "--"; }
function fmtVal(v?: number): string { return v != null ? v.toFixed(4) : "--"; }
function scrollLogToBottom() { nextTick(() => { if (logContainer.value) { logContainer.value.scrollTop = logContainer.value.scrollHeight; } }); }

function emptyStatus(): RealtimeStatus {
  return {
    type: "realtime_status", sessionId: null, running: false, elapsedMs: 0,
    sources: {
      left:  { type:"video", status:"stopped", frameIndex:0, fps:0, videoTimeMs:0, hostReceiveTimestamp:0 },
      right: { type:"video", status:"stopped", frameIndex:0, fps:0, videoTimeMs:0, hostReceiveTimestamp:0 },
      hmd:   { type:"video", status:"stopped", frameIndex:0, fps:0, videoTimeMs:0, hostReceiveTimestamp:0 },
      imu:   { type:"imu", status:"stopped", sampleIndex:0, sampleRate:0,
               latest:{ acc:{x:0,y:0,z:0}, gyro:{x:0,y:0,z:0}, quat:{w:0,x:0,y:0,z:0} },
               hostReceiveTimestamp:0 }
    },
    logs: []
  };
}

// ── Lifecycle ──────────────────────────────────────────────────────────

onMounted(async () => {
  try { devices.value = await fetchDevices(); } catch { /* offline */ }
  try { const cur = await fetchCurrentSession(); if (cur) Object.assign(status, cur); } catch { /* offline */ }
  await fetchTaskList();
  await refreshSavedSessions();
  connectWs();
});
onBeforeUnmount(() => { mounted = false; disconnectWs(); });
</script>
