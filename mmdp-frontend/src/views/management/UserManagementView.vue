<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>用户管理</h1>
        <p>维护平台登录账号、角色、管理员标记与启停状态</p>
      </div>
      <button class="light2-btn light2-btn-primary" @click="openCreateDialog">+ 新建用户</button>
    </div>

    <!-- filters -->
    <div class="light2-filters">
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索账号 / 姓名 / 角色"
        class="light2-input"
        @keyup.enter="applySearch"
      />
      <div class="light2-sel">
        <select v-model="statusFilter" @change="applySearch">
          <option value="">全部状态</option>
          <option v-for="option in USER_STATUS_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </div>
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="applySearch">搜索</button>
      <button class="light2-btn light2-btn-sec light2-btn-sm" @click="resetSearch">重置</button>
    </div>

    <!-- table -->
    <div class="light2-tbl overflow-x-auto">
      <table class="min-w-[900px]">
        <thead>
          <tr>
            <th style="width:100px">账号</th>
            <th style="width:80px">姓名</th>
            <th style="width:80px">角色</th>
            <th style="width:60px">管理员</th>
            <th style="width:60px">状态</th>
            <th style="width:140px">最近登录</th>
            <th style="min-width:80px">备注</th>
            <th style="width:130px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">正在加载...</td>
          </tr>
          <tr v-else-if="!filteredUsers.length">
            <td colspan="8" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">
              {{ keyword || statusFilter ? '当前筛选条件下没有匹配结果' : '暂无用户数据' }}
            </td>
          </tr>
          <tr v-for="user in filteredUsers" :key="user.id" style="white-space:nowrap">
            <td>
              <span class="light2-tname">{{ user.username }}</span>
              <div class="light2-tsub" v-if="user.email">{{ user.email }}</div>
            </td>
            <td>{{ user.displayName }}</td>
            <td>{{ getRoleLabel(user.roleCode) }}</td>
            <td>
              <span class="light2-badge" :class="user.isAdmin ? 'light2-badge-info' : 'light2-badge-neutral'">
                <span class="light2-bdot" :style="{ background: user.isAdmin ? 'var(--color-brand-500)' : '#9298a3' }" />
                {{ user.isAdmin ? '是' : '否' }}
              </span>
            </td>
            <td>
              <span class="light2-badge" :class="user.status === 'ACTIVE' ? 'light2-badge-ok' : 'light2-badge-warn'">
                <span class="light2-bdot" :style="{ background: user.status === 'ACTIVE' ? '#0d7d3e' : '#b87a0a' }" />
                {{ getUserStatusLabel(user.status) }}
              </span>
            </td>
            <td>{{ formatDateTime(user.lastLoginAt || undefined) }}</td>
            <td>
              <div style="max-width:120px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap" :title="user.remark || ''">
                {{ user.remark || "-" }}
              </div>
            </td>
            <td>
              <div class="light2-actions" style="white-space:nowrap">
                <button class="light2-btn light2-btn-sec light2-btn-sm" @click="openEditDialog(user)">编辑</button>
                <button
                  class="light2-btn light2-btn-sec light2-btn-sm"
                  :style="user.status === 'ACTIVE' ? 'color:#c5222f;border-color:#fecdd3' : 'color:#0d7d3e;border-color:#a7f3d0'"
                  @click="toggleUserStatus(user)"
                >{{ user.status === 'ACTIVE' ? '停用' : '启用' }}</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- dialog -->
    <AppDialog
      :open="dialogOpen"
      :title="editingUser ? '编辑用户' : '新建用户'"
      description="维护角色、管理员标记、启停状态及基础联系信息。"
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
            <option v-for="option in USER_ROLE_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
          </select>
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-[var(--color-text-secondary)]">状态</span>
          <select v-model="form.status" class="app-input app-input-compact">
            <option v-for="option in USER_STATUS_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
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
import { computed, reactive, ref } from "vue";
import AppDialog from "@/components/AppDialog.vue";
import { createUser, fetchUsers, updateUser, updateUserStatus } from "@/api/users";
import { formatDateTime } from "@/utils/format";
import {
  getRoleLabel, getUserStatusLabel, USER_ROLE_OPTIONS, USER_STATUS_OPTIONS,
  type CreateUserRequest, type UpdateUserRequest, type UserResponse,
  type UserRoleCode, type UserStatus,
} from "@/types/user";

