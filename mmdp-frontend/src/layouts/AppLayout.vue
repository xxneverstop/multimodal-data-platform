<template>
  <div class="min-h-screen bg-slate-100 text-[var(--color-text-900)]">
    <div class="mx-auto flex min-h-screen max-w-[1440px]">
      <aside class="hidden w-[248px] shrink-0 border-r border-slate-200 bg-slate-900 text-slate-100 lg:flex lg:flex-col">
        <div class="border-b border-slate-800 px-5 py-5">
          <div class="inline-flex items-center gap-2 rounded-[8px] bg-slate-800 px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.14em] text-slate-300">
            <span class="h-1.5 w-1.5 rounded-full bg-emerald-400"></span>
            MMDP Console
          </div>
          <h1 class="mt-3 text-lg font-semibold tracking-tight text-white">多模态数据平台</h1>
          <p class="mt-1 text-xs leading-5 text-slate-400">实验室内部采集任务、文件管理与质检报告后台。</p>
        </div>

        <div class="flex-1 space-y-6 px-3 py-4">
          <section>
            <p class="mb-2 px-2 text-[11px] font-medium uppercase tracking-[0.14em] text-slate-500">核心模块</p>
            <nav class="space-y-1">
              <RouterLink
                v-for="item in coreMenuItems"
                :key="item.to"
                :to="item.to"
                class="group flex min-h-9 items-center gap-3 rounded-[8px] px-2.5 py-2 text-sm transition"
                :class="navClass(item.to)"
              >
                <span class="h-4 w-0.5 rounded-full transition" :class="isActive(item.to) ? 'bg-slate-100' : 'bg-transparent group-hover:bg-slate-500'"></span>
                <div class="min-w-0 flex-1">
                  <div class="truncate font-medium">{{ item.label }}</div>
                  <div v-if="isActive(item.to)" class="mt-0.5 truncate text-[11px] leading-4 text-slate-400">{{ item.description }}</div>
                </div>
              </RouterLink>
            </nav>
          </section>

          <section>
            <p class="mb-2 px-2 text-[11px] font-medium uppercase tracking-[0.14em] text-slate-500">规划模块</p>
            <nav class="space-y-1">
              <RouterLink
                v-for="item in planningMenuItems"
                :key="item.to"
                :to="item.to"
                class="group flex min-h-9 items-center gap-3 rounded-[8px] px-2.5 py-2 text-sm transition"
                :class="navClass(item.to)"
              >
                <span class="h-4 w-0.5 rounded-full transition" :class="isActive(item.to) ? 'bg-slate-100' : 'bg-transparent group-hover:bg-slate-500'"></span>
                <div class="min-w-0 flex-1">
                  <div class="truncate font-medium">{{ item.label }}</div>
                  <div v-if="isActive(item.to)" class="mt-0.5 truncate text-[11px] leading-4 text-slate-400">{{ item.description }}</div>
                </div>
              </RouterLink>
            </nav>
          </section>
        </div>

        <div class="border-t border-slate-800 px-5 py-4">
          <div class="rounded-[10px] bg-slate-950 px-3 py-3 text-xs">
            <div class="font-medium uppercase tracking-[0.14em] text-slate-500">平台状态</div>
            <div class="mt-1 text-sm font-medium text-white">任务管理与质检链路已可用</div>
            <div class="mt-1 leading-5 text-slate-400">当前版本聚焦采集任务、文件上传和 QC 报告展示。</div>
          </div>
        </div>
      </aside>

      <div class="min-w-0 flex-1">
        <header class="border-b border-slate-200 bg-white px-4 py-3 md:px-5 lg:px-6">
          <div class="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div class="space-y-1.5">
              <div class="inline-flex items-center gap-2 rounded-[8px] bg-slate-100 px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.14em] text-slate-500 lg:hidden">
                <span class="h-1.5 w-1.5 rounded-full bg-emerald-500"></span>
                MMDP Console
              </div>
              <div>
                <p class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">Lab Data Platform</p>
                <h2 class="mt-1 text-lg font-semibold tracking-tight text-slate-900">{{ pageTitle }}</h2>
              </div>
              <p class="max-w-3xl text-sm leading-5 text-slate-500">{{ pageDescription }}</p>
            </div>

            <div class="flex flex-wrap gap-2">
              <div class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                <div class="font-medium uppercase tracking-[0.14em] text-slate-400">Workspace</div>
                <div class="mt-1 text-sm font-semibold text-slate-800">MMDP Frontend</div>
              </div>
              <div class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                <div class="font-medium uppercase tracking-[0.14em] text-slate-400">当前分区</div>
                <div class="mt-1 text-sm font-semibold text-slate-800">{{ currentSection }}</div>
              </div>
            </div>
          </div>

          <nav class="mt-4 flex gap-2 overflow-x-auto pb-1 lg:hidden">
            <RouterLink
              v-for="item in mobileMenuItems"
              :key="item.to"
              :to="item.to"
              class="shrink-0 rounded-[8px] border px-3 py-1.5 text-sm font-medium transition"
              :class="isActive(item.to) ? 'border-slate-900 bg-slate-900 text-white' : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'"
            >
              {{ item.label }}
            </RouterLink>
          </nav>
        </header>

        <main class="px-4 py-5 md:px-5 lg:px-6">
          <div class="mx-auto max-w-[1080px]">
            <RouterView />
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink, RouterView, useRoute } from "vue-router";

const route = useRoute();

const coreMenuItems = [
  { label: "采集任务", to: "/tasks", description: "任务列表、详情与执行入口" },
  { label: "新建任务", to: "/tasks/new", description: "创建新的采集工作项" }
];

const planningMenuItems = [
  { label: "数据文件", to: "/placeholder/data-files", description: "文件目录、索引与归档" },
  { label: "质检中心", to: "/placeholder/qc-reports", description: "质检报告聚合与筛选" },
  { label: "数据集管理", to: "/placeholder/datasets", description: "数据资产与版本组织" },
  { label: "处理流水线", to: "/placeholder/pipelines", description: "处理流程、执行与监控" },
  { label: "系统设置", to: "/placeholder/settings", description: "配置项、权限与环境设置" }
];

const mobileMenuItems = computed(() => [...coreMenuItems, ...planningMenuItems]);

const pageTitle = computed(() => (route.meta.title as string) ?? "多模态数据平台");
const pageDescription = computed(
  () => (route.meta.description as string) ?? "面向采集任务、文件资产与质检报告的统一工作台。"
);

const currentSection = computed(() => (route.path.startsWith("/tasks") ? "任务中心" : "规划模块"));

function isActive(path: string) {
  if (path === "/tasks") {
    return route.path === "/tasks" || route.path.startsWith("/tasks/");
  }
  return route.path === path;
}

function navClass(path: string) {
  return isActive(path)
    ? "bg-slate-800 text-white"
    : "text-slate-300 hover:bg-slate-800/80 hover:text-white";
}
</script>
