<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>采集会话</h1>
        <p>按采集会话查看数据 · 质检 · 导出 · 回放状态</p>
      </div>
    </div>

    <!-- metrics -->
    <div class="light2-metrics">
      <div v-for="m in metricItems" :key="m.label" class="light2-mcard">
        <div class="light2-mstripe" :style="{ background: m.tone === 'session' ? '#0d9444' : m.tone === 'asset' ? '#7c3aed' : m.tone === 'export' ? '#0d8ea0' : '#c5222f' }" />
        <div class="light2-mlabel">{{ m.label }}</div>
        <div class="light2-mvalue">{{ m.value }}</div>
      </div>
    </div>

    <!-- filters -->
    <div class="light2-filters">
      <input v-model="listQuery.sessionNumber" type="text" placeholder="搜索采集编号 / 任务 / 被试..." class="light2-input" @keyup.enter="applyQuery" />
      <div class="light2-sel">
        <select v-model="listQuery.qcStatus" @change="applyQuery">
          <option value="">全部 QC</option>
          <option v-for="o in qcOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <div class="light2-sel">
        <select v-model="listQuery.exportStatus" @change="applyQuery">
          <option value="">全部导出</option>
          <option v-for="o in exportOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <div class="light2-sel">
        <select v-model="sortState.field" @change="updateSortField(sortState.field)">
          <option v-for="o in sortOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="applyQuery">搜索</button>
      <button class="light2-btn light2-btn-sec light2-btn-sm" @click="resetQuery">重置</button>
    </div>

    <!-- table -->
    <div class="light2-tbl overflow-x-auto">
      <table class="min-w-[1420px]">
        <thead>
          <tr>
            <th>采集</th>
            <th>所属任务</th>
            <th>数据总大小</th>
            <th>时长</th>
            <th>被试 - 动作</th>
            <th>采集员</th>
            <th>上传时间</th>
            <th>配置</th>
            <th>资产 / 文件</th>
            <th style="width:140px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="10" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">正在加载采集列表...</td>
          </tr>
          <tr v-else-if="!displayedList.length">
            <td colspan="10" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">{{ emptyText }}</td>
          </tr>
          <tr v-for="session in displayedList" :key="session.sessionId">
            <td>
              <RouterLink :to="`/sessions/${session.sessionId}`" class="light2-code">{{ session.sessionCode || session.sessionId }}</RouterLink>
              <div v-if="session.sessionCode && session.sessionCode !== session.sessionId" class="light2-tsub">{{ session.sessionId }}</div>
            </td>
            <td>
              <RouterLink :to="`/acquisition/${session.taskId}`" class="light2-tname hover:underline">{{ session.taskName || "-" }}</RouterLink>
              <div class="light2-tsub">{{ session.taskCode || `#${session.taskId}` }}</div>
            </td>
            <td>{{ formatFileSize(session.totalSize || 0) }}</td>
            <td>-</td>
            <td>{{ session.subjectCode || "-" }} - {{ session.actionName || "-" }}</td>
            <td>{{ session.collectorName || "-" }}</td>
            <td>{{ formatDateTime(session.uploadedAt ?? undefined) }}</td>
            <td>{{ session.profileName || "-" }}</td>
            <td class="light2-code">{{ session.assetCount ?? 0 }}/{{ session.fileCount ?? 0 }}</td>
            <td>
              <div class="light2-actions">
                <RouterLink :to="`/play/${session.sessionId}`" class="light2-btn light2-btn-sec light2-btn-sm">播放</RouterLink>
                <RouterLink :to="`/sessions/${session.sessionId}`" class="light2-btn light2-btn-sec light2-btn-sm">详情</RouterLink>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- pagination -->
    <div class="light2-pg">
      <span>第 {{ pageState.page }} 页，共 {{ Math.max(1, Math.ceil(pageState.total / pageState.pageSize)) }} 页 · 总计 {{ pageState.total }} 条</span>
      <div class="light2-pg-btns">
        <button :disabled="pageState.page <= 1" @click="updatePage(pageState.page - 1)">← 上一页</button>
        <button :disabled="pageState.page >= Math.ceil(pageState.total / pageState.pageSize)" @click="updatePage(pageState.page + 1)">下一页 →</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch, watchEffect } from "vue";
