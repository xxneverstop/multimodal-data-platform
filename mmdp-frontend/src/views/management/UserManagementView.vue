<template>
  <div class="space-y-4">
    <section class="app-shell-panel px-5 py-4">
      <div class="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div>
          <h1 class="text-xl font-semibold text-[var(--color-text-primary)]">用户管理</h1>
          <p class="mt-1 text-sm text-[var(--color-text-secondary)]">
            维护平台登录账号、角色、管理员标记与启停状态。
          </p>
        </div>
        <BaseButton variant="primary" @click="openCreateDialog">新建用户</BaseButton>
      </div>
    </section>

    <section class="app-shell-panel px-5 py-4">
      <div class="grid gap-3 md:grid-cols-3">
        <div class="rounded-[14px] border border-[var(--color-border-soft)] bg-[var(--color-surface-page)] px-4 py-3">
          <div class="text-xs font-semibold uppercase tracking-[0.08em] text-[var(--color-text-tertiary)]">用户总数</div>
          <div class="mt-2 text-lg font-semibold text-[var(--color-text-primary)]">{{ users.length }}</div>
        </div>
        <div class="rounded-[14px] border border-[var(--color-border-soft)] bg-[var(--color-surface-page)] px-4 py-3">
          <div class="text-xs font-semibold uppercase tracking-[0.08em] text-[var(--color-text-tertiary)]">启用中</div>
          <div class="mt-2 text-lg font-semibold text-[var(--color-text-primary)]">{{ activeCount }}</div>
        </div>
        <div class="rounded-[14px] border border-[var(--color-border-soft)] bg-[var(--color-surface-page)] px-4 py-3">
          <div class="text-xs font-semibold uppercase tracking-[0.08em] text-[var(--color-text-tertiary)]">管理员</div>
          <div class="mt-2 text-lg font-semibold text-[var(--color-text-primary)]">{{ adminCount }}</div>
        </div>
      </div>
    </section>

    <section class="app-shell-panel overflow-hidden">
      <div class="flex flex-col gap-3 border-b border-[var(--color-border-soft)] px-5 py-4 md:flex-row md:items-center md:justify-between">
        <div class="flex flex-1 items-center gap-3">
          <input
            v-model="keyword"
            class="app-input max-w-[320px]"
            placeholder="搜索账号、姓名、角色"
          />
          <select v-model="statusFilter" class="app-input max-w-[160px]">
            <option value="">全部状态</option>
            <option v-for="option in USER_STATUS_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </div>
        <BaseButton variant="secondary" @click="reloadUsers" :disabled="loading">
          {{ loading ? "刷新中..." : "刷新" }}
        </BaseButton>
      </div>

      <div v-if="errorMessage" class="border-b border-rose-100 bg-rose-50 px-5 py-3 text-sm text-rose-600">
        {{ errorMessage }}
      </div>

      <div class="overflow-x-auto">
        <table class="min-w-full border-collapse">
          <thead class="bg-[var(--color-surface-page)] text-left text-sm text-[var(--color-text-secondary)]">
            <tr>
              <th class="px-5 py-3 font-medium">账号</th>
              <th class="px-5 py-3 font-medium">姓名</th>
              <th class="px-5 py-3 font-medium">角色</th>
              <th class="px-5 py-3 font-medium">管理员</th>
              <th class="px-5 py-3 font-medium">状态</th>
              <th class="px-5 py-3 font-medium">最近登录</th>
              <th class="px-5 py-3 font-medium">备注</th>
              <th class="px-5 py-3 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!loading && filteredUsers.length === 0">
              <td colspan="8" class="px-5 py-10 text-center text-sm text-[var(--color-text-secondary)]">
                当前没有匹配的用户记录
              </td>
            </tr>
            <tr
              v-for="user in filteredUsers"
              :key="user.id"
              class="border-t border-[var(--color-border-soft)] text-sm text-[var(--color-text-primary)]"
            >
              <td class="px-5 py-4">
                <div class="font-medium">{{ user.username }}</div>
                <div class="mt-1 text-xs text-[var(--color-text-tertiary)]">{{ user.email || "-" }}</div>
              </td>
              <td class="px-5 py-4">{{ user.displayName }}</td>
              <td class="px-5 py-4">{{ getRoleLabel(user.roleCode) }}</td>
              <td class="px-5 py-4">
                <span
                  class="inline-flex rounded-full border px-2.5 py-1 text-xs font-medium"
                  :class="user.isAdmin ? 'border-sky-200 bg-sky-50 text-sky-700' : 'border-slate-200 bg-slate-50 text-slate-600'"
                >
                  {{ user.isAdmin ? "是" : "否" }}
                </span>
              </td>
              <td class="px-5 py-4">
                <span
                  class="inline-flex rounded-full border px-2.5 py-1 text-xs font-medium"
                  :class="user.status === 'ACTIVE' ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : 'border-amber-200 bg-amber-50 text-amber-700'"
                >
                  {{ getUserStatusLabel(user.status) }}
                </span>
              </td>
              <td class="px-5 py-4 text-[var(--color-text-secondary)]">{{ formatDateTime(user.lastLoginAt || undefined) }}</td>
              <td class="px-5 py-4 text-[var(--color-text-secondary)]">{{ user.remark || "-" }}</td>
              <td class="px-5 py-4">
                <div class="flex flex-wrap gap-2">
                  <BaseButton size="sm" variant="secondary" @click="openEditDialog(user)">编辑</BaseButton>
                  <BaseButton
                    size="sm"
                    :variant="user.status === 'ACTIVE' ? 'danger' : 'soft'"
                    @click="toggleUserStatus(user)"
                  >
                    {{ user.status === "ACTIVE" ? "停用" : "启用" }}
                  </BaseButton>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <AppDialog
      :open="dialogOpen"
      :title="editingUser ? '编辑用户' : '新建用户'"
      description="当前版本支持维护角色、管理员标记、启停状态及基础联系信息。"
      :loading="submitting"
      @close="closeDialog"
      @confirm="submitDialog"
    >
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">登录账号</span>
          <input v-model="form.username" class="app-input app-input-compact" :disabled="!!editingUser" />
        </label>

        <label v-if="!editingUser" class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">初始密码</span>
          <input v-model="form.password" class="app-input app-input-compact" type="password" />
        </label>

        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">展示姓名</span>
          <input v-model="form.displayName" class="app-input app-input-compact" />
        </label>

        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">角色</span>
          <select v-model="form.roleCode" class="app-input app-input-compact">
            <option v-for="option in USER_ROLE_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">状态</span>
          <select v-model="form.status" class="app-input app-input-compact">
            <option v-for="option in USER_STATUS_OPTIONS" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
        </label>

        <label class="flex items-center gap-2 pt-7 text-sm text-[var(--color-text-primary)]">
          <input v-model="form.isAdmin" type="checkbox" class="h-4 w-4 rounded border-slate-300" />
          <span>具备管理员权限</span>
        </label>

        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">手机号</span>
          <input v-model="form.phone" class="app-input app-input-compact" />
        </label>

        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">邮箱</span>
          <input v-model="form.email" class="app-input app-input-compact" />
        </label>
      </div>

      <label class="mt-4 block">
        <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">备注</span>
        <textarea v-model="form.remark" class="app-input min-h-[92px]" />
      </label>

      <p v-if="dialogErrorMessage" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">
        {{ dialogErrorMessage }}
      </p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import AppDialog from "@/components/AppDialog.vue";
