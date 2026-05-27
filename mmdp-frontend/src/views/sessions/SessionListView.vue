<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 会话" title="采集会话" description="每次开始/停止采集为一个 Session。" :meta="headerMeta" />

    <PageCard title="筛选条件">
      <SearchActionBar v-model="filters" :fields="filterFields" @search="searchCount += 1" />
    </PageCard>

    <PageCard title="会话列表">
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">Session ID</th>
              <th class="px-3 py-2.5 font-medium">所属任务</th>
              <th class="px-3 py-2.5 font-medium">Subject Code</th>
              <th class="px-3 py-2.5 font-medium">Action Name</th>
              <th class="px-3 py-2.5 font-medium">时长</th>
              <th class="px-3 py-2.5 font-medium">上传状态</th>
              <th class="px-3 py-2.5 font-medium">上传时间</th>
              <th class="px-3 py-2.5 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="s in filteredSessions" :key="s.sessionId" class="hover:bg-slate-50">
              <td class="px-3 py-2.5 font-mono font-medium text-slate-900 text-xs">{{ s.sessionId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ s.taskName ?? 'Task-' + s.taskId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ s.subjectCode }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ s.actionName }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ formatDuration(s.durationMs) }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="s.uploadStatus" /></td>
              <td class="px-3 py-2.5 text-slate-500">{{ formatDateTime(s.createdAt) }}</td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex justify-end gap-2">
                  <BaseButton size="sm" variant="ghost" :to="`/sessions/${s.sessionId}`">详情</BaseButton>
                  <BaseButton size="sm" variant="ghost" :to="`/play/${s.sessionId}`">
                    <BaseIcon name="play" size="sm" />
                    播放
                  </BaseButton>
                </div>
              </td>
            </tr>
            <tr v-if="!filteredSessions.length">
              <td colspan="8" class="px-3 py-8 text-center text-slate-400">暂无会话数据。请先在采集页面上传 Session。</td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchAllSessions, type SessionResponse } from "@/api/sessions";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { FilterField } from "@/components/FilterBar.vue";
import { formatDateTime } from "@/utils/format";

const sessions = ref<SessionResponse[]>([]);
const searchCount = ref(0);
const filters = reactive({ taskId: "", sessionId: "" });
const filterFields: FilterField[] = [
  { key: "taskId", label: "任务ID", placeholder: "请输入任务编号" },
  { key: "sessionId", label: "Session ID", placeholder: "请输入会话编号" }
];

const filteredSessions = computed(() => sessions.value.filter((s) =>
  (!filters.taskId || String(s.taskId).includes(filters.taskId.trim())) &&
  (!filters.sessionId || s.sessionId.toLowerCase().includes(filters.sessionId.trim().toLowerCase()))
));
const headerMeta = computed(() => [{ label: "总数", value: sessions.value.length }, { label: "结果", value: filteredSessions.value.length }]);

function formatDuration(ms: number | null): string {
  if (!ms || ms <= 0) return "--";
  const totalSec = Math.floor(ms / 1000);
  const m = Math.floor(totalSec / 60);
  const s = totalSec % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

onMounted(async () => {
  try {
    sessions.value = await fetchAllSessions();
  } catch { /* backend offline */ }
});
</script>
