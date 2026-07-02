<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>{{ resolved.title }}</h1>
        <p>{{ resolved.description }}</p>
      </div>
      <button class="light2-btn light2-btn-primary" @click="dialogOpen = true">+ 新建</button>
    </div>

    <hr class="light2-divider" />

    <!-- filters -->
    <div class="light2-filters">
      <input v-model="filters.keyword" type="text" placeholder="搜索关键词..." class="light2-input" @keyup.enter="searchCount += 1" />
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="searchCount += 1">搜索</button>
      <button class="light2-btn light2-btn-sec light2-btn-sm" @click="filters.keyword = ''; searchCount += 1">重置</button>
    </div>

    <!-- info cards -->
    <div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px;margin-bottom:16px">
      <div v-for="(item, idx) in resolved.cards" :key="item.label" class="rounded-[12px] border bg-white px-4 py-3.5 shadow-[var(--shadow-card)]" :style="{ borderLeft: `3px solid ${idx === 0 ? 'var(--color-brand-500)' : '#0d9444'}` }">
        <div style="font-size:12px;font-weight:600;color:var(--color-text-tertiary);text-transform:uppercase;letter-spacing:0.06em">{{ item.label }}</div>
        <div style="font-size:15px;font-weight:700;color:var(--color-text-primary);margin-top:0.35rem">{{ item.value }}</div>
        <div v-if="item.description" style="font-size:12px;color:var(--color-text-secondary);margin-top:0.25rem">{{ item.description }}</div>
      </div>
    </div>

    <!-- placeholder table (light-2 style) -->
    <div class="light2-tbl">
      <table>
        <thead>
          <tr>
            <th>名称</th>
            <th>类型</th>
            <th>状态</th>
            <th>更新时间</th>
            <th style="width:80px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td colspan="5" style="text-align:center;padding:40px 0;color:var(--color-text-secondary)">当前为 {{ resolved.title }} 的管理入口，后续接入完整实体列表。</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- dialog -->
    <AppDialog :open="dialogOpen" :title="resolved.dialogTitle" description="先保留前端占位字段。" @close="dialogOpen = false" @confirm="submitMock(`${resolved.title}已创建`)">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm" style="color:var(--color-text-secondary)">{{ resolved.fieldLabel }}</span>
          <input v-model="dialogValue" class="app-input app-input-compact" />
        </label>
      </div>
      <p v-if="dialogMessage" class="mt-3 text-sm" style="color:var(--color-text-tertiary)">{{ dialogMessage }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute } from "vue-router";
import AppDialog from "@/components/AppDialog.vue";

const route = useRoute();
const dialogOpen = ref(false);
const dialogValue = ref("");
const dialogMessage = ref("");
const searchCount = ref(0);
const filters = ref({ keyword: "" });

const config = {
  users: {
    title: "用户管理",
    description: "查看和维护用户信息 · 账号、角色、状态",
    dialogTitle: "新建用户",
    fieldLabel: "用户名称",
    cards: [
      { label: "用户列表", value: "账号、角色、状态", description: "当前活跃用户 12 人" },
      { label: "角色管理", value: "管理员 / 操作员 / 查看者", description: "后续接入权限系统" }
    ]
  },
  devices: {
    title: "设备管理",
    description: "查看和维护采集设备信息 · 设备类型与台账",
    dialogTitle: "新建设备",
    fieldLabel: "设备名称",
    cards: [
      { label: "设备类型", value: "camera / imu / hmd", description: "支持扩展更多设备类型" },
      { label: "设备台账", value: "维护基础信息", description: "当前登记设备 8 台" }
    ]
  },
  workflows: {
    title: "流程管理",
    description: "查看和维护流程规则 · 规则组合与关联对象",
    dialogTitle: "新建流程",
    fieldLabel: "流程名称",
    cards: [
      { label: "流程定位", value: "规则组合 · 减少重复操作", description: "自定义处理+质检+导出链路" },
      { label: "关联对象", value: "处理 / 质检 / 导出", description: "覆盖主要业务流程" }
    ]
  },
  storages: {
    title: "存储管理",
    description: "查看和维护存储配置 · 统一配置入口",
    dialogTitle: "新建存储",
    fieldLabel: "存储名称",
    cards: [
      { label: "存储源", value: "阿里云 OSS", description: "Bucket: mmdp-test / mmdp-prod" },
      { label: "配置台账", value: "Endpoint · AK · Region", description: "通过环境变量注入 · 不存 Git" }
    ]
  },
  dictionaries: {
    title: "字典管理",
    description: "查看和维护枚举字典 · 统一前后端展示",
    dialogTitle: "新建字典项",
    fieldLabel: "字典项名称",
    cards: [
      { label: "字典范围", value: "核心枚举", description: "资产类型 · 状态 · 模态 · 来源" },
      { label: "维护目标", value: "可维护枚举 · 减少硬编码", description: "支持动态增删改查" }
    ]
  }
} as const;

const resolved = computed(() => config[(route.params.module as keyof typeof config) || "users"] ?? config.users);

function submitMock(message: string) {
  dialogMessage.value = message;
  window.setTimeout(() => { dialogOpen.value = false; dialogMessage.value = ""; dialogValue.value = ""; }, 600);
}
</script>
