<template>
  <div class="space-y-5">
    <PageHeader
      surface="plain"
      eyebrow="功能 / 采集"
      title="采集"
      description="查看采集、资产规模与状态，并继续播放、导出或进入详情。"
    />

    <BusinessMetrics :items="metricItems" />

    <section class="app-list-panel">
      <div class="app-list-panel-section app-list-panel-section-muted">
        <SearchActionBar
          v-model="listQuery"
          :core-fields="coreFilterFields"
          :advanced-fields="advancedFilterFields"
          :advanced-open="advancedOpen"
          :advanced-active="advancedActive"
          search-tone="session"
          @update:advanced-open="advancedOpen = $event"
          @search="applyQuery"
          @reset="resetQuery"
        >
          <template #advanced>
            <SortToolbar
              compact
              :sort-field="sortState.field"
              :sort-order="sortState.order"
              :page-size="pageState.pageSize"
              :sort-options="sortOptions"
              @update:sort-field="updateSortField"
              @update:sort-order="updateSortOrder"
              @update:page-size="updatePageSize"
            />
          </template>
        </SearchActionBar>
      </div>

      <div class="app-column-strip lg:grid-cols-[minmax(0,1.35fr)_minmax(0,1.2fr)_minmax(240px,1fr)_minmax(190px,0.8fr)_auto]">
        <div>采集</div>
        <div>上下文</div>
        <div>资产摘要</div>
        <div class="text-right">状态</div>
        <div class="text-right">操作</div>
      </div>

      <div v-if="loading" class="px-5 py-12 text-center text-sm text-[var(--color-text-secondary)]">
        正在加载采集列表...
      </div>

      <div v-else-if="displayedList.length" class="app-summary-list">
        <article v-for="session in displayedList" :key="session.sessionId" class="app-summary-row app-row-accent-session">
          <div class="grid gap-4 px-5 py-4 lg:grid-cols-[minmax(0,1.35fr)_minmax(0,1.2fr)_minmax(240px,1fr)_minmax(190px,0.8fr)_auto] lg:items-center">
            <section class="min-w-0">
              <div class="flex items-start gap-3">
                <div class="app-metric-icon app-tone-session mt-0.5 h-8 w-8 shrink-0">
                  <BaseIcon name="camera" size="sm" />
                </div>
                <div class="min-w-0 space-y-1">
                  <div class="app-summary-title-strong truncate">{{ session.sessionCode || session.sessionId }}</div>
                  <div class="app-summary-subtitle-muted truncate">
                    {{ session.taskCode || `#${session.taskId}` }} · {{ session.taskName }}
                  </div>
                </div>
              </div>
            </section>

            <section class="min-w-0">
              <div class="app-summary-meta-inline">
                <span><strong>被试</strong> {{ session.subjectCode || "-" }}</span>
                <span>·</span>
                <span><strong>动作</strong> {{ session.actionName || "-" }}</span>
                <span>·</span>
                <span><strong>Profile</strong> {{ session.profileName || "-" }}</span>
                <span>·</span>
                <span><strong>时间</strong> {{ formatDateTime(session.startedAt || session.createdAt) }}</span>
              </div>
            </section>

            <section class="min-w-0 space-y-1">
              <div class="app-summary-meta-inline">
                <span><strong>模态</strong> {{ session.modality || "-" }}</span>
              </div>
              <div class="app-summary-stat-inline">
                <span>资产 <strong>{{ session.assetCount ?? 0 }}</strong></span>
                <span>·</span>
                <span>文件 <strong>{{ session.fileCount ?? 0 }}</strong></span>
                <span>·</span>
                <span>{{ formatFileSize(session.totalSize ?? 0) }}</span>
              </div>
              <div class="app-summary-subtitle-muted truncate">{{ session.sourceSummary || "暂无资产摘要" }}</div>
            </section>

            <section class="min-w-0">
              <div class="app-status-stack justify-start lg:justify-end">
                <StatusBadge :status="session.uploadStatus" />
                <StatusBadge :status="session.qcStatus" />
                <StatusBadge :status="session.exportStatus" />
              </div>
            </section>

            <section class="min-w-0">
              <div class="app-action-group">
                <BaseButton size="sm" variant="soft" tone="session" :to="`/play/${session.sessionId}`">
                  <BaseIcon name="play" size="sm" />
                  播放
                </BaseButton>
                <BaseButton size="sm" variant="ghost" :to="`/sessions/${session.sessionId}`">详情</BaseButton>
                <BaseButton
                  size="sm"
                  variant="secondary"
                  tone="export"
                  :to="session.exportStatus === 'READY' ? `/export?sessionId=${session.sessionId}` : undefined"
                  :disabled="session.exportStatus !== 'READY'"
                >
                  导出
                </BaseButton>
              </div>
            </section>
          </div>
        </article>
      </div>

      <div v-else class="px-5 py-12 text-center text-sm text-[var(--color-text-secondary)]">
        {{ emptyText }}
      </div>

      <ListPagination
        :page="pageState.page"
        :page-size="pageState.pageSize"
        :total="pageState.total"
        @update:page="updatePage"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch, watchEffect } from "vue";
import { useRoute } from "vue-router";
import { fetchSessions } from "@/api/sessions";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import BusinessMetrics, { type MetricItem } from "@/components/BusinessMetrics.vue";
import ListPagination from "@/components/ListPagination.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import SortToolbar, { type SortOption } from "@/components/SortToolbar.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { FilterField } from "@/components/FilterBar.vue";
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
  field: "startedAt" | "totalSize" | "fileCount" | "assetCount";
  order: "asc" | "desc";
};

const route = useRoute();

const rawList = ref<SessionListItem[]>([]);
const loading = ref(false);
const hasLoaded = ref(false);
const advancedOpen = ref(false);

