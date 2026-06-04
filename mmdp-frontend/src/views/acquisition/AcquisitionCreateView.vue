<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 采集" title="创建采集任务" description="创建任务并绑定采集 Profile，确定数据规则集。">
      <template #actions>
        <BaseButton to="/acquisition">返回列表</BaseButton>
      </template>
    </PageHeader>

    <PageCard title="任务信息">
      <form class="grid gap-4 md:grid-cols-2" @submit.prevent="handleSubmit">
        <!-- 任务名称 -->
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">任务名称 <span class="text-rose-500">*</span></span>
          <input v-model="form.taskName" required class="app-input app-input-compact" placeholder="例如：步行采集-张三-20260602" />
        </label>

        <!-- 被试名字 -->
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">被试名字 <span class="text-rose-500">*</span></span>
          <input v-model="form.subjectName" required class="app-input app-input-compact" placeholder="例如：张三" />
          <span class="mt-0.5 text-xs text-slate-400">新被试自动创建，已有被试自动关联</span>
        </label>

        <!-- 动作名称 -->
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">采集动作 <span class="text-rose-500">*</span></span>
          <input v-model="form.actionName" required class="app-input app-input-compact" placeholder="例如：步行 / 跑步 / 跳跃" />
        </label>

        <!-- 采集日期 -->
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">采集日期 <span class="text-rose-500">*</span></span>
          <input v-model="form.collectDate" type="date" required class="app-input app-input-compact" />
        </label>

        <!-- 采集 Profile (关键字段) -->
        <label class="block md:col-span-2">
          <span class="mb-1 block text-sm text-slate-600">采集 Profile <span class="text-rose-500">*</span></span>
          <select v-model="profileIdValue" required class="app-input app-input-compact" @change="onProfileChange">
            <option :value="''" disabled>选择 Profile（决定设备、模态、规则）</option>
            <option v-for="profile in profiles" :key="profile.id" :value="String(profile.id)">
              {{ profile.profileName }} ({{ profile.profileCode }})
            </option>
          </select>
        </label>

        <!-- Profile 自动带出的信息（只读） -->
        <label class="block">
          <span class="mb-1 block text-sm text-slate-500">设备组合</span>
          <input :value="selectedProfile?.deviceGroupCode || '—'" readonly class="app-input app-input-compact bg-slate-50 text-slate-400" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-500">模态组合</span>
          <input :value="selectedProfile?.modalityGroupCode || '—'" readonly class="app-input app-input-compact bg-slate-50 text-slate-400" />
        </label>

        <!-- 采集人员 -->
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">采集人员</span>
          <input v-model="form.operatorName" class="app-input app-input-compact" placeholder="操作人员姓名" />
        </label>

        <!-- 采集地点 -->
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">采集地点</span>
          <input v-model="form.captureLocation" class="app-input app-input-compact" placeholder="例如：实验室A" />
        </label>

        <!-- 备注 -->
        <label class="block md:col-span-2">
          <span class="mb-1 block text-sm text-slate-600">备注</span>
          <input v-model="form.remark" class="app-input app-input-compact" placeholder="可选" />
        </label>

        <div class="md:col-span-2 flex items-center gap-2">
          <BaseButton variant="primary" type="submit" :disabled="submitting || loadingProfiles">
            {{ submitting ? "创建中..." : "创建任务" }}
          </BaseButton>
          <span v-if="errorMessage" class="text-sm text-rose-500">{{ errorMessage }}</span>
        </div>
      </form>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { fetchCollectionProfiles, type CollectionProfileResponse } from "@/api/profiles";
import { createTask } from "@/api/tasks";
import BaseButton from "@/components/BaseButton.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import type { CreateTaskRequest } from "@/types/task";

const router = useRouter();
const submitting = ref(false);
const loadingProfiles = ref(false);
const errorMessage = ref("");
const profiles = ref<CollectionProfileResponse[]>([]);
const profileIdValue = ref("");

const form = reactive<CreateTaskRequest>({
  taskName: "",
  subjectCode: "",
  subjectName: "",
  actionName: "",
  profileId: null,
  deviceType: "",
  modality: "",
  collectDate: "",
  scene: "",
  operatorName: "",
  captureLocation: "",
  remark: "",
});

const selectedProfile = computed(
  () => profiles.value.find((p) => String(p.id) === profileIdValue.value) ?? null,
);

function onProfileChange() {
  const p = selectedProfile.value;
  form.deviceType = p?.deviceGroupCode || "";
  form.modality = p?.modalityGroupCode || "";
}

async function loadProfiles() {
  loadingProfiles.value = true;
  try {
    profiles.value = await fetchCollectionProfiles();
    if (profiles.value.length > 0) {
      profileIdValue.value = String(profiles.value[0].id);
      onProfileChange();
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载 Profile 失败";
  } finally {
    loadingProfiles.value = false;
  }
}

async function handleSubmit() {
  if (!selectedProfile.value) {
    errorMessage.value = "请选择采集 Profile";
    return;
  }
  submitting.value = true;
  errorMessage.value = "";
  try {
    form.profileId = selectedProfile.value.id;
    const task = await createTask(form);
    await router.push(`/acquisition/${task.id}`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "创建任务失败";
  } finally {
    submitting.value = false;
  }
}

onMounted(loadProfiles);
</script>
