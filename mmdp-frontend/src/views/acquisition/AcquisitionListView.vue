<template>
  <div class="space-y-5">
    <PageHeader
      surface="plain"
      eyebrow="功能 / 任务"
      title="任务"
      description="查看任务、采集进展和处理状态，快速进入采集、详情或播放流程。"
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
          search-tone="task"
          @update:advanced-open="advancedOpen = $event"
          @search="applyQuery"
          @reset="resetQuery"
        >
          <template #actions>
            <BaseButton variant="secondary" size="md" @click="dialogOpen = true">
              <BaseIcon name="plus" size="sm" />
              新建任务
            </BaseButton>
          </template>

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

      <div class="app-column-strip lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1.3fr)_minmax(220px,0.95fr)_minmax(180px,0.8fr)_auto]">
        <div>任务</div>
        <div>对象信息</div>
        <div>采集摘要</div>
        <div class="text-right">状态</div>
        <div class="text-right">操作</div>
      </div>

      <div v-if="loading" class="px-5 py-12 text-center text-sm text-[var(--color-text-secondary)]">
        正在加载任务列表...
      </div>

      <div v-else-if="displayedList.length" class="app-summary-list">
        <article v-for="task in displayedList" :key="task.id" class="app-summary-row app-row-accent-task">
          <div class="grid gap-4 px-5 py-4 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1.3fr)_minmax(220px,0.95fr)_minmax(180px,0.8fr)_auto] lg:items-center">
            <section class="min-w-0">
              <div class="flex items-start gap-3">
                <div class="app-metric-icon app-tone-task mt-0.5 h-8 w-8 shrink-0">
                  <BaseIcon name="clipboard-check" size="sm" />
                </div>
                <div class="min-w-0 space-y-1">
                  <div class="app-summary-title-strong truncate">{{ task.taskName }}</div>
                  <div class="app-summary-subtitle-muted">
                    {{ task.taskCode || `#${task.id}` }}
                  </div>
                </div>
              </div>
            </section>

            <section class="min-w-0">
              <div class="app-summary-meta-inline">
                <span><strong>被试</strong> {{ task.subjectCode || "-" }}</span>
                <span>·</span>
                <span><strong>动作</strong> {{ task.actionName || "-" }}</span>
                <span>·</span>
                <span><strong>Profile</strong> {{ task.profileName || "-" }}</span>
                <span>·</span>
                <span><strong>日期</strong> {{ task.collectDate || "-" }}</span>
              </div>
            </section>

            <section class="min-w-0 space-y-1">
              <div class="app-summary-stat-inline">
                <span><strong>{{ task.sessionCount ?? 0 }}</strong> 次采集</span>
                <span>·</span>
                <span>最近 {{ formatDateTime(task.latestSessionStartedAt || "") }}</span>
              </div>
            </section>

            <section class="min-w-0">
              <div class="app-status-stack justify-start lg:justify-end">
                <StatusBadge :status="task.status" />
                <StatusBadge
                  :status="task.latestSessionStatus || 'PENDING'"
                  :label="`最近 ${formatStatusLabel(task.latestSessionStatus || 'PENDING')}`"
                />
              </div>
            </section>

            <section class="min-w-0">
              <div class="app-action-group">
                <BaseButton size="sm" variant="soft" tone="task" :to="`/sessions?taskId=${task.id}`">
                  查看采集({{ task.sessionCount ?? 0 }})
                </BaseButton>
                <BaseButton size="sm" variant="ghost" :to="`/acquisition/${task.id}`">详情</BaseButton>
                <BaseButton size="sm" variant="ghost" :to="`/sessions?taskId=${task.id}`">
                  <BaseIcon name="play" size="sm" />
                  播放
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

    <AppDialog
      :open="dialogOpen"
      title="新建任务"
      description="填写基础信息后创建采集任务。"
      :loading="submitting"
      @close="closeDialog"
      @confirm="handleCreate"
    >
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">任务名称</span>
          <input v-model="form.taskName" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">被试编号</span>
          <input v-model="form.subjectCode" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">动作名称</span>
          <input v-model="form.actionName" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">采集日期</span>
          <input v-model="form.collectDate" type="date" class="app-input app-input-compact" />
        </label>
      </div>
      <p v-if="message" class="mt-3 text-sm text-[var(--color-text-secondary)]">{{ message }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watchEffect } from "vue";
