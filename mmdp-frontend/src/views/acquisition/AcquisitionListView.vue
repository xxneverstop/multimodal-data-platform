<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>任务中心</h1>
        <p>管理采集任务 · 查看进度 · 进入工作区</p>
      </div>
      <button class="light2-btn light2-btn-primary" @click="dialogOpen = true">+ 新建任务</button>
    </div>

    <!-- metrics -->
    <div class="light2-metrics">
      <div v-for="m in metricItems" :key="m.label" class="light2-mcard">
        <div class="light2-mstripe" :style="{ background: m.tone === 'task' ? 'var(--color-brand-500)' : m.tone === 'session' ? '#0d9444' : m.tone === 'asset' ? '#7c3aed' : '#e3740a' }" />
        <div class="light2-mlabel">{{ m.label }}</div>
        <div class="light2-mvalue">{{ m.value }}</div>
      </div>
    </div>

    <!-- filters -->
    <div class="light2-filters">
      <input v-model="listQuery.keyword" type="text" placeholder="搜索任务编号 / 名称 / 被试..." class="light2-input" @keyup.enter="applyQuery" />
      <div class="light2-sel">
        <select v-model="listQuery.status" @change="applyQuery">
          <option value="">全部状态</option>
          <option v-for="o in statusOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
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
      <table class="min-w-[1180px]">
        <thead>
          <tr>
            <th>任务编号</th>
            <th>任务名称</th>
            <th>配置</th>
            <th>创建时间</th>
            <th>采集数</th>
            <th>最新采集时间</th>
            <th>最新采集</th>
            <th>任务状态</th>
            <th style="width:150px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="9" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">正在加载任务列表...</td>
          </tr>
          <tr v-else-if="!displayedList.length">
            <td colspan="9" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">{{ emptyText }}</td>
          </tr>
          <tr v-for="task in displayedList" :key="task.id">
            <td>
              <RouterLink :to="`/acquisition/${task.id}`" class="light2-code">{{ task.taskCode || `#${task.id}` }}</RouterLink>
            </td>
            <td>
              <RouterLink :to="`/acquisition/${task.id}`" class="light2-tname hover:underline">{{ task.taskName }}</RouterLink>
            </td>
            <td>{{ task.profileName || "-" }}</td>
            <td>{{ formatDateTime(task.createdAt) }}</td>
            <td class="light2-code">{{ task.sessionCount ?? 0 }}</td>
            <td>{{ formatDateTime(task.latestSessionStartedAt ?? undefined) }}</td>
            <td>
              <RouterLink
                v-if="task.latestSessionId && latestSessionLabel(task)"
                :to="`/sessions/${task.latestSessionId}`"
                class="light2-code"
              >
                {{ latestSessionLabel(task) }}
              </RouterLink>
              <span v-else>{{ latestSessionLabel(task) || "-" }}</span>
            </td>
            <td><span class="light2-badge" :class="badgeClass(task.status)"><span class="light2-bdot" :style="{ background: badgeColor(task.status) }" />{{ formatStatusLabel(task.status) }}</span></td>
            <td>
              <div class="light2-actions">
                <RouterLink :to="`/sessions?taskId=${task.id}`" class="light2-btn light2-btn-sec light2-btn-sm">查看采集</RouterLink>
                <RouterLink :to="`/acquisition/${task.id}`" class="light2-btn light2-btn-sec light2-btn-sm">详情</RouterLink>
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

    <!-- dialog -->
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
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">Profile</span>
          <select v-model="profileIdValue" class="app-input app-input-compact">
            <option value="" disabled>请选择 Profile</option>
            <option v-for="profile in profiles" :key="profile.id" :value="String(profile.id)">
              {{ profile.profileName }} ({{ profile.profileCode }})
            </option>
          </select>
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
import { RouterLink, useRouter } from "vue-router";
import { fetchCollectionProfiles, type CollectionProfileResponse } from "@/api/profiles";
import { fetchSessions } from "@/api/sessions";
import { createTask, fetchTasks } from "@/api/tasks";
import AppDialog from "@/components/AppDialog.vue";
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
const profiles = ref<CollectionProfileResponse[]>([]);
const profileIdValue = ref("");
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
  profileId: null,
  actionName: "",
  deviceType: "",
  modality: "",
  collectDate: "",
  scene: "",
  operatorName: "",
  captureLocation: "",
  remark: "",
});

