<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>Profile 管理</h1>
        <p>管理采集 Profile 配置 · 定义 Source 规则、处理链路与回放策略</p>
      </div>
      <button class="light2-btn light2-btn-primary" @click="openCreateDialog">+ 新建 Profile</button>
    </div>

    <!-- filters -->
    <div class="light2-filters">
      <input
        v-model="keyword"
        type="text"
        placeholder="搜索 profileCode / profileName..."
        class="light2-input"
        @keyup.enter="applySearch"
      />
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="applySearch">搜索</button>
      <button class="light2-btn light2-btn-sec light2-btn-sm" @click="resetSearch">重置</button>
    </div>

    <!-- table -->
    <div class="light2-tbl overflow-x-auto">
      <table class="min-w-[960px]">
        <thead>
          <tr>
            <th style="width:140px">Profile Code</th>
            <th>名称</th>
            <th style="width:110px">任务类型</th>
            <th style="width:80px">Source 数</th>
            <th style="width:60px">版本</th>
            <th style="width:72px">状态</th>
            <th style="width:152px">更新时间</th>
            <th style="width:112px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="loading">
            <td colspan="8" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">正在加载 Profile...</td>
          </tr>
          <tr v-else-if="!displayedList.length">
            <td colspan="8" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">
              {{ keyword ? '当前筛选条件下没有匹配结果' : '暂无 Profile 数据' }}
            </td>
          </tr>
          <tr v-for="profile in displayedList" :key="profile.id">
            <td><span class="light2-code">{{ profile.profileCode }}</span></td>
            <td>
              <span class="light2-tname">{{ profile.profileName }}</span>
              <div class="light2-tsub" v-if="profile.deviceGroupCode || profile.modalityGroupCode">
                {{ [profile.deviceGroupCode, profile.modalityGroupCode].filter(Boolean).join(' / ') }}
              </div>
            </td>
            <td>{{ profile.taskTypeCode }}</td>
            <td style="text-align:center">
              <span class="light2-badge light2-badge-neutral">{{ profile.sources?.length ?? 0 }}</span>
            </td>
            <td style="text-align:center">{{ profile.version }}</td>
            <td>
              <span class="light2-badge" :class="profile.enabled ? 'light2-badge-ok' : 'light2-badge-err'">
                <span class="light2-bdot" :style="{ background: profile.enabled ? '#0d9444' : '#d92d20' }" />
                {{ profile.enabled ? '已启用' : '已禁用' }}
              </span>
            </td>
            <td class="light2-code">{{ formatDateTime(profile) }}</td>
            <td>
              <div class="light2-actions">
                <button class="light2-btn light2-btn-sec light2-btn-sm" @click="openEditDialog(profile)">编辑</button>
                <button
                  v-if="profile.enabled"
                  class="light2-btn light2-btn-sec light2-btn-sm"
                  style="color:#d92d20;border-color:#fca5a5"
                  @click="handleDisable(profile)"
                >禁用</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- pagination -->
    <div v-if="pageState.total > 0" class="light2-pg">
      <span>
        第 {{ pageState.page }} 页，共 {{ Math.max(1, totalPages) }} 页 &middot; 总计 {{ filteredList.length }} 条
      </span>
      <div class="light2-pg-btns">
        <button :disabled="pageState.page <= 1" @click="updatePage(pageState.page - 1)">&larr; 上一页</button>
        <button :disabled="pageState.page >= totalPages" @click="updatePage(pageState.page + 1)">下一页 &rarr;</button>
      </div>
    </div>

    <!-- Create / Edit Dialog -->
    <AppDialog
      :open="dialogOpen"
      :title="editingProfile ? '编辑 Profile' : '新建 Profile'"
      :description="editingProfile ? '修改 Profile 基本信息和 Source 配置' : '填写 Profile 基本信息，配置数据源规则'"
      size="lg"
      :loading="submitting"
      @close="closeDialog"
      @confirm="submitDialog"
    >
      <div class="dialog-form">
        <!-- 基本信息 -->
        <fieldset class="form-section">
          <legend class="form-section-title">基本信息</legend>
          <div class="form-grid">
            <label class="form-field">
              <span class="form-label">Profile Code <span class="text-rose-500">*</span></span>
              <input v-model="form.profileCode" class="app-input app-input-compact" placeholder="如 ZED_STEREO_IMU_V1" required />
            </label>
            <label class="form-field">
              <span class="form-label">Profile Name <span class="text-rose-500">*</span></span>
              <input v-model="form.profileName" class="app-input app-input-compact" placeholder="如 ZED 双目 + IMU 采集" required />
            </label>
            <label class="form-field">
              <span class="form-label">任务类型 <span class="text-rose-500">*</span></span>
              <input
                v-model="form.taskTypeCode"
                class="app-input app-input-compact"
                list="task-type-list"
                placeholder="如 HUMAN_DEMO"
                required
              />
              <datalist id="task-type-list">
                <option v-for="opt in TASK_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
              </datalist>
            </label>
            <label class="form-field">
              <span class="form-label">版本</span>
              <input v-model="form.version" class="app-input app-input-compact" placeholder="v1" />
            </label>
            <label class="form-field">
              <span class="form-label">设备组编码</span>
              <input v-model="form.deviceGroupCode" class="app-input app-input-compact" placeholder="如 ZED_IMU" />
            </label>
            <label class="form-field">
              <span class="form-label">模态组编码</span>
              <input v-model="form.modalityGroupCode" class="app-input app-input-compact" placeholder="如 ZED_IMU_RAW" />
            </label>
          </div>
          <label class="form-field" style="margin-top:12px">
            <span class="form-label">备注</span>
            <input v-model="form.remark" class="app-input app-input-compact" placeholder="Profile 用途说明" />
          </label>
        </fieldset>

        <!-- 规则编码 -->
        <fieldset class="form-section">
          <legend class="form-section-title">规则编码</legend>
          <div class="form-grid">
            <label class="form-field">
              <span class="form-label">Package Rule</span>
              <input v-model="form.packageRuleCode" class="app-input app-input-compact" list="rule-list" placeholder="SESSION_ZIP_V1" />
            </label>
            <label class="form-field">
              <span class="form-label">Parser Rule</span>
              <input v-model="form.parserRuleCode" class="app-input app-input-compact" list="rule-list" placeholder="SESSION_JSONL_VIDEO_IMU_V1" />
            </label>
            <label class="form-field">
              <span class="form-label">Archive Rule</span>
              <input v-model="form.archiveRuleCode" class="app-input app-input-compact" list="rule-list" placeholder="SESSION_ARCHIVE_V1" />
            </label>
            <label class="form-field">
              <span class="form-label">Playback Rule</span>
              <input v-model="form.playbackRuleCode" class="app-input app-input-compact" list="rule-list" placeholder="MULTI_VIDEO_IMU_V1" />
            </label>
          </div>
          <datalist id="rule-list">
            <option v-for="opt in RULE_CODE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </datalist>
        </fieldset>

        <!-- Sources 子表单 -->
        <fieldset class="form-section">
          <legend class="form-section-title">
            Source 配置
            <button type="button" class="light2-btn light2-btn-sec light2-btn-sm" style="margin-left:12px" @click="addSourceRow">+ 添加 Source</button>
          </legend>
          <div v-if="!form.sources.length" class="form-hint">尚未配置 Source。点击 "添加 Source" 开始配置数据源。</div>
          <div v-else class="source-table-wrapper">
            <table class="source-table">
              <thead>
                <tr>
                  <th>sourceKey *</th>
                  <th>名称 *</th>
                  <th>类型 *</th>
                  <th>设备角色</th>
                  <th>必需</th>
                  <th style="width:44px"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(src, idx) in form.sources" :key="idx">
                  <td><input v-model="src.sourceKey" class="source-input" placeholder="zed" /></td>
                  <td><input v-model="src.sourceName" class="source-input" placeholder="ZED Raw" /></td>
                  <td>
                    <input v-model="src.sourceType" class="source-input" list="source-type-list" placeholder="video" />
                  </td>
                  <td>
                    <input v-model="src.deviceRoleCode" class="source-input" list="device-role-list" placeholder="ZED" />
                  </td>
                  <td style="text-align:center">
                    <input type="checkbox" v-model="src.requiredFlag" />
                  </td>
                  <td>
                    <button type="button" class="source-remove-btn" @click="removeSourceRow(idx)" title="移除此 Source">&times;</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <datalist id="source-type-list">
            <option v-for="opt in SOURCE_TYPE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </datalist>
          <datalist id="device-role-list">
            <option v-for="opt in DEVICE_ROLE_OPTIONS" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </datalist>
        </fieldset>

        <!-- 错误提示 -->
        <p v-if="dialogError" class="dialog-error">{{ dialogError }}</p>
      </div>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watchEffect } from "vue";
