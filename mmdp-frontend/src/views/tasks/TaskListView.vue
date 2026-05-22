<template>
  <div class="space-y-5">
    <PageHeader
      eyebrow="任务中心"
      title="任务列表"
      description="统一查看实验室采集任务，并进入任务详情页完成资产接入、处理记录、质量检查和数据链路追踪。"
      :meta="headerMeta"
    >
      <template #actions>
        <BaseButton variant="primary" to="/tasks/new">新建任务</BaseButton>
      </template>
    </PageHeader>

    <section class="grid gap-3 md:grid-cols-3">
      <MetricCard label="任务总数" :value="taskPage.total || taskPage.records.length" description="当前环境中的任务记录总量" />
      <MetricCard label="当前页记录" :value="taskPage.records.length" description="本次列表返回并展示的任务数量" />
      <MetricCard label="状态覆盖" :value="statusCoverage" description="用于确认任务状态字段是否完整返回" />
    </section>

    <PageCard eyebrow="任务入口" title="任务总览" description="任务详情页是当前阶段的主要工作区。新建任务后，可在详情页继续完成资产接入、处理记录、质量检查和数据链路追踪。">
      <DataTableShell>
        <template #toolbar>
          <div class="grid gap-3 xl:grid-cols-[minmax(0,1fr)_210px_auto]">
            <div class="rounded-[14px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-500">
              当前平台围绕“任务 -> 资产 -> 处理作业 -> 派生资产 -> 数据链路”组织。建议从任务详情页进入后续数据生命周期操作。
            </div>
            <div class="rounded-[14px] border border-slate-200 bg-white px-4 py-3 text-xs text-slate-500">
              <div class="font-medium tracking-[0.14em] text-slate-400">记录数量</div>
              <div class="mt-1 text-sm font-semibold text-slate-800">{{ taskPage.records.length }} / {{ taskPage.total || taskPage.records.length }}</div>
            </div>
            <div class="flex items-center justify-end">
              <BaseButton variant="secondary" to="/tasks/new">录入新任务</BaseButton>
            </div>
          </div>
        </template>

        <div v-if="loading" class="px-4 py-12 text-center text-sm text-slate-500">正在加载任务列表...</div>
        <EmptyState
          v-else-if="!taskPage.records.length"
          title="暂无采集任务"
          description="当前还没有可展示的任务。可以先新建任务，再进入详情页完成资产接入与处理记录。"
          icon="任"
        />
        <table v-else class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs tracking-[0.12em] text-slate-500">
            <tr>
              <th class="px-4 py-3 font-medium">任务信息</th>
              <th class="px-4 py-3 font-medium">被试编号</th>
              <th class="px-4 py-3 font-medium">动作 / 设备</th>
              <th class="px-4 py-3 font-medium">当前数据状态</th>
              <th class="px-4 py-3 font-medium">创建时间</th>
              <th class="px-4 py-3 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white text-sm">
            <tr v-for="task in taskPage.records" :key="task.id" class="hover:bg-slate-50/80">
              <td class="px-4 py-3">
                <div class="font-medium text-slate-900">{{ task.taskName }}</div>
                <div class="mt-0.5 text-xs text-slate-500">任务 ID #{{ task.id }}</div>
              </td>
              <td class="px-4 py-3 text-slate-700">{{ task.subjectCode }}</td>
              <td class="px-4 py-3">
                <div class="font-medium text-slate-900">{{ task.actionName }}</div>
                <div class="mt-0.5 text-xs text-slate-500">{{ task.deviceType }}</div>
              </td>
              <td class="px-4 py-3">
                <StatusBadge :status="task.status" />
              </td>
              <td class="px-4 py-3 text-slate-500">{{ formatDateTime(task.createdAt) }}</td>
              <td class="px-4 py-3 text-right">
                <RouterLink :to="`/tasks/${task.id}`" class="app-link font-medium">进入任务详情</RouterLink>
              </td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>

      <p v-if="errorMessage" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">
        {{ errorMessage }}
      </p>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink } from "vue-router";
import { fetchTasks } from "@/api/tasks";
import BaseButton from "@/components/BaseButton.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import EmptyState from "@/components/EmptyState.vue";
import MetricCard from "@/components/MetricCard.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { TaskPageResponse } from "@/types/task";
import { formatDateTime } from "@/utils/format";

const loading = ref(false);
const errorMessage = ref("");

const taskPage = reactive<TaskPageResponse>({
  current: 1,
  size: 10,
  total: 0,
  records: []
});

const headerMeta = computed(() => [
  { label: "当前页", value: taskPage.current },
  { label: "返回记录", value: taskPage.records.length },
  { label: "任务总数", value: taskPage.total || taskPage.records.length }
]);

const statusCoverage = computed(() => {
  if (!taskPage.records.length) {
    return "0 / 0";
  }
  const coveredCount = taskPage.records.filter((task) => Boolean(task.status)).length;
  return `${coveredCount} / ${taskPage.records.length}`;
});

async function loadTasks() {
  loading.value = true;
  errorMessage.value = "";
  try {
    const data = await fetchTasks();
    Object.assign(taskPage, data);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载任务列表失败";
  } finally {
    loading.value = false;
  }
}

onMounted(loadTasks);
</script>
