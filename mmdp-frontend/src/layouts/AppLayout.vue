<template>
  <div class="min-h-screen bg-[var(--color-surface-page)] text-[var(--color-text-900)]">
    <header class="fixed inset-x-0 top-0 z-40 bg-white" style="border-bottom:1px solid #d0d5dd">
      <div class="flex h-14 items-center justify-between gap-4 px-4 md:px-5">
        <RouterLink
          to="/home"
          class="inline-flex items-center text-[var(--color-text-primary)] transition hover:text-[var(--color-brand-600)]"
        >
          <MmdpLogo :size="32" />
        </RouterLink>

        <div ref="accountMenuRef" class="relative">
          <button
            type="button"
            class="inline-flex h-8 items-center gap-2 rounded-[var(--radius-md)] border border-[var(--color-border-default)] bg-white px-3 text-sm text-[var(--color-text-secondary)] transition hover:border-[var(--color-text-tertiary)] hover:bg-[var(--color-hover-subtle)] hover:text-[var(--color-text-primary)]"
            @click="accountMenuOpen = !accountMenuOpen"
          >
            <BaseIcon name="user-circle" size="sm" />
            <span class="hidden max-w-[180px] truncate font-medium sm:inline">
              {{ displayName }}
            </span>
            <BaseIcon
              name="chevron-down"
              size="sm"
              class="transition"
              :class="accountMenuOpen ? 'rotate-180' : ''"
            />
          </button>

          <div
            v-if="accountMenuOpen"
            class="absolute right-0 top-[calc(100%+8px)] w-[220px] rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-white p-1.5 shadow-[var(--shadow-dropdown)]"
          >
            <div class="border-b border-[var(--color-border-soft)] px-3 py-2">
              <div class="text-sm font-semibold text-[var(--color-text-primary)]">{{ displayName }}</div>
              <div class="mt-1 text-xs text-[var(--color-text-tertiary)]">
                {{ authStore.user.value?.username }} · {{ roleLabel }}
              </div>
            </div>
            <button
              type="button"
              class="mt-1 flex w-full items-center gap-2 rounded-[var(--radius-md)] px-3 py-2 text-left text-sm text-[var(--color-text-secondary)] transition hover:bg-[var(--color-hover-subtle)] hover:text-[var(--color-text-primary)]"
              @click="handleLogout"
            >
              <BaseIcon name="log-out" size="sm" />
              <span>退出登录</span>
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="flex min-h-screen w-full min-w-0 pt-14">
      <aside class="hidden shrink-0 bg-white lg:flex" style="border-right:1px solid #d0d5dd">
        <div class="sticky top-14 flex h-[calc(100vh-56px)] w-[56px] flex-col items-center gap-2 px-2 py-4">
          <RouterLink
            to="/home"
            class="nav-rail-button"
            :class="route.path.startsWith('/home') ? 'nav-rail-button-active' : ''"
            title="首页"
          >
            <BaseIcon name="home" />
          </RouterLink>
          <div style="width:24px;height:1px;background:#d0d5dd;margin:4px 0" />
          <SideNavGroup label="功能" icon="grid" :active="isFunctionRoute" @toggle="setSection('function')" />
          <div v-if="authStore.isAdmin.value" style="width:24px;height:1px;background:#d0d5dd;margin:4px 0" />
          <SideNavGroup
            v-if="authStore.isAdmin.value"
            label="管理"
            icon="settings"
            :active="isManagementRoute"
            @toggle="setSection('management')"
          />
        </div>
      </aside>

      <aside
        v-if="showSecondarySidebar"
        class="hidden w-[100px] shrink-0 bg-white lg:flex lg:flex-col" style="border-right:1px solid #d0d5dd"
      >
        <div class="sticky top-14 h-[calc(100vh-56px)] px-1.5 py-3">
          <nav class="space-y-0.5">
            <RouterLink
              v-for="item in secondaryItems"
              :key="item.to"
              :to="item.to"
              class="relative flex items-center gap-2 rounded-[var(--radius-md)] border border-transparent px-2.5 py-2 text-[13.5px] font-medium tracking-[0.01em] transition"
              :class="
                route.path === item.to
                  ? ['app-nav-module-active before:absolute before:left-0 before:top-1.5 before:bottom-1.5 before:w-[2.5px] before:rounded-r', 'app-tone-brand']
                  : 'text-[var(--color-text-tertiary)] hover:bg-[var(--color-hover-subtle)] hover:text-[var(--color-text-primary)] hover:shadow-[0_1px_2px_rgba(0,0,0,0.03)]'
              "
            >
              <BaseIcon v-if="item.icon" :name="item.icon" size="sm" class="shrink-0" />
              <span class="truncate">{{ item.label }}</span>
            </RouterLink>
          </nav>
        </div>
      </aside>

      <div class="min-w-0 flex-1">
        <div class="bg-white px-4 py-2.5 md:px-5 lg:hidden" style="border-bottom:1px solid #d0d5dd">
          <nav class="flex gap-2 overflow-x-auto pb-1">
            <RouterLink
              v-for="item in mobileItems"
              :key="item.to"
              :to="item.to"
              class="inline-flex shrink-0 items-center gap-1.5 rounded-[var(--radius-md)] border px-3 py-1.5 text-sm transition"
              :class="
                route.path === item.to
                  ? ['app-nav-module-active', 'app-tone-brand']
                  : 'border-[var(--color-border-default)] bg-white text-[var(--color-text-secondary)] hover:bg-[var(--color-hover-subtle)] hover:text-[var(--color-text-primary)]'
              "
            >
              <BaseIcon v-if="item.icon" :name="item.icon" size="sm" class="shrink-0" />
              <span>{{ item.label }}</span>
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
import { useAuthStore } from "@/stores/auth";
import { getRoleLabel } from "@/types/user";

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();

