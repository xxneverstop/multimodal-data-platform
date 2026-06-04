<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 质检" title="质检" description="分规则和结果两个视角查看当前质检能力。" />

    <SectionTabs v-model="activeTab" :items="tabs" />

    <PageCard title="筛选条件">
      <SearchActionBar v-model="filters" :fields="filterFields" @search="searchCount += 1">
        <template #actions>
          <BaseButton v-if="activeTab === 'rules'" variant="primary" size="md" @click="dialogOpen = true">
            <BaseIcon name="plus" size="sm" />
            新建规则
          </BaseButton>
        </template>
      </SearchActionBar>
    </PageCard>

    <PageCard v-if="activeTab === 'rules'" title="质检规则">
      <div class="mb-3 rounded-[10px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
        当前规则为前端结构占位，用于承接后续“按 Profile 自动推荐 + 上传时勾选规则”的能力。
      </div>
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">规则名称</th>
              <th class="px-3 py-2.5 font-medium">类型</th>
              <th class="px-3 py-2.5 font-medium">适用对象</th>
              <th class="px-3 py-2.5 font-medium">优先级</th>
              <th class="px-3 py-2.5 font-medium">执行模式</th>
              <th class="px-3 py-2.5 font-medium">绑定范围</th>
              <th class="px-3 py-2.5 font-medium">更新时间</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="item in filteredRules" :key="item.id" class="hover:bg-slate-50">
              <td class="px-3 py-2.5 font-medium text-slate-900">{{ item.name }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.ruleType }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.appliesTo }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.priority }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.executionMode }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.bindings.map((binding) => binding.scopeLabel).join(" / ") }}</td>
              <td class="px-3 py-2.5 text-slate-500">{{ formatDateTime(item.updatedAt) }}</td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>

    <PageCard v-else title="质检结果">
      <div class="mb-3 rounded-[10px] border border-slate-200 bg-slate-50 px-4 py-3 text-sm text-slate-600">
        当前结果以文件级质检为主，Session 级质检汇总会在后端补齐后接入。
      </div>
      <DataTableShell>
        <table class="min-w-full text-left text-sm">
          <thead class="bg-slate-50 text-xs text-slate-500">
            <tr>
              <th class="px-3 py-2.5 font-medium">报告编号</th>
              <th class="px-3 py-2.5 font-medium">taskId</th>
              <th class="px-3 py-2.5 font-medium">采集编号</th>
              <th class="px-3 py-2.5 font-medium">关联资产</th>
              <th class="px-3 py-2.5 font-medium">状态</th>
              <th class="px-3 py-2.5 font-medium">规则模板</th>
              <th class="px-3 py-2.5 font-medium">创建时间</th>
              <th class="px-3 py-2.5 font-medium text-right">操作</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-slate-200 bg-white">
            <tr v-for="item in filteredLogs" :key="item.id" class="hover:bg-slate-50">
              <td class="px-3 py-2.5 font-medium text-slate-900">{{ item.id }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.taskId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.sessionId }}</td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.assetName }}</td>
              <td class="px-3 py-2.5"><StatusBadge :status="item.qcStatus" /></td>
              <td class="px-3 py-2.5 text-slate-600">{{ item.ruleTemplate }}</td>
              <td class="px-3 py-2.5 text-slate-500">{{ formatDateTime(item.createdAt) }}</td>
              <td class="px-3 py-2.5 text-right">
                <BaseButton size="sm" variant="ghost" @click="selectedLog = item">详情</BaseButton>
              </td>
            </tr>
          </tbody>
        </table>
      </DataTableShell>
    </PageCard>

    <AppDrawer :open="Boolean(selectedLog)" title="质检日志详情" @close="selectedLog = null">
      <div v-if="selectedLog" class="space-y-3">
        <div class="text-sm text-slate-600"><span class="font-medium text-slate-900">资产：</span>{{ selectedLog.assetName }}</div>
        <div class="text-sm text-slate-600"><span class="font-medium text-slate-900">摘要：</span>{{ selectedLog.summary }}</div>
        <pre class="overflow-x-auto rounded-[10px] bg-slate-900 px-4 py-3 text-xs text-slate-100">{{ JSON.stringify(selectedLog.report.reportJson, null, 2) }}</pre>
      </div>
    </AppDrawer>

    <AppDialog :open="dialogOpen" title="新建质检规则" description="第一阶段先保留前端占位字段，后续接后端实体。" @close="dialogOpen = false" @confirm="submitMock">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">规则名称</span>
          <input v-model="dialogForm.name" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">适用对象</span>
          <input v-model="dialogForm.appliesTo" class="app-input app-input-compact" />
        </label>
      </div>
      <p v-if="dialogMessage" class="mt-3 text-sm text-slate-500">{{ dialogMessage }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchQcLogs, fetchQcRules } from "@/api/platform";
import AppDialog from "@/components/AppDialog.vue";
import AppDrawer from "@/components/AppDrawer.vue";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import SectionTabs from "@/components/SectionTabs.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { FilterField } from "@/components/FilterBar.vue";
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
const filterFields: FilterField[] = [
  { key: "keyword", label: "关键字", placeholder: "请输入规则名称或报告编号" },
  { key: "sessionId", label: "采集编号", placeholder: "请输入采集编号" },
];

const filteredRules = computed(() =>
  rules.value.filter((item) => {
    const keyword = filters.keyword.trim().toLowerCase();
    const matchKeyword = !keyword || [item.name, item.appliesTo, item.ruleType].some((value) => value.toLowerCase().includes(keyword));
    const matchSession = !filters.sessionId || item.bindings.some((binding) => binding.scopeLabel.includes(filters.sessionId.trim()));
    return matchKeyword && matchSession;
  }),
);

const filteredLogs = computed(() =>
  logs.value.filter((item) => {
    const keyword = filters.keyword.trim().toLowerCase();
    const matchKeyword =
      !keyword ||
      [String(item.id), item.assetName, item.ruleTemplate, item.summary].some((value) => value.toLowerCase().includes(keyword));
    const matchSession = !filters.sessionId || item.sessionId.includes(filters.sessionId.trim());
    return matchKeyword && matchSession;
  }),
);

function submitMock() {
  dialogMessage.value = "规则结构已预留，待后端规则实体接入。";
  window.setTimeout(() => {
    dialogOpen.value = false;
    dialogMessage.value = "";
  }, 800);
}

onMounted(async () => {
  rules.value = await fetchQcRules();
  logs.value = await fetchQcLogs();
});
</script>
