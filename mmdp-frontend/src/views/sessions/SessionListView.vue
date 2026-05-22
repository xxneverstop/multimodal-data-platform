<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / Session" title="Session" description="查看真实采集会话。" :meta="headerMeta" />

    <PageCard title="筛选条件">
      <SearchActionBar v-model="filters" :fields="filterFields" @search="searchCount += 1" />
    </PageCard>

    <PageCard title="会话列表">
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">sessionId</th>
              <th class="px-3 py-2.5 font-medium">taskId</th>
              <th class="px-3 py-2.5 font-medium">会话名称</th>
              <th class="px-3 py-2.5 font-medium">设备</th>
              <th class="px-3 py-2.5 font-medium">数据状态</th>
              <th class="px-3 py-2.5 font-medium">处理状态</th>
              <th class="px-3 py-2.5 font-medium">质检状态</th>
              <th class="px-3 py-2.5 font-medium">创建时间</th>
              <th class="px-3 py-2.5 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="session in filteredSessions" :key="session.sessionId" class="hover:bg-slate-50">
              <td class="px-3 py-2.5 font-medium text-slate-900">{{ session.sessionId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ session.taskId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ session.sessionName }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ session.deviceSummary }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="session.dataStatus" /></td>
              <td class="px-3 py-2.5"><StatusBadge :status="session.processingStatus" /></td>
              <td class="px-3 py-2.5"><StatusBadge :status="session.qcStatus" /></td>
              <td class="px-3 py-2.5 text-slate-500">{{ formatDateTime(session.createdAt) }}</td>
              <td class="px-3 py-2.5 text-right"><BaseButton size="sm" variant="ghost" :to="`/sessions/${session.sessionId}`">详情</BaseButton></td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchSessionList } from "@/api/platform";
import BaseButton from "@/components/BaseButton.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { FilterField } from "@/components/FilterBar.vue";
import type { SessionRecord } from "@/types/platform";
import { formatDateTime } from "@/utils/format";

const sessions = ref<SessionRecord[]>([]);
const searchCount = ref(0);
const filters = reactive({ taskId: "", sessionId: "" });
const filterFields: FilterField[] = [
  { key: "taskId", label: "taskId", placeholder: "请输入任务编号" },
  { key: "sessionId", label: "sessionId", placeholder: "请输入会话编号" }
];

const filteredSessions = computed(() => sessions.value.filter((session) => (!filters.taskId || String(session.taskId).includes(filters.taskId.trim())) && (!filters.sessionId || session.sessionId.includes(filters.sessionId.trim()))));
const headerMeta = computed(() => [{ label: "总数", value: sessions.value.length }, { label: "结果", value: filteredSessions.value.length }]);

onMounted(async () => {
  sessions.value = await fetchSessionList();
});
</script>