import { RouterLink, useRoute } from "vue-router";
import { fetchSessions } from "@/api/sessions";
import type { SessionListItem } from "@/types/session";
import { formatDateTime, formatFileSize } from "@/utils/format";

type SessionListQueryState = {
  taskNumber: string;
  sessionNumber: string;
  qcStatus: string;
  uploadStatus: string;
  exportStatus: string;
  modality: string;
  startedAtFrom: string;
  startedAtTo: string;
};

type SessionSortState = {
  field: "createdAt" | "startedAt" | "totalSize" | "fileCount" | "assetCount";
  order: "asc" | "desc";
};

const route = useRoute();

const rawList = ref<SessionListItem[]>([]);
const loading = ref(false);
const hasLoaded = ref(false);

const pageState = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
});

const routeTaskId = computed(() => String(route.query.taskId ?? "").trim());
const routeSessionKeyword = computed(() => String(route.query.sessionId ?? route.query.sessionCode ?? "").trim());
const listQuery = reactive(createDefaultQuery(routeTaskId.value, routeSessionKeyword.value));
const appliedQuery = reactive(createDefaultQuery(routeTaskId.value, routeSessionKeyword.value));
const sortState = reactive<SessionSortState>({
  field: "createdAt",
  order: "desc",
});

const qcOptions = [
  { label: "待处理", value: "PENDING" },
  { label: "质检通过", value: "QC_PASSED" },
  { label: "质检警告", value: "QC_WARNING" },
  { label: "质检失败", value: "QC_FAILED" },
];

const exportOptions = [
  { label: "待导出", value: "PENDING" },
  { label: "可导出", value: "READY" },
];

const sortOptions = [
  { label: "最近导入", value: "createdAt" },
  { label: "时间降序", value: "startedAt" },
  { label: "总大小", value: "totalSize" },
  { label: "文件数", value: "fileCount" },
  { label: "资产数", value: "assetCount" },
];

const filteredList = computed(() => rawList.value.filter((item) => matchesSessionQuery(item, appliedQuery)));
const sortedList = computed(() => sortSessionList(filteredList.value, sortState));
const displayedList = computed(() => slicePage(sortedList.value, pageState.page, pageState.pageSize));

const metricItems = computed(() => {
  const pendingStatuses = new Set(["PENDING", "WAITING", "FAILED", "ERROR", "QC_FAILED", "QC_WARNING", "RUNNING"]);
  const pendingCount = rawList.value.filter(
    (session) =>
      pendingStatuses.has(session.qcStatus) ||
      pendingStatuses.has(session.uploadStatus) ||
      pendingStatuses.has(session.exportStatus),
  ).length;
  return [
    { label: "采集总数", value: rawList.value.length, tone: "session" },
    { label: "资产总数", value: rawList.value.reduce((sum, session) => sum + (session.assetCount ?? 0), 0), tone: "asset" },
    { label: "文件总数", value: rawList.value.reduce((sum, session) => sum + (session.fileCount ?? 0), 0), tone: "export" },
    { label: "待处理", value: pendingCount, tone: "qc" },
  ];
});

const emptyText = computed(() => {
  if (!hasLoaded.value || loading.value) {
    return "正在加载采集数据...";
  }
  return rawList.value.length === 0 ? "暂无采集数据" : "当前筛选条件下没有匹配结果";
});

watchEffect(() => {
  pageState.total = sortedList.value.length;
  const totalPages = Math.max(1, Math.ceil(pageState.total / pageState.pageSize));
  if (pageState.page > totalPages) {
    pageState.page = totalPages;
  }
});

function createDefaultQuery(taskId = "", sessionKeyword = ""): SessionListQueryState {
  return {
    taskNumber: taskId,
    sessionNumber: sessionKeyword,
    qcStatus: "",
    uploadStatus: "",
    exportStatus: "",
    modality: "",
    startedAtFrom: "",
    startedAtTo: "",
  };
}

