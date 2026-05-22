<template>
  <div class="space-y-4">
    <PageHeader eyebrow="管理" :title="resolved.title" :description="resolved.description" />

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

    <PageCard :title="resolved.title">
      <DataTableShell>
        <template #toolbar>
          <div class="text-sm text-slate-500">当前为 {{ resolved.title }} 的管理入口。</div>
        </template>
        <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          <EntitySummaryCard v-for="item in resolved.cards" :key="item.label" :label="item.label" :value="item.value" :description="item.description" />
        </div>
      </DataTableShell>
    </PageCard>

    <AppDialog :open="dialogOpen" :title="resolved.dialogTitle" description="先保留前端占位字段。" @close="dialogOpen = false" @confirm="submitMock(`${resolved.title}已创建`)">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">{{ resolved.fieldLabel }}</span>
          <input v-model="dialogValue" class="app-input app-input-compact" />
        </label>
      </div>
      <p v-if="dialogMessage" class="mt-3 text-sm text-slate-500">{{ dialogMessage }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import AppDialog from "@/components/AppDialog.vue";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import DataTableShell from "@/components/DataTableShell.vue";
import EntitySummaryCard from "@/components/EntitySummaryCard.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import SearchActionBar from "@/components/SearchActionBar.vue";
import type { FilterField } from "@/components/FilterBar.vue";

const route = useRoute();
const dialogOpen = ref(false);
const dialogValue = ref("");
const dialogMessage = ref("");
const searchCount = ref(0);
const filters = ref({ keyword: "" });
const filterFields: FilterField[] = [{ key: "keyword", label: "关键词", placeholder: "请输入关键词" }];

const config = {
  users: {
    title: "用户",
    description: "查看和维护用户信息。",
    dialogTitle: "新建用户",
    fieldLabel: "用户名称",
    cards: [
      { label: "当前结构", value: "用户列表", description: "账号、角色、状态" },
      { label: "角色", value: "预留", description: "后续接权限" }
    ]
  },
  devices: {
    title: "设备",
    description: "查看和维护采集设备信息。",
    dialogTitle: "新建设备",
    fieldLabel: "设备名称",
    cards: [
      { label: "设备类型", value: "camera / imu / hmd", description: "支持扩展更多类型" },
      { label: "当前目标", value: "设备台账", description: "维护基础信息" }
    ]
  },
  workflows: {
    title: "流程",
    description: "查看和维护流程规则。",
    dialogTitle: "新建流程",
    fieldLabel: "流程名称",
    cards: [
      { label: "流程定位", value: "规则组合", description: "减少重复操作" },
      { label: "关联对象", value: "处理 / 质检 / 导出", description: "覆盖主要流程" }
    ]
  },
  storages: {
    title: "存储",
    description: "查看和维护存储配置。",
    dialogTitle: "新建存储",
    fieldLabel: "存储名称",
    cards: [
      { label: "存储源", value: "MinIO / OSS / COS", description: "统一配置入口" },
      { label: "当前目标", value: "配置台账", description: "先保留结构" }
    ]
  },
  dictionaries: {
    title: "字典",
    description: "查看和维护枚举字典。",
    dialogTitle: "新建字典项",
    fieldLabel: "字典项名称",
    cards: [
      { label: "字典范围", value: "核心枚举", description: "统一前后端展示" },
      { label: "当前目标", value: "可维护枚举", description: "减少硬编码" }
    ]
  }
} as const;

const resolved = computed(() => config[(route.params.module as keyof typeof config) || "users"] ?? config.users);

function submitMock(message: string) {
  dialogMessage.value = message;
  window.setTimeout(() => {
    dialogOpen.value = false;
    dialogMessage.value = "";
    dialogValue.value = "";
  }, 600);
}
</script>
