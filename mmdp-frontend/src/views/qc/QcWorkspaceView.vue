<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>质量质检</h1>
        <p>分规则和结果两个视角查看当前质检能力</p>
      </div>
      <button v-if="activeTab === 'rules'" class="light2-btn light2-btn-primary" @click="dialogOpen = true">+ 新建规则</button>
    </div>

    <!-- tabs -->
    <div class="tab-bar" style="margin-bottom:16px">
      <button v-for="t in tabs" :key="t.value" class="tab-bar-item" :class="activeTab === t.value ? 'tab-bar-item-active' : ''" @click="activeTab = t.value">{{ t.label }}</button>
    </div>

    <!-- filters -->
    <div class="light2-filters">
      <input v-model="filters.keyword" type="text" placeholder="搜索规则名称 / 报告编号..." class="light2-input" @keyup.enter="searchCount += 1" />
      <input v-model="filters.sessionId" type="text" placeholder="采集编号..." class="light2-input" @keyup.enter="searchCount += 1" />
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="searchCount += 1">搜索</button>
    </div>

    <!-- rules table -->
    <div v-if="activeTab === 'rules'" class="light2-tbl">
      <table>
        <thead>
          <tr>
            <th>规则名称</th>
            <th>类型</th>
            <th>适用对象</th>
            <th>优先级</th>
            <th>执行模式</th>
            <th>绑定范围</th>
            <th>更新时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredRules" :key="item.id">
            <td style="font-weight:600">{{ item.name }}</td>
            <td>{{ item.ruleType }}</td>
            <td>{{ item.appliesTo }}</td>
            <td>{{ item.priority }}</td>
            <td>{{ item.executionMode }}</td>
            <td>{{ item.bindings.map(b => b.scopeLabel).join(" / ") }}</td>
            <td>{{ formatDateTime(item.updatedAt) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- logs table -->
    <div v-else class="light2-tbl">
      <table>
        <thead>
          <tr>
            <th>报告编号</th>
            <th>taskId</th>
            <th>采集编号</th>
            <th>关联资产</th>
            <th>状态</th>
            <th>规则模板</th>
            <th>创建时间</th>
            <th style="width:80px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredLogs" :key="item.id">
            <td style="font-weight:600">{{ item.id }}</td>
            <td>{{ item.taskId }}</td>
            <td>{{ item.sessionId }}</td>
            <td>{{ item.assetName }}</td>
            <td><span class="light2-badge" :class="badgeClass(item.qcStatus)"><span class="light2-bdot" :style="{background:badgeColor(item.qcStatus)}" />{{ item.qcStatus }}</span></td>
            <td>{{ item.ruleTemplate }}</td>
            <td>{{ formatDateTime(item.createdAt) }}</td>
            <td><button class="light2-btn light2-btn-sec light2-btn-sm" @click="selectedLog = item">详情</button></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- drawer -->
    <AppDrawer :open="Boolean(selectedLog)" title="质检日志详情" @close="selectedLog = null">
      <div v-if="selectedLog" class="space-y-3">
        <div style="color:var(--color-text-secondary)"><span style="font-weight:600;color:var(--color-text-primary)">资产：</span>{{ selectedLog.assetName }}</div>
        <div style="color:var(--color-text-secondary)"><span style="font-weight:600;color:var(--color-text-primary)">摘要：</span>{{ selectedLog.summary }}</div>
        <pre class="overflow-x-auto rounded-[10px] bg-slate-900 px-4 py-3 text-xs text-slate-100">{{ JSON.stringify(selectedLog.report.reportJson, null, 2) }}</pre>
      </div>
    </AppDrawer>

    <!-- dialog -->
    <AppDialog :open="dialogOpen" title="新建质检规则" description="第一阶段先保留前端占位字段。" @close="dialogOpen = false" @confirm="submitMock">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm" style="color:var(--color-text-secondary)">规则名称</span>
          <input v-model="dialogForm.name" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm" style="color:var(--color-text-secondary)">适用对象</span>
          <input v-model="dialogForm.appliesTo" class="app-input app-input-compact" />
        </label>
      </div>
      <p v-if="dialogMessage" class="mt-3 text-sm" style="color:var(--color-text-tertiary)">{{ dialogMessage }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchQcLogs, fetchQcRules } from "@/api/platform";
import AppDialog from "@/components/AppDialog.vue";
import AppDrawer from "@/components/AppDrawer.vue";
import type { QcLogRecord, QcRuleRecord } from "@/types/platform";
import { formatDateTime } from "@/utils/format";

const activeTab = ref("rules");
const dialogOpen = ref(false);
const dialogMessage = ref("");
const searchCount = ref(0);
const tabs = [
  { label: "质检规则", value: "rules" },
  { label: "质检结果", value: "logs" },
];
const rules = ref<QcRuleRecord[]>([]);
const logs = ref<QcLogRecord[]>([]);
const selectedLog = ref<QcLogRecord | null>(null);
const filters = reactive({ keyword: "", sessionId: "" });
const dialogForm = reactive({ name: "", appliesTo: "" });

const filteredRules = computed(() =>
  rules.value.filter((item) => {
    const keyword = filters.keyword.trim().toLowerCase();
    const matchKeyword = !keyword || [item.name, item.appliesTo, item.ruleType].some((v) => v.toLowerCase().includes(keyword));
    const matchSession = !filters.sessionId || item.bindings.some((b) => b.scopeLabel.includes(filters.sessionId.trim()));
    return matchKeyword && matchSession;
  }),
);

const filteredLogs = computed(() =>
  logs.value.filter((item) => {
    const keyword = filters.keyword.trim().toLowerCase();
    const matchKeyword = !keyword || [String(item.id), item.assetName, item.ruleTemplate, item.summary].some((v) => v.toLowerCase().includes(keyword));
    const matchSession = !filters.sessionId || item.sessionId.includes(filters.sessionId.trim());
    return matchKeyword && matchSession;
  }),
);

const BADGE_MAP: Record<string, { cls: string; color: string }> = {
  PASSED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  QC_PASSED: { cls: "light2-badge-ok", color: "#0d7d3e" },
  QC_WARNING: { cls: "light2-badge-warn", color: "#b87a0a" },
  QC_FAILED: { cls: "light2-badge-err", color: "#c5222f" },
  PENDING: { cls: "light2-badge-neutral", color: "#9298a3" },
  FAILED: { cls: "light2-badge-err", color: "#c5222f" },
};
function badgeClass(s: string) { return BADGE_MAP[s]?.cls ?? "light2-badge-neutral"; }
function badgeColor(s: string) { return BADGE_MAP[s]?.color ?? "#9298a3"; }

function submitMock() {
  dialogMessage.value = "规则结构已预留，待后端规则实体接入。";
  window.setTimeout(() => { dialogOpen.value = false; dialogMessage.value = ""; }, 800);
}

onMounted(async () => {
  rules.value = await fetchQcRules();
  logs.value = await fetchQcLogs();
});
</script>