type NavItem = {
  label: string;
  to: string;
  icon?: string;
};

const functionItems: NavItem[] = [
  { label: "上传", to: "/upload", icon: "upload" },
  { label: "任务", to: "/acquisition", icon: "clipboard-check" },
  { label: "采集", to: "/sessions", icon: "camera" },
  { label: "处理", to: "/processing", icon: "workflow" },
  { label: "标注", to: "/annotation", icon: "tags" },
  { label: "质检", to: "/qc", icon: "shield" },
  { label: "导出", to: "/export", icon: "download" },
  { label: "工具", to: "/tools/session-organizer", icon: "wrench" },
];

const managementItems: NavItem[] = [
  { label: "用户", to: "/management/users", icon: "users" },
  { label: "设备", to: "/management/devices", icon: "server" },
  { label: "流程", to: "/management/workflows", icon: "git-branch" },
  { label: "存储", to: "/management/storages", icon: "database" },
  { label: "字典", to: "/management/dictionaries", icon: "book" },
  { label: "Profile", to: "/management/profiles", icon: "layers" },
];

const mobileItems = computed<NavItem[]>(() => {
  const items: NavItem[] = [{ label: "首页", to: "/home" }, ...functionItems];
  if (authStore.isAdmin.value) {
    items.push({ label: "用户", to: "/management/users", icon: "users" });
  }
  return items;
});

const isHomeRoute = computed(() => route.path.startsWith("/home"));
const isManagementRoute = computed(() => route.path.startsWith("/management"));
const isFunctionRoute = computed(() => !isHomeRoute.value && !isManagementRoute.value);

const showSecondarySidebar = computed(() => {
  if (isHomeRoute.value) {
    return false;
  }
  if (isManagementRoute.value && !authStore.isAdmin.value) {
    return false;
  }
  return true;
});

const secondaryItems = computed(() => (isManagementRoute.value ? managementItems : functionItems));

const displayName = computed(() => authStore.user.value?.displayName || authStore.user.value?.username || "未登录");
const roleLabel = computed(() => getRoleLabel(authStore.user.value?.roleCode || "VIEWER"));

const accountMenuRef = ref<HTMLElement | null>(null);
const accountMenuOpen = ref(false);

function setSection(target: "function" | "management") {
  if (target === "function") {
    if (!isFunctionRoute.value) {
      router.push("/acquisition");
    }
    return;
  }

  if (!authStore.isAdmin.value) {
    return;
  }

  if (!isManagementRoute.value) {
    router.push("/management/users");
  }
}

async function handleLogout() {
  accountMenuOpen.value = false;
  await authStore.logout();
  router.replace("/login");
}

function moduleToneClass(path: string) {
  if (path.startsWith("/upload")) return "app-tone-upload";
  if (path.startsWith("/acquisition")) return "app-tone-task";
  if (path.startsWith("/sessions")) return "app-tone-session";
  if (path.startsWith("/processing")) return "app-tone-process";
  if (path.startsWith("/annotation")) return "app-tone-annotation";
  if (path.startsWith("/qc")) return "app-tone-qc";
  if (path.startsWith("/export")) return "app-tone-export";
  return "app-tone-brand";
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