const users = ref<UserResponse[]>([]);
const loading = ref(true);
const submitting = ref(false);
const dialogErrorMessage = ref("");
const keyword = ref("");
const statusFilter = ref("");
const dialogOpen = ref(false);
const editingUser = ref<UserResponse | null>(null);

const form = reactive({
  username: "", password: "", displayName: "",
  roleCode: "COLLECTOR" as UserRoleCode, isAdmin: false, status: "ACTIVE" as UserStatus,
  phone: "", email: "", remark: "",
});

const filteredUsers = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  return users.value.filter((user) => {
    if (statusFilter.value && user.status !== statusFilter.value) return false;
    if (!kw) return true;
    return [user.username, user.displayName, getRoleLabel(user.roleCode), user.email || ""]
      .some((v) => v.toLowerCase().includes(kw));
  });
});

async function reloadUsers() {
  loading.value = true;
  try { users.value = await fetchUsers(); } catch { users.value = []; }
  loading.value = false;
}
reloadUsers();

function applySearch() {}
function resetSearch() { keyword.value = ""; statusFilter.value = ""; }

function openCreateDialog() { editingUser.value = null; resetForm(); dialogErrorMessage.value = ""; dialogOpen.value = true; }
function openEditDialog(user: UserResponse) {
  editingUser.value = user;
  form.username = user.username; form.password = ""; form.displayName = user.displayName;
  form.roleCode = user.roleCode; form.isAdmin = user.isAdmin; form.status = user.status;
  form.phone = user.phone || ""; form.email = user.email || ""; form.remark = user.remark || "";
  dialogErrorMessage.value = ""; dialogOpen.value = true;
}
function closeDialog() { dialogOpen.value = false; dialogErrorMessage.value = ""; }

async function submitDialog() {
  dialogErrorMessage.value = "";
  if (!form.username.trim() && !editingUser.value) { dialogErrorMessage.value = "请输入登录账号"; return; }
  if (!editingUser.value && form.password.trim().length < 6) { dialogErrorMessage.value = "初始密码至少需要 6 位"; return; }
  if (!form.displayName.trim()) { dialogErrorMessage.value = "请输入展示姓名"; return; }
  submitting.value = true;
  try {
    if (editingUser.value) {
      await updateUser(editingUser.value.id, {
        displayName: form.displayName.trim(), roleCode: form.roleCode, isAdmin: form.isAdmin,
        status: form.status, phone: n(form.phone), email: n(form.email), remark: n(form.remark),
      });
    } else {
      await createUser({
        username: form.username.trim(), password: form.password, displayName: form.displayName.trim(),
        roleCode: form.roleCode, isAdmin: form.isAdmin, status: form.status,
        phone: n(form.phone), email: n(form.email), remark: n(form.remark),
      });
    }
    dialogOpen.value = false;
    await reloadUsers();
  } catch (e) { dialogErrorMessage.value = e instanceof Error ? e.message : "保存失败"; }
  finally { submitting.value = false; }
}

async function toggleUserStatus(user: UserResponse) {
  try {
    await updateUserStatus(user.id, { status: user.status === "ACTIVE" ? "DISABLED" : "ACTIVE" });
    await reloadUsers();
  } catch { /* 忽略 */ }
}

function resetForm() {
  form.username = ""; form.password = ""; form.displayName = ""; form.roleCode = "COLLECTOR";
  form.isAdmin = false; form.status = "ACTIVE"; form.phone = ""; form.email = ""; form.remark = "";
}
function n(v: string): string | undefined { const t = v.trim(); return t || undefined; }
</script>