async function loadSessionRawList() {
  const page = await fetchSessions({ page: 1, pageSize: 500, sortBy: "createdAt", sortOrder: "desc" });
  return page.records;
}

function syncAppliedQuery() {
  Object.assign(appliedQuery, listQuery);
}

function matchesSessionQuery(session: SessionListItem, query: SessionListQueryState) {
  const taskNumber = query.taskNumber.trim().toLowerCase();
  const sessionNumber = query.sessionNumber.trim().toLowerCase();
  const modality = query.modality.trim().toLowerCase();
  const startedAt = String(session.startedAt || session.createdAt || "").slice(0, 10);

  const matchesTask =
    !taskNumber ||
    String(session.taskId).toLowerCase() === taskNumber ||
    String(session.taskCode || "").toLowerCase() === taskNumber;

  const matchesSession =
    !sessionNumber ||
    String(session.sessionId || "").toLowerCase() === sessionNumber ||
    String(session.sessionCode || "").toLowerCase() === sessionNumber;

  const matchesQc = !query.qcStatus || session.qcStatus === query.qcStatus;
  const matchesUpload = !query.uploadStatus || session.uploadStatus === query.uploadStatus;
  const matchesExport = !query.exportStatus || session.exportStatus === query.exportStatus;
  const matchesModality =
    !modality ||
    String(session.modality || "").toLowerCase().includes(modality) ||
    String(session.sourceSummary || "").toLowerCase().includes(modality);
  const matchesFrom = !query.startedAtFrom || startedAt >= query.startedAtFrom;
  const matchesTo = !query.startedAtTo || startedAt <= query.startedAtTo;

  return matchesTask && matchesSession && matchesQc && matchesUpload && matchesExport && matchesModality && matchesFrom && matchesTo;
}

function sortSessionList(list: SessionListItem[], state: SessionSortState) {
  return [...list].sort((left, right) => {
    const leftValue = getSessionSortValue(left, state.field);
    const rightValue = getSessionSortValue(right, state.field);

    if (leftValue === rightValue) {
      return 0;
    }

    if (state.order === "asc") {
      return leftValue > rightValue ? 1 : -1;
    }
    return leftValue < rightValue ? 1 : -1;
  });
}

function getSessionSortValue(session: SessionListItem, field: SessionSortState["field"]) {
  switch (field) {
    case "createdAt":
      return session.createdAt || "";
    case "totalSize":
      return session.totalSize ?? 0;
    case "fileCount":
      return session.fileCount ?? 0;
    case "assetCount":
      return session.assetCount ?? 0;
    default:
      return session.startedAt || session.createdAt || "";
  }
}

function slicePage<T>(list: T[], page: number, pageSize: number) {
  const start = (page - 1) * pageSize;
  return list.slice(start, start + pageSize);
}

async function loadSessions() {
  loading.value = true;
  try {
    rawList.value = await loadSessionRawList();
    hasLoaded.value = true;
  } catch {
    rawList.value = [];
    hasLoaded.value = true;
  } finally {
    loading.value = false;
  }
}

function applyQuery() {
  syncAppliedQuery();
  pageState.page = 1;
}

function resetQuery() {
  const next = createDefaultQuery(routeTaskId.value, routeSessionKeyword.value);
  Object.assign(listQuery, next);
  Object.assign(appliedQuery, next);
  pageState.page = 1;
  pageState.pageSize = 10;
  sortState.field = "createdAt";
  sortState.order = "desc";
}

function updateSortField(value: string) {
  sortState.field = value as SessionSortState["field"];
  pageState.page = 1;
}

function updatePage(page: number) {
  pageState.page = page;
}

watch(
  () => [routeTaskId.value, routeSessionKeyword.value],
  ([taskId, sessionKeyword]) => {
    Object.assign(listQuery, createDefaultQuery(taskId, sessionKeyword));
    Object.assign(appliedQuery, createDefaultQuery(taskId, sessionKeyword));
    pageState.page = 1;
  },
);

onMounted(() => {
  syncAppliedQuery();
  void loadSessions();
});
</script>