import BaseButton from "@/components/BaseButton.vue";
import { createUser, fetchUsers, updateUser, updateUserStatus } from "@/api/users";
import { formatDateTime } from "@/utils/format";
import {
  getRoleLabel,
  getUserStatusLabel,
  USER_ROLE_OPTIONS,
  USER_STATUS_OPTIONS,
  type CreateUserRequest,
  type UpdateUserRequest,
  type UserResponse,
  type UserRoleCode,
  type UserStatus,
} from "@/types/user";

const users = ref<UserResponse[]>([]);
const loading = ref(false);
const submitting = ref(false);
const errorMessage = ref("");
const dialogErrorMessage = ref("");
const keyword = ref("");
const statusFilter = ref("");
const dialogOpen = ref(false);
const editingUser = ref<UserResponse | null>(null);

const form = reactive({
  username: "",
  password: "",
  displayName: "",
  roleCode: "COLLECTOR" as UserRoleCode,
  isAdmin: false,
  status: "ACTIVE" as UserStatus,
  phone: "",
  email: "",
  remark: "",
});

const filteredUsers = computed(() => {
  const keywordValue = keyword.value.trim().toLowerCase();
  return users.value.filter((user) => {
    const matchesStatus = !statusFilter.value || user.status === statusFilter.value;
    if (!matchesStatus) {
      return false;
    }
    if (!keywordValue) {
      return true;
    }
    return [
      user.username,
      user.displayName,
      getRoleLabel(user.roleCode),
      user.email || "",
    ].some((value) => value.toLowerCase().includes(keywordValue));
  });
});