import AppDialog from "@/components/AppDialog.vue";
import {
  fetchAllProfiles,
  createProfile,
  updateProfile,
  disableProfile,
} from "@/api/profiles";
import type {
  CollectionProfileResponse,
  CreateSourceItem,
  UpdateProfileRequest,
} from "@/types/profile";
import { RULE_CODE_OPTIONS, TASK_TYPE_OPTIONS, SOURCE_TYPE_OPTIONS, DEVICE_ROLE_OPTIONS } from "@/types/profile";

// --- 列表状态 ---
const profiles = ref<CollectionProfileResponse[]>([]);
const loading = ref(true);
const keyword = ref("");
const pageState = reactive({ page: 1, pageSize: 10 });

const filteredList = computed(() => {
  if (!keyword.value.trim()) return profiles.value;
  const kw = keyword.value.trim().toLowerCase();
  return profiles.value.filter(
    (p) =>
      p.profileCode.toLowerCase().includes(kw) ||
      p.profileName.toLowerCase().includes(kw)
  );
});

const totalPages = computed(() => Math.max(1, Math.ceil(filteredList.value.length / pageState.pageSize)));

const displayedList = computed(() => {
  const start = (pageState.page - 1) * pageState.pageSize;
  return filteredList.value.slice(start, start + pageState.pageSize);
});

