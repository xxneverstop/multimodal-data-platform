<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>数据处理</h1>
        <p>查看处理规则和执行记录</p>
      </div>
      <button v-if="activeTab === 'templates'" class="light2-btn light2-btn-primary" @click="dialogOpen = true">+ 新建规则</button>
    </div>

    <!-- tabs -->
    <div class="tab-bar" style="margin-bottom:16px">
      <button v-for="t in tabs" :key="t.value" class="tab-bar-item" :class="activeTab === t.value ? 'tab-bar-item-active' : ''" @click="activeTab = t.value">{{ t.label }}</button>
    </div>

    <!-- filters -->
    <div class="light2-filters">
      <input v-model="filters.taskId" type="text" placeholder="按 taskId 筛选..." class="light2-input" @keyup.enter="searchCount += 1" />
      <input v-model="filters.sessionId" type="text" placeholder="按 sessionId 筛选..." class="light2-input" @keyup.enter="searchCount += 1" />
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="searchCount += 1">搜索</button>
    </div>

    <!-- templates table -->
    <div v-if="activeTab === 'templates'" class="light2-tbl">
      <table>
        <thead>
          <tr>
            <th>规则名称</th>
            <th>类型</th>
            <th>输入模态</th>
            <th>输出类型</th>
            <th>适用范围</th>
            <th>最近执行</th>
            <th>状态</th>
            <th style="width:120px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="t in filteredTemplates" :key="t.id">
            <td style="font-weight:600">{{ t.name }}</td>
            <td>{{ t.templateType }}</td>
            <td>{{ t.inputModality }}</td>
            <td>{{ t.outputAssetType }}</td>
            <td>{{ t.scope }}</td>
            <td>{{ formatDateTime(t.recentRunAt || undefined) }}</td>
            <td><span class="light2-badge" :class="badgeClass(t.recentRunStatus || 'PENDING')"><span class="light2-bdot" :style="{background:badgeColor(t.recentRunStatus||'PENDING')}" />{{ t.recentRunStatus || 'PENDING' }}</span></td>
            <td>
              <div class="light2-actions">
                <button class="light2-btn light2-btn-sec light2-btn-sm" @click="selectedTemplate = t">详情</button>
                <RouterLink :to="`/upload?taskId=${t.taskId||''}&sessionId=${t.sessionId||''}`" class="light2-btn light2-btn-sec light2-btn-sm">执行</RouterLink>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- jobs list -->
    <div v-else class="light2-tbl">
      <table>
        <thead>
          <tr>
            <th>作业 ID</th>
            <th>Pipeline</th>
            <th>输入</th>
            <th>状态</th>
            <th>开始时间</th>
            <th>耗时</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="job in recentJobs" :key="job.id">
            <td class="light2-code">{{ job.id }}</td>
            <td style="font-weight:600">{{ job.pipelineId }}</td>
            <td>{{ job.sessionCode || job.taskCode || '-' }}</td>
            <td><span class="light2-badge" :class="badgeClass(job.status)"><span class="light2-bdot" :style="{background:badgeColor(job.status)}" />{{ job.status }}</span></td>
            <td>{{ formatDateTime(job.createdAt) }}</td>
            <td class="light2-code">{{ job.duration || '-' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- drawer -->
    <AppDrawer :open="Boolean(selectedTemplate)" title="处理规则详情" @close="selectedTemplate = null">
      <div v-if="selectedTemplate" class="space-y-3 text-sm" style="color:var(--color-text-secondary)">
        <div><span style="font-weight:600;color:var(--color-text-primary)">名称：</span>{{ selectedTemplate.name }}</div>
        <div><span style="font-weight:600;color:var(--color-text-primary)">类型：</span>{{ selectedTemplate.templateType }}</div>
        <div><span style="font-weight:600;color:var(--color-text-primary)">输入模态：</span>{{ selectedTemplate.inputModality }}</div>
        <div><span style="font-weight:600;color:var(--color-text-primary)">输出类型：</span>{{ selectedTemplate.outputAssetType }}</div>
      </div>
    </AppDrawer>

    <!-- dialog -->
    <AppDialog :open="dialogOpen" title="新建处理规则" description="先保留前端占位字段。" @close="dialogOpen = false" @confirm="submitMock('处理规则已创建')">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm" style="color:var(--color-text-secondary)">规则名称</span>
          <input v-model="ruleForm.name" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm" style="color:var(--color-text-secondary)">处理类型</span>
          <input v-model="ruleForm.type" class="app-input app-input-compact" />
        </label>
      </div>
      <p v-if="dialogMessage" class="mt-3 text-sm" style="color:var(--color-text-tertiary)">{{ dialogMessage }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink } from "vue-router";
import { fetchPlatformOverview, fetchProcessingTemplates } from "@/api/platform";
import AppDialog from "@/components/AppDialog.vue";
import AppDrawer from "@/components/AppDrawer.vue";
import type { ProcessingTemplateRecord } from "@/types/platform";
import type { ProcessingJobResponse } from "@/types/processing";
import { formatDateTime } from "@/utils/format";

const templates = ref<ProcessingTemplateRecord[]>([]);
const recentJobs = ref<ProcessingJobResponse[]>([]);
const selectedTemplate = ref<ProcessingTemplateRecord | null>(null);
const dialogOpen = ref(false);
const dialogMessage = ref("");
const searchCount = ref(0);
const activeTab = ref("templates");
const tabs = [
  { label: "处理规则", value: "templates" },
  { label: "执行记录", value: "jobs" }
];
const filters = reactive({ taskId: "", sessionId: "" });
const ruleForm = reactive({ name: "", type: "" });
const filteredTemplates = computed(() => templates.value.filter((template) => (!filters.taskId || String(template.taskId || "").includes(filters.taskId.trim())) && (!filters.sessionId || String(template.sessionId || "").includes(filters.sessionId.trim()))));

const BADGE_MAP: Record<string, { cls: string; color: string }> = {
  ACTIVE: { cls: "light2-badge-ok", color: "#0d7d3e" },
  PENDING: { cls: "light2-badge-neutral", color: "#9298a3" },
  RUNNING: { cls: "light2-badge-warn", color: "#b87a0a" },
  SUCCESS: { cls: "light2-badge-ok", color: "#0d7d3e" },
  COMPLETED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  FAILED: { cls: "light2-badge-err", color: "#c5222f" },
};
function badgeClass(s: string) { return BADGE_MAP[s]?.cls ?? "light2-badge-neutral"; }
function badgeColor(s: string) { return BADGE_MAP[s]?.color ?? "#9298a3"; }

function submitMock(message: string) {
  dialogMessage.value = message;
  window.setTimeout(() => { dialogOpen.value = false; dialogMessage.value = ""; }, 600);
}

onMounted(async () => {
  templates.value = await fetchProcessingTemplates();
  const overview = await fetchPlatformOverview();
  recentJobs.value = overview.recentJobs;
});
</script>