const pageState = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
});

const routeTaskId = computed(() => String(route.query.taskId ?? "").trim());
const listQuery = reactive(createDefaultQuery(routeTaskId.value));
const appliedQuery = reactive(createDefaultQuery(routeTaskId.value));
const sortState = reactive<SessionSortState>({
  field: "startedAt",
  order: "desc",
});

const qcOptions = [
  { label: "待处理", value: "PENDING" },
  { label: "质检通过", value: "QC_PASSED" },
  { label: "质检警告", value: "QC_WARNING" },
  { label: "质检失败", value: "QC_FAILED" },
];

const uploadOptions = [
  { label: "待上传", value: "PENDING" },
  { label: "上传成功", value: "SUCCESS" },
  { label: "上传失败", value: "FAILED" },
  { label: "已上传", value: "UPLOADED" },
];

const exportOptions = [
  { label: "待导出", value: "PENDING" },
  { label: "可导出", value: "READY" },
];

const coreFilterFields: FilterField[] = [
  { key: "taskNumber", label: "任务编号", placeholder: "任务 ID 或 taskCode" },
  { key: "sessionNumber", label: "采集编号", placeholder: "sessionId 或 sessionCode" },
  { key: "qcStatus", label: "质检状态", type: "select", options: qcOptions },
];

const advancedFilterFields: FilterField[] = [
  { key: "uploadStatus", label: "上传状态", type: "select", options: uploadOptions },
  { key: "exportStatus", label: "导出状态", type: "select", options: exportOptions },
  { key: "modality", label: "模态 / 数据类型", placeholder: "例如 IMU / 视频" },
  { key: "startedAtFrom", label: "采集时间从", type: "date" },
  { key: "startedAtTo", label: "采集时间到", type: "date" },
];

const sortOptions: SortOption[] = [
  { label: "采集时间", value: "startedAt" },
  { label: "总大小", value: "totalSize" },
  { label: "文件数", value: "fileCount" },
  { label: "资产数", value: "assetCount" },
];

const filteredList = computed(() => rawList.value.filter((item) => matchesSessionQuery(item, appliedQuery)));
const sortedList = computed(() => sortSessionList(filteredList.value, sortState));
const displayedList = computed(() => slicePage(sortedList.value, pageState.page, pageState.pageSize));

const metricItems = computed<MetricItem[]>(() => {
  const pendingStatuses = new Set(["PENDING", "WAITING", "FAILED", "ERROR", "QC_FAILED", "QC_WARNING", "RUNNING"]);
  const pendingCount = rawList.value.filter(
    (session) =>
      pendingStatuses.has(session.qcStatus) ||
      pendingStatuses.has(session.uploadStatus) ||
      pendingStatuses.has(session.exportStatus),
  ).length;
  return [
    { label: "采集总数", value: rawList.value.length, icon: "camera", tone: "session" },
    { label: "资产总数", value: rawList.value.reduce((sum, session) => sum + (session.assetCount ?? 0), 0), icon: "hard-drive", tone: "asset" },
    { label: "文件总数", value: rawList.value.reduce((sum, session) => sum + (session.fileCount ?? 0), 0), icon: "database", tone: "export" },
    { label: "异常 / 待处理", value: pendingCount, icon: "shield", tone: "qc" },
  ];
});

const emptyText = computed(() => {
  if (!hasLoaded.value || loading.value) {
    return "正在加载采集数据...";
  }
  return rawList.value.length === 0 ? "暂无采集数据" : "当前筛选条件下没有匹配结果";
});

const advancedActive = computed(() => {
  const queryActive = Boolean(
    listQuery.uploadStatus ||
      listQuery.exportStatus ||
      listQuery.modality ||
      listQuery.startedAtFrom ||
      listQuery.startedAtTo,
  );
  return queryActive || sortState.field !== "startedAt" || sortState.order !== "desc" || pageState.pageSize !== 10;
});

watchEffect(() => {
  pageState.total = sortedList.value.length;
  const totalPages = Math.max(1, Math.ceil(pageState.total / pageState.pageSize));
  if (pageState.page > totalPages) {
    pageState.page = totalPages;
  }
});

watchEffect(() => {
  if (advancedActive.value) {
    advancedOpen.value = true;
  }
});

function createDefaultQuery(taskId = ""): SessionListQueryState {
  return {
    taskNumber: taskId,
    sessionNumber: "",
    qcStatus: "",
    uploadStatus: "",
    exportStatus: "",
    modality: "",
    startedAtFrom: "",
    startedAtTo: "",
  };
}

async function loadSessionRawList() {
  const page = await fetchSessions({ page: 1, pageSize: 500 });
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
  const next = createDefaultQuery(routeTaskId.value);
  Object.assign(listQuery, next);
  Object.assign(appliedQuery, next);
  pageState.page = 1;
  pageState.pageSize = 10;
  sortState.field = "startedAt";
  sortState.order = "desc";
  advancedOpen.value = false;
}

function updateSortField(value: string) {
  sortState.field = value as SessionSortState["field"];
  pageState.page = 1;
}

function updateSortOrder(value: "asc" | "desc") {
  sortState.order = value;
  pageState.page = 1;
}

function updatePageSize(value: number) {
  pageState.pageSize = value;
  pageState.page = 1;
}

function updatePage(page: number) {
  pageState.page = page;
}

watch(
  () => routeTaskId.value,
  (taskId) => {
    Object.assign(listQuery, createDefaultQuery(taskId));
    Object.assign(appliedQuery, createDefaultQuery(taskId));
    pageState.page = 1;
  },
);

onMounted(() => {
  syncAppliedQuery();
  void loadSessions();
});
</script>