import { useRouter } from "vue-router";
import { fetchSessions } from "@/api/sessions";
import { createTask, fetchTasks } from "@/api/tasks";
import AppDialog from "@/components/AppDialog.vue";
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
import type { CreateTaskRequest, TaskResponse } from "@/types/task";
import { formatDateTime, formatStatusLabel } from "@/utils/format";

type TaskListQueryState = {
  keyword: string;
  taskNumber: string;
  status: string;
  subjectCode: string;
  actionName: string;
  collectDateFrom: string;
  collectDateTo: string;
};

type TaskSortState = {
  field: "createdAt" | "collectDate" | "latestSessionStartedAt" | "sessionCount";
  order: "asc" | "desc";
};

const router = useRouter();

const rawList = ref<TaskResponse[]>([]);
const metricSessions = ref<SessionListItem[]>([]);
const loading = ref(false);
const hasLoaded = ref(false);
const dialogOpen = ref(false);
const submitting = ref(false);
const message = ref("");
const advancedOpen = ref(false);

const pageState = reactive({
  page: 1,
  pageSize: 10,
  total: 0,
});

const listQuery = reactive(createDefaultQuery());
const appliedQuery = reactive(createDefaultQuery());
const sortState = reactive<TaskSortState>({
  field: "createdAt",
  order: "desc",
});

const form = reactive<CreateTaskRequest>({
  taskName: "",
  subjectCode: "",
  actionName: "",
  deviceType: "",
  modality: "",
  collectDate: "",
  scene: "",
  operatorName: "",
  captureLocation: "",
  remark: "",
});

const statusOptions = [
  { label: "已创建", value: "CREATED" },
  { label: "已上传", value: "UPLOADED" },
  { label: "质检通过", value: "QC_PASSED" },
  { label: "质检警告", value: "QC_WARNING" },
  { label: "质检失败", value: "QC_FAILED" },
];

const coreFilterFields: FilterField[] = [
  { key: "keyword", label: "关键字", placeholder: "任务名称 / 被试 / 动作" },
  { key: "taskNumber", label: "任务编号", placeholder: "任务 ID 或 taskCode" },
  { key: "status", label: "状态", type: "select", options: statusOptions },
];

const advancedFilterFields: FilterField[] = [
  { key: "subjectCode", label: "被试", placeholder: "输入被试编号" },
  { key: "actionName", label: "动作", placeholder: "输入动作名称" },
  { key: "collectDateFrom", label: "采集日期从", type: "date" },
  { key: "collectDateTo", label: "采集日期到", type: "date" },
];

const sortOptions: SortOption[] = [
  { label: "创建时间", value: "createdAt" },
  { label: "采集日期", value: "collectDate" },
  { label: "最近采集时间", value: "latestSessionStartedAt" },
  { label: "采集数", value: "sessionCount" },
];

const filteredList = computed(() => rawList.value.filter((item) => matchesTaskQuery(item, appliedQuery)));
const sortedList = computed(() => sortTaskList(filteredList.value, sortState));
const displayedList = computed(() => slicePage(sortedList.value, pageState.page, pageState.pageSize));

const metricItems = computed<MetricItem[]>(() => {
  const sessionTotal = rawList.value.reduce((sum, task) => sum + (task.sessionCount ?? 0), 0);
  const assetTotal = metricSessions.value.reduce((sum, session) => sum + (session.assetCount ?? 0), 0);
  const processingStatuses = new Set(["CREATED", "PENDING", "RUNNING", "UPLOADING", "ACTIVE"]);
  const processingTotal = rawList.value.filter((task) => processingStatuses.has(task.status)).length;
  return [
    { label: "任务总数", value: rawList.value.length, icon: "clipboard-check", tone: "task" },
    { label: "采集总数", value: sessionTotal, icon: "camera", tone: "session" },
    { label: "资产总数", value: assetTotal, icon: "hard-drive", tone: "asset" },
    { label: "处理中任务", value: processingTotal, icon: "workflow", tone: "process" },
  ];
});

const emptyText = computed(() => {
  if (!hasLoaded.value || loading.value) {
    return "正在加载任务数据...";
  }
  return rawList.value.length === 0 ? "暂无任务数据" : "当前筛选条件下没有匹配结果";
});

