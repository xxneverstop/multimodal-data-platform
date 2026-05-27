<template>
  <div class="min-h-screen bg-[var(--color-surface-page)] text-[var(--color-text-900)]">
    <header class="fixed inset-x-0 top-0 z-40 border-b border-slate-200 bg-white">
      <div class="flex h-14 items-center justify-between gap-4 px-4 md:px-5">
        <RouterLink
          to="/home"
          class="inline-flex items-center text-slate-900 transition hover:text-[var(--color-brand-600)]"
        >
          <MmdpLogo :size="32" />
        </RouterLink>

        <div ref="accountMenuRef" class="relative">
          <button
            type="button"
            class="inline-flex h-9 items-center gap-2 rounded-[10px] border border-slate-200 bg-white px-3 text-sm text-slate-700 transition hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900"
            @click="accountMenuOpen = !accountMenuOpen"
          >
            <BaseIcon name="user-circle" size="sm" />
            <span class="hidden max-w-[120px] truncate font-medium sm:inline">平台管理员</span>
            <BaseIcon
              name="chevron-down"
              size="sm"
              class="transition"
              :class="accountMenuOpen ? 'rotate-180' : ''"
            />
          </button>

          <div
            v-if="accountMenuOpen"
            class="absolute right-0 top-[calc(100%+8px)] w-[180px] rounded-[12px] border border-slate-200 bg-white p-1.5 shadow-[0_12px_32px_rgba(15,23,42,0.10)]"
          >
            <button
              v-for="action in accountActions"
              :key="action.label"
              type="button"
              class="flex w-full items-center gap-2 rounded-[8px] px-3 py-2 text-left text-sm text-slate-700 transition hover:bg-slate-50 hover:text-slate-900"
              @click="handleAccountAction(action.label)"
            >
              <BaseIcon :name="action.icon" size="sm" />
              <span>{{ action.label }}</span>
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="flex min-h-screen w-full min-w-0 pt-14">
      <aside class="hidden shrink-0 border-r border-slate-200 bg-white lg:flex">
        <div class="sticky top-14 flex h-[calc(100vh-56px)] w-[56px] flex-col items-center gap-2 px-2 py-4">
          <RouterLink
            to="/home"
            class="nav-rail-button"
            :class="route.path.startsWith('/home') ? 'nav-rail-button-active' : ''"
            title="主页"
          >
            <BaseIcon name="home" />
          </RouterLink>
          <SideNavGroup label="功能" icon="grid" :active="isFunctionRoute" @toggle="setSection('function')" />
          <SideNavGroup label="管理" icon="settings" :active="isManagementRoute" @toggle="setSection('management')" />
        </div>
      </aside>

      <aside
        v-if="showSecondarySidebar"
        class="hidden w-[92px] shrink-0 border-r border-slate-200 bg-white lg:flex lg:flex-col"
      >
        <div class="sticky top-14 h-[calc(100vh-56px)] px-2 py-4">
          <nav class="space-y-1">
            <RouterLink
              v-for="item in secondaryItems"
              :key="item.to"
              :to="item.to"
              class="flex items-center justify-center rounded-[8px] border px-2 py-2 text-sm transition"
              :class="
                route.path === item.to
                  ? 'border-slate-300 bg-slate-100 text-slate-900'
                  : 'border-transparent text-slate-600 hover:bg-slate-50 hover:text-slate-900'
              "
            >
              <span class="w-full truncate text-center">{{ item.label }}</span>
            </RouterLink>
          </nav>
        </div>
      </aside>

      <div class="min-w-0 flex-1">
        <div class="border-b border-slate-200 bg-white px-4 py-2.5 md:px-5 lg:hidden">
          <nav class="flex gap-2 overflow-x-auto pb-1">
            <RouterLink
              v-for="item in mobileItems"
              :key="item.to"
              :to="item.to"
              class="shrink-0 rounded-[8px] border px-3 py-1.5 text-sm transition"
              :class="
                route.path === item.to
                  ? 'border-slate-300 bg-slate-100 text-slate-900'
                  : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'
              "
            >
              {{ item.label }}
            </RouterLink>
          </nav>
        </div>

        <main class="min-w-0 px-4 py-4 md:px-5">
          <div class="w-full min-w-0">
            <RouterView />
          </div>
        </main>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { RouterLink, RouterView, useRoute, useRouter } from "vue-router";
import BaseIcon from "@/components/BaseIcon.vue";
import MmdpLogo from "@/components/MmdpLogo.vue";
import SideNavGroup from "@/components/SideNavGroup.vue";

const route = useRoute();
const router = useRouter();

const functionItems = [
  { label: "数据", to: "/data" },
  { label: "上传", to: "/upload" },
  { label: "采集", to: "/acquisition" },
  { label: "会话", to: "/sessions" },
  { label: "处理", to: "/processing" },
  { label: "标注", to: "/annotation" },
  { label: "质检", to: "/qc" },
  { label: "导出", to: "/export" },
  { label: "原型", to: "/collector" }
];

const managementItems = [
  { label: "用户", to: "/management/users" },
  { label: "设备", to: "/management/devices" },
  { label: "流程", to: "/management/workflows" },
  { label: "存储", to: "/management/storages" },
  { label: "字典", to: "/management/dictionaries" }
];

const mobileItems = [{ label: "主页", to: "/home" }, ...functionItems];

const isHomeRoute = computed(() => route.path.startsWith("/home"));
const isManagementRoute = computed(() => route.path.startsWith("/management"));
const isFunctionRoute = computed(() => !isHomeRoute.value && !isManagementRoute.value);

const showSecondarySidebar = computed(() => !isHomeRoute.value);
const secondaryItems = computed(() => (isManagementRoute.value ? managementItems : functionItems));

const accountMenuRef = ref<HTMLElement | null>(null);
const accountMenuOpen = ref(false);
const accountActions = [
  { label: "账号设置", icon: "settings" },
  { label: "个人信息", icon: "user-circle" },
  { label: "退出登录", icon: "users" }
];

function setSection(target: "function" | "management") {
  if (target === "function") {
    if (!isFunctionRoute.value) {
      router.push("/data");
    }
    return;
  }

  if (!isManagementRoute.value) {
    router.push("/management/users");
  }
}

function handleAccountAction(_label: string) {
  accountMenuOpen.value = false;
}

function handleDocumentClick(event: MouseEvent) {
  const target = event.target;
  if (!(target instanceof Node)) {
    return;
  }
  if (!accountMenuRef.value?.contains(target)) {
    accountMenuOpen.value = false;
  }
}

onMounted(() => {
  document.addEventListener("click", handleDocumentClick);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", handleDocumentClick);
});
</script>