watchEffect(() => {
  if (pageState.page > totalPages.value) {
    pageState.page = totalPages.value;
  }
});

function applySearch() {
  pageState.page = 1;
}

function resetSearch() {
  keyword.value = "";
  pageState.page = 1;
}

function updatePage(p: number) {
  pageState.page = p;
}

function formatDateTime(profile: CollectionProfileResponse) {
  // updatedAt 通过后端返回的 remark 附加？无法直接获取，显示占位
  // CollectionProfileResponse 目前不含 updatedAt — 使用 id 排序
  return profile.profileCode;
}

// --- 弹窗状态 ---
const dialogOpen = ref(false);
const submitting = ref(false);
const dialogError = ref("");
const editingProfile = ref<CollectionProfileResponse | null>(null);

interface SourceRow {
  sourceKey: string;
  sourceName: string;
  sourceType: string;
  deviceRoleCode?: string;
  requiredFlag?: boolean;
}

const emptyForm = () => ({
  profileCode: "",
  profileName: "",
  taskTypeCode: "",
  modalityGroupCode: "",
  deviceGroupCode: "",
  packageRuleCode: "",
  parserRuleCode: "",
  archiveRuleCode: "",
  playbackRuleCode: "",
  version: "",
  remark: "",
  sources: [] as SourceRow[],
});

const form = reactive(emptyForm());

function openCreateDialog() {
  editingProfile.value = null;
  dialogError.value = "";
  Object.assign(form, emptyForm());
  dialogOpen.value = true;
}

function openEditDialog(profile: CollectionProfileResponse) {
  editingProfile.value = profile;
  dialogError.value = "";
  form.profileCode = profile.profileCode;
  form.profileName = profile.profileName;
  form.taskTypeCode = profile.taskTypeCode;
  form.modalityGroupCode = profile.modalityGroupCode || "";
  form.deviceGroupCode = profile.deviceGroupCode || "";
  form.packageRuleCode = profile.packageRuleCode || "";
  form.parserRuleCode = profile.parserRuleCode || "";
  form.archiveRuleCode = profile.archiveRuleCode || "";
  form.playbackRuleCode = profile.playbackRuleCode || "";
  form.version = profile.version || "";
  form.remark = profile.remark || "";
  form.sources = profile.sources.map((s) => ({
    sourceKey: s.sourceKey,
    sourceName: s.sourceName,
    sourceType: s.sourceType,
    deviceRoleCode: s.deviceRoleCode ?? undefined,
    requiredFlag: s.required,
  }));
  dialogOpen.value = true;
}

function closeDialog() {
  dialogOpen.value = false;
  dialogError.value = "";
}

function addSourceRow() {
  form.sources.push({
    sourceKey: "",
    sourceName: "",
    sourceType: "",
    deviceRoleCode: "",
    requiredFlag: true,
  });
}

function removeSourceRow(idx: number) {
  form.sources.splice(idx, 1);
}