const advancedActive = computed(() => {
  const queryActive = Boolean(
    listQuery.subjectCode ||
      listQuery.actionName ||
      listQuery.collectDateFrom ||
      listQuery.collectDateTo,
  );
  return queryActive || sortState.field !== "createdAt" || sortState.order !== "desc" || pageState.pageSize !== 10;
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

function createDefaultQuery(): TaskListQueryState {
  return {
    keyword: "",
    taskNumber: "",
    status: "",
    subjectCode: "",
    actionName: "",
    collectDateFrom: "",
    collectDateTo: "",
  };
}

async function loadAcquisitionRawList() {
  const page = await fetchTasks({ page: 1, pageSize: 200 });
  return page.records;
}

function syncAppliedQuery() {
  Object.assign(appliedQuery, listQuery);
}

function matchesTaskQuery(task: TaskResponse, query: TaskListQueryState) {
  const keyword = query.keyword.trim().toLowerCase();
  const taskNumber = query.taskNumber.trim().toLowerCase();
  const subjectCode = query.subjectCode.trim().toLowerCase();
  const actionName = query.actionName.trim().toLowerCase();

  const matchesKeyword =
    !keyword ||
    [task.taskName, task.subjectCode, task.actionName, task.profileName || "", task.taskCode || ""]
      .some((value) => String(value).toLowerCase().includes(keyword));

  const matchesTaskNumber =
    !taskNumber ||
    String(task.id).toLowerCase() === taskNumber ||
    String(task.taskCode || "").toLowerCase() === taskNumber;

  const matchesStatus = !query.status || task.status === query.status;
  const matchesSubject = !subjectCode || String(task.subjectCode || "").toLowerCase().includes(subjectCode);
  const matchesAction = !actionName || String(task.actionName || "").toLowerCase().includes(actionName);
  const matchesFrom = !query.collectDateFrom || String(task.collectDate || "") >= query.collectDateFrom;
  const matchesTo = !query.collectDateTo || String(task.collectDate || "") <= query.collectDateTo;

  return matchesKeyword && matchesTaskNumber && matchesStatus && matchesSubject && matchesAction && matchesFrom && matchesTo;
}

function sortTaskList(list: TaskResponse[], state: TaskSortState) {
  return [...list].sort((left, right) => {
    const leftValue = getTaskSortValue(left, state.field);
    const rightValue = getTaskSortValue(right, state.field);

    if (leftValue === rightValue) {
      return 0;
    }

    if (state.order === "asc") {
      return leftValue > rightValue ? 1 : -1;
    }
    return leftValue < rightValue ? 1 : -1;
  });
}

function getTaskSortValue(task: TaskResponse, field: TaskSortState["field"]) {
  switch (field) {
    case "collectDate":
      return task.collectDate || "";
    case "latestSessionStartedAt":
      return task.latestSessionStartedAt || "";
    case "sessionCount":
      return task.sessionCount ?? 0;
    default:
      return task.createdAt || "";
  }
}

function slicePage<T>(list: T[], page: number, pageSize: number) {
  const start = (page - 1) * pageSize;
  return list.slice(start, start + pageSize);
}

async function loadTasks() {
  loading.value = true;
  message.value = "";
  try {
    const [taskResult, sessionResult] = await Promise.allSettled([
      loadAcquisitionRawList(),
      fetchSessions({ page: 1, pageSize: 500 }).then((page) => page.records),
    ]);

    rawList.value = taskResult.status === "fulfilled" ? taskResult.value : [];
    metricSessions.value = sessionResult.status === "fulfilled" ? sessionResult.value : [];
    hasLoaded.value = true;

    if (taskResult.status === "rejected") {
      throw taskResult.reason;
    }
  } catch (error) {
    rawList.value = [];
    message.value = error instanceof Error ? error.message : "加载任务列表失败";
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
  Object.assign(listQuery, createDefaultQuery());
  Object.assign(appliedQuery, createDefaultQuery());
  pageState.page = 1;
  pageState.pageSize = 10;
  sortState.field = "createdAt";
  sortState.order = "desc";
  advancedOpen.value = false;
}

function updateSortField(value: string) {
  sortState.field = value as TaskSortState["field"];
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

function closeDialog() {
  dialogOpen.value = false;
  message.value = "";
}

async function handleCreate() {
  submitting.value = true;
  message.value = "";
  try {
    const task = await createTask(form);
    await loadTasks();
    closeDialog();
    await router.push(`/acquisition/${task.id}`);
  } catch (error) {
    message.value = error instanceof Error ? error.message : "创建失败";
  } finally {
    submitting.value = false;
  }
}

onMounted(() => {
  syncAppliedQuery();
  void loadTasks();
});
</script>