const selectedProfile = computed(
  () => profiles.value.find((profile) => String(profile.id) === profileIdValue.value) ?? null,
);

const statusOptions = [
  { label: "已创建", value: "CREATED" },
  { label: "已上传", value: "UPLOADED" },
  { label: "质检通过", value: "QC_PASSED" },
  { label: "质检警告", value: "QC_WARNING" },
  { label: "质检失败", value: "QC_FAILED" },
];

const sortOptions = [
  { label: "日期降序", value: "createdAt" },
  { label: "日期升序", value: "collectDate" },
  { label: "最近采集", value: "latestSessionStartedAt" },
  { label: "采集数", value: "sessionCount" },
];

const filteredList = computed(() => rawList.value.filter((item) => matchesTaskQuery(item, appliedQuery)));
const sortedList = computed(() => sortTaskList(filteredList.value, sortState));
const displayedList = computed(() => slicePage(sortedList.value, pageState.page, pageState.pageSize));

const metricItems = computed(() => {
  const sessionTotal = rawList.value.reduce((sum, task) => sum + (task.sessionCount ?? 0), 0);
  const assetTotal = metricSessions.value.reduce((sum, session) => sum + (session.assetCount ?? 0), 0);
  const processingStatuses = new Set(["CREATED", "PENDING", "RUNNING", "UPLOADING", "ACTIVE"]);
  const processingTotal = rawList.value.filter((task) => processingStatuses.has(task.status)).length;
  return [
    { label: "任务总数", value: rawList.value.length, tone: "task" },
    { label: "采集总数", value: sessionTotal, tone: "session" },
    { label: "资产总数", value: assetTotal, tone: "asset" },
    { label: "异常任务", value: processingTotal, tone: "process" },
  ];
});

const BADGE_MAP: Record<string, { cls: string; color: string }> = {
  ACTIVE: { cls: "light2-badge-info", color: "var(--color-brand-500)" },
  CREATED: { cls: "light2-badge-neutral", color: "#9298a3" },
  COMPLETED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  ERROR: { cls: "light2-badge-err", color: "#c5222f" },
  FAILED: { cls: "light2-badge-err", color: "#c5222f" },
  QC_PASSED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  QC_WARNING: { cls: "light2-badge-warn", color: "#b87a0a" },
  QC_FAILED: { cls: "light2-badge-err", color: "#c5222f" },
  PENDING: { cls: "light2-badge-neutral", color: "#9298a3" },
  RUNNING: { cls: "light2-badge-warn", color: "#b87a0a" },
  UPLOADING: { cls: "light2-badge-info", color: "var(--color-brand-500)" },
  UPLOADED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  SUCCESS: { cls: "light2-badge-ok", color: "#0d7d3e" },
};
function badgeClass(status: string) {
  return BADGE_MAP[status]?.cls ?? "light2-badge-neutral";
}
function badgeColor(status: string) {
  return BADGE_MAP[status]?.color ?? "#9298a3";
}

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

function latestSessionLabel(task: TaskResponse) {
  return task.latestSessionCode || task.latestSessionId || "";
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
  if (!selectedProfile.value) {
    message.value = "请选择 Profile";
    return;
  }
  form.profileId = selectedProfile.value.id;
  form.deviceType = selectedProfile.value.deviceGroupCode;
  form.modality = selectedProfile.value.modalityGroupCode;
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
  void fetchCollectionProfiles().then((list) => {
    profiles.value = list;
    if (!profileIdValue.value && list.length > 0) {
      profileIdValue.value = String(list[0].id);
      form.profileId = list[0].id;
    }
  }).catch(() => {});
  void loadTasks();
});
</script>