async function submitDialog() {
  // 前端校验
  if (!form.profileCode.trim()) {
    dialogError.value = "Profile Code 不能为空";
    return;
  }
  if (!form.profileName.trim()) {
    dialogError.value = "Profile Name 不能为空";
    return;
  }
  if (!form.taskTypeCode.trim()) {
    dialogError.value = "任务类型不能为空";
    return;
  }
  // 校验 sources 必填字段
  for (let i = 0; i < form.sources.length; i++) {
    const s = form.sources[i];
    if (!s.sourceKey.trim()) {
      dialogError.value = `Source #${i + 1} 的 sourceKey 不能为空`;
      return;
    }
    if (!s.sourceName.trim()) {
      dialogError.value = `Source #${i + 1} 的名称不能为空`;
      return;
    }
    if (!s.sourceType.trim()) {
      dialogError.value = `Source #${i + 1} 的类型不能为空`;
      return;
    }
  }

  submitting.value = true;
  dialogError.value = "";
  try {
    if (editingProfile.value) {
      // 编辑模式
      const payload: UpdateProfileRequest = {
        profileCode: form.profileCode.trim(),
        profileName: form.profileName.trim(),
        taskTypeCode: form.taskTypeCode.trim(),
        modalityGroupCode: form.modalityGroupCode.trim() || undefined,
        deviceGroupCode: form.deviceGroupCode.trim() || undefined,
        packageRuleCode: form.packageRuleCode.trim() || undefined,
        parserRuleCode: form.parserRuleCode.trim() || undefined,
        archiveRuleCode: form.archiveRuleCode.trim() || undefined,
        playbackRuleCode: form.playbackRuleCode.trim() || undefined,
        version: form.version.trim() || undefined,
        remark: form.remark.trim() || undefined,
      };
      await updateProfile(editingProfile.value.id, payload);
      // TODO: 编辑模式下 sources 变更需要单独调 API
    } else {
      // 创建模式
      const sources: CreateSourceItem[] = form.sources.map((s) => ({
        sourceKey: s.sourceKey.trim(),
        sourceName: s.sourceName.trim(),
        sourceType: s.sourceType.trim(),
        deviceRoleCode: s.deviceRoleCode?.trim() || undefined,
        requiredFlag: s.requiredFlag,
      }));
      await createProfile({
        profileCode: form.profileCode.trim(),
        profileName: form.profileName.trim(),
        taskTypeCode: form.taskTypeCode.trim(),
        modalityGroupCode: form.modalityGroupCode.trim() || undefined,
        deviceGroupCode: form.deviceGroupCode.trim() || undefined,
        packageRuleCode: form.packageRuleCode.trim() || undefined,
        parserRuleCode: form.parserRuleCode.trim() || undefined,
        archiveRuleCode: form.archiveRuleCode.trim() || undefined,
        playbackRuleCode: form.playbackRuleCode.trim() || undefined,
        version: form.version.trim() || undefined,
        remark: form.remark.trim() || undefined,
        sources: sources.length > 0 ? sources : undefined,
      });
    }
    closeDialog();
    await loadProfiles();
  } catch (e: any) {
    dialogError.value = e?.message || "操作失败，请重试";
  } finally {
    submitting.value = false;
  }
}

async function handleDisable(profile: CollectionProfileResponse) {
  if (!confirm(`确认禁用 Profile "${profile.profileName}"？`)) return;
  try {
    await disableProfile(profile.id);
    await loadProfiles();
  } catch (e: any) {
    alert(e?.message || "禁用失败");
  }
}

// --- 数据加载 ---
async function loadProfiles() {
  loading.value = true;
  try {
    profiles.value = await fetchAllProfiles();
  } catch {
    profiles.value = [];
  }
  loading.value = false;
}

loadProfiles();
</script>

<style scoped>
.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.form-section {
  border: 1px solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  padding: 16px;
}

.form-section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 12px;
  display: flex;
  align-items: center;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.form-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.form-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
  padding: 12px 0;
  text-align: center;
}

.dialog-error {
  color: #d92d20;
  font-size: 13px;
  padding: 8px 12px;
  background: #fef2f2;
  border-radius: 6px;
  border: 1px solid #fecaca;
}

/* --- Source 内嵌表格 --- */
.source-table-wrapper {
  overflow-x: auto;
}

.source-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.source-table th {
  text-align: left;
  font-weight: 500;
  color: var(--color-text-tertiary);
  padding: 6px 8px;
  border-bottom: 1px solid var(--color-border-soft);
  white-space: nowrap;
}

.source-table td {
  padding: 4px 6px;
  border-bottom: 1px solid var(--color-border-soft);
}

.source-input {
  width: 100%;
  padding: 4px 8px;
  border: 1px solid var(--color-border-default);
  border-radius: 4px;
  font-size: 12px;
  font-family: inherit;
  color: var(--color-text-primary);
  background: #fff;
  outline: none;
  transition: border-color 0.15s;
}

.source-input:focus {
  border-color: var(--color-brand-500);
  box-shadow: 0 0 0 2px rgba(0, 83, 230, 0.1);
}

.source-remove-btn {
  background: none;
  border: none;
  font-size: 16px;
  color: #d92d20;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  line-height: 1;
}

.source-remove-btn:hover {
  background: #fef2f2;
}
</style>
