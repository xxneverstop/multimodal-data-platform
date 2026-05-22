<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 采集" title="采集任务" description="查看和管理业务任务。" :meta="headerMeta" />

    <PageCard title="筛选条件">
      <SearchActionBar v-model="filters" :fields="filterFields" @search="searchCount += 1">
        <template #actions>
          <BaseButton variant="primary" size="md" @click="dialogOpen = true">
            <BaseIcon name="plus" size="sm" />
            新建
          </BaseButton>
        </template>
      </SearchActionBar>
    </PageCard>

    <PageCard title="任务列表">
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">任务名称</th>
              <th class="px-3 py-2.5 font-medium">taskId</th>
              <th class="px-3 py-2.5 font-medium">subjectCode</th>
              <th class="px-3 py-2.5 font-medium">actionName</th>
              <th class="px-3 py-2.5 font-medium">采集人员</th>
              <th class="px-3 py-2.5 font-medium">计划模态</th>
              <th class="px-3 py-2.5 font-medium">状态</th>
              <th class="px-3 py-2.5 font-medium">Session 数</th>
              <th class="px-3 py-2.5 font-medium">创建时间</th>
              <th class="px-3 py-2.5 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="task in filteredTasks" :key="task.id" class="hover:bg-slate-50">
              <td class="px-3 py-2.5 font-medium text-slate-900">{{ task.taskName }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ task.id }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ task.subjectCode }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ task.actionName }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ task.operatorName || "-" }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ task.modality }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="task.status" /></td>
              <td class="px-3 py-2.5 text-slate-600">1</td>
              <td class="px-3 py-2.5 text-slate-500">{{ formatDateTime(task.createdAt) }}</td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex justify-end gap-2">
                  <BaseButton size="sm" variant="ghost" :to="`/acquisition/${task.id}`">详情</BaseButton>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>

    <AppDialog :open="dialogOpen" title="新建采集任务" description="填写基础信息后创建任务。" :loading="submitting" @close="closeDialog" @confirm="handleCreate">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">任务名称</span>
          <input v-model="form.taskName" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">subjectCode</span>
          <input v-model="form.subjectCode" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">actionName</span>
          <input v-model="form.actionName" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">设备类型</span>
          <input v-model="form.deviceType" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">计划模态</span>
          <input v-model="form.modality" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">采集日期</span>
          <input v-model="form.collectDate" type="date" class="app-input app-input-compact" />
        </label>
      </div>
      <p v-if="message" class="mt-3 text-sm text-slate-500">{{ message }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { fetchAcquisitionList } from "@/api/platform";
import { createTask } from "@/api/tasks";
import AppDialog from "@/components/AppDialog.vue";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { FilterField } from "@/components/FilterBar.vue";
import type { CreateTaskRequest, TaskResponse } from "@/types/task";
import { formatDateTime } from "@/utils/format";

const router = useRouter();
const tasks = ref<TaskResponse[]>([]);
const dialogOpen = ref(false);
const submitting = ref(false);
const message = ref("");
const searchCount = ref(0);
const filters = reactive({ taskId: "", keyword: "" });
const form = reactive<CreateTaskRequest>({
  taskName: "",
  subjectCode: "",
  actionName: "",
  deviceType: "camera",
  modality: "多模态",
  collectDate: "",
  scene: "",
  operatorName: "",
  captureLocation: "",
  remark: ""
});

const filterFields: FilterField[] = [
  { key: "taskId", label: "taskId", placeholder: "请输入任务编号" },
  { key: "keyword", label: "关键词", placeholder: "请输入任务名称、被试或动作" }
];

const filteredTasks = computed(() =>
  tasks.value.filter((task) => {
    const matchTaskId = !filters.taskId || String(task.id).includes(filters.taskId.trim());
    const keyword = filters.keyword.trim().toLowerCase();
    const matchKeyword = !keyword || [task.taskName, task.subjectCode, task.actionName].some((value) => value.toLowerCase().includes(keyword));
    return matchTaskId && matchKeyword;
  })
);

const headerMeta = computed(() => [
  { label: "总数", value: tasks.value.length },
  { label: "结果", value: filteredTasks.value.length }
]);

function closeDialog() {
  dialogOpen.value = false;
  message.value = "";
}

async function loadTasks() {
  const page = await fetchAcquisitionList();
  tasks.value = page.records;
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

onMounted(loadTasks);
</script>