const activeCount = computed(() => users.value.filter((item) => item.status === "ACTIVE").length);
const adminCount = computed(() => users.value.filter((item) => item.isAdmin).length);

onMounted(() => {
  reloadUsers();
});

async function reloadUsers() {
  loading.value = true;
  errorMessage.value = "";
  try {
    users.value = await fetchUsers();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载用户失败";
  } finally {
    loading.value = false;
  }
}

function openCreateDialog() {
  editingUser.value = null;
  resetForm();
  dialogErrorMessage.value = "";
  dialogOpen.value = true;
}

function openEditDialog(user: UserResponse) {
  editingUser.value = user;
  form.username = user.username;
  form.password = "";
  form.displayName = user.displayName;
  form.roleCode = user.roleCode;
  form.isAdmin = user.isAdmin;
  form.status = user.status;
  form.phone = user.phone || "";
  form.email = user.email || "";
  form.remark = user.remark || "";
  dialogErrorMessage.value = "";
  dialogOpen.value = true;
}

function closeDialog() {
  dialogOpen.value = false;
  dialogErrorMessage.value = "";
}

async function submitDialog() {
  dialogErrorMessage.value = "";
  if (!form.username.trim() && !editingUser.value) {
    dialogErrorMessage.value = "请输入登录账号";
    return;
  }
  if (!editingUser.value && form.password.trim().length < 6) {
    dialogErrorMessage.value = "初始密码至少需要 6 位";
    return;
  }
  if (!form.displayName.trim()) {
    dialogErrorMessage.value = "请输入展示姓名";
    return;
  }

  submitting.value = true;
  try {
    if (editingUser.value) {
      const payload: UpdateUserRequest = {
        displayName: form.displayName.trim(),
        roleCode: form.roleCode,
        isAdmin: form.isAdmin,
        status: form.status,
        phone: normalizeOptional(form.phone),
        email: normalizeOptional(form.email),
        remark: normalizeOptional(form.remark),
      };
      await updateUser(editingUser.value.id, payload);
    } else {
      const payload: CreateUserRequest = {
        username: form.username.trim(),
        password: form.password,
        displayName: form.displayName.trim(),
        roleCode: form.roleCode,
        isAdmin: form.isAdmin,
        status: form.status,
        phone: normalizeOptional(form.phone),
        email: normalizeOptional(form.email),
        remark: normalizeOptional(form.remark),
      };
      await createUser(payload);
    }
    dialogOpen.value = false;
    await reloadUsers();
  } catch (error) {
    dialogErrorMessage.value = error instanceof Error ? error.message : "保存用户失败";
  } finally {
    submitting.value = false;
  }
}

async function toggleUserStatus(user: UserResponse) {
  const nextStatus: UserStatus = user.status === "ACTIVE" ? "DISABLED" : "ACTIVE";
  try {
    await updateUserStatus(user.id, { status: nextStatus });
    await reloadUsers();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "更新用户状态失败";
  }
}

function resetForm() {
  form.username = "";
  form.password = "";
  form.displayName = "";
  form.roleCode = "COLLECTOR";
  form.isAdmin = false;
  form.status = "ACTIVE";
  form.phone = "";
  form.email = "";
  form.remark = "";
}

function normalizeOptional(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed ? trimmed : undefined;
}
</script>
