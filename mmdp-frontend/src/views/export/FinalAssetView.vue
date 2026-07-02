<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>数据导出</h1>
        <p>按采集会话组织导出明细 · 下载入口</p>
      </div>
    </div>

    <hr class="light2-divider" />

    <!-- chips -->
    <div class="chip-group" style="margin-bottom:14px">
      <button class="chip-item" :class="quickFilter === '' ? 'chip-item-active' : ''" @click="quickFilter = ''">全部</button>
      <button v-for="q in quickItems" :key="q.value" class="chip-item" :class="quickFilter === q.value ? 'chip-item-active' : ''" @click="quickFilter = quickFilter === q.value ? '' : q.value">{{ q.label }}</button>
    </div>

    <!-- filters -->
    <div class="light2-filters">
      <input v-model="filters.taskId" type="text" placeholder="搜索任务编号..." class="light2-input" @keyup.enter="searchCount += 1" />
      <input v-model="filters.sessionId" type="text" placeholder="搜索采集编号..." class="light2-input" @keyup.enter="searchCount += 1" />
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="searchCount += 1">搜索</button>
      <button class="light2-btn light2-btn-sec light2-btn-sm" @click="filters.taskId = ''; filters.sessionId = ''; quickFilter = ''; searchCount += 1">重置</button>
    </div>

    <!-- table -->
    <div class="light2-tbl">
      <table>
        <thead>
          <tr>
            <th>采集编号</th>
            <th>所属任务</th>
            <th>被试/动作</th>
            <th>资产数</th>
            <th>总大小</th>
            <th>QC</th>
            <th>导出状态</th>
            <th>更新时间</th>
            <th style="width:120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="!filteredSessions.length">
            <td colspan="9" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">暂无可导出的采集会话。</td>
          </tr>
          <tr v-for="s in filteredSessions" :key="s.sessionId">
            <td class="light2-code">{{ s.sessionCode || s.sessionId }}</td>
            <td>{{ s.taskName }}</td>
            <td>{{ s.subjectCode }} / {{ s.actionName }}</td>
            <td>{{ s.assetCount }}</td>
            <td class="light2-code">{{ formatFileSize(s.totalSize) }}</td>
            <td><span class="light2-badge" :class="badgeClass(s.qcStatus)"><span class="light2-bdot" :style="{background:badgeColor(s.qcStatus)}" />{{ s.qcStatus }}</span></td>
            <td><span class="light2-badge" :class="badgeClass(s.exportStatus)"><span class="light2-bdot" :style="{background:badgeColor(s.exportStatus)}" />{{ s.exportStatus }}</span></td>
            <td>{{ formatDateTime(s.updatedAt) }}</td>
            <td>
              <div class="light2-actions">
                <RouterLink :to="`/sessions/${s.sessionId}`" class="light2-btn light2-btn-sec light2-btn-sm">详情</RouterLink>
                <button class="light2-btn light2-btn-primary light2-btn-sm" @click="selectedSession = s">下载</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- drawer -->
    <AppDrawer :open="Boolean(selectedSession)" title="导出详情" @close="selectedSession = null">
      <div v-if="selectedSession" class="space-y-3">
        <div class="rounded-[10px] border px-4 py-3 text-sm" style="border-color:var(--color-border-soft);background:var(--color-surface-muted);color:var(--color-text-secondary)">
          当前导出仍基于会话下已存在的平台文件，尚未生成独立导出包。
        </div>
        <a
          v-for="asset in downloadableAssets"
          :key="asset.id"
          :href="asset.rawAsset.storageUrl || '#'"
          target="_blank"
          rel="noreferrer"
          class="flex items-center justify-between rounded-[10px] border bg-white px-3 py-3 text-sm transition hover:bg-slate-50"
          style="border-color:var(--color-border-soft);color:var(--color-text-primary)"
        >
          <span class="truncate">{{ asset.assetName }}</span>
          <span style="font-size:12px;color:var(--color-text-tertiary)">{{ asset.fileFormat }}</span>
        </a>
        <div v-if="!downloadableAssets.length" class="py-8 text-center text-sm" style="color:var(--color-text-tertiary)">当前采集没有可下载文件。</div>
      </div>
    </AppDrawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink } from "vue-router";
import { fetchFinalAssets } from "@/api/platform";
import AppDrawer from "@/components/AppDrawer.vue";
import type { FinalAssetRecord } from "@/types/platform";
import { formatDateTime, formatFileSize } from "@/utils/format";

const sessions = ref<FinalAssetRecord[]>([]);
const selectedSession = ref<FinalAssetRecord | null>(null);
const searchCount = ref(0);
const quickFilter = ref("");
const filters = reactive({ taskId: "", sessionId: "" });

const quickItems = [
  { label: "已完成 QC", value: "qc" },
  { label: "可导出", value: "export" },
];

const filteredSessions = computed(() =>
  sessions.value.filter((session) => {
    const matchTask = !filters.taskId || String(session.taskId).includes(filters.taskId.trim());
    const matchSession = !filters.sessionId || session.sessionId.includes(filters.sessionId.trim()) || String(session.sessionCode || "").includes(filters.sessionId.trim());
    if (!matchTask || !matchSession) return false;
    if (quickFilter.value === "qc") return session.qcStatus === "PASSED" || session.qcStatus === "QC_PASSED";
    if (quickFilter.value === "export") return session.exportStatus === "READY";
    return true;
  }),
);

const downloadableAssets = computed(() => selectedSession.value?.assets.filter((a) => a.rawAsset.storageUrl) ?? []);

const BADGE_MAP: Record<string, { cls: string; color: string }> = {
  PASSED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  QC_PASSED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  READY: { cls: "light2-badge-ok", color: "#0d7d3e" },
  PENDING: { cls: "light2-badge-neutral", color: "#9298a3" },
};
function badgeClass(s: string) { return BADGE_MAP[s]?.cls ?? "light2-badge-neutral"; }
function badgeColor(s: string) { return BADGE_MAP[s]?.color ?? "#9298a3"; }

onMounted(async () => {
  sessions.value = await fetchFinalAssets();
});
</script>
