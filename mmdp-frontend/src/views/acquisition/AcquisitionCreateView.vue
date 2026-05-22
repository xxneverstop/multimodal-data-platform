<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 采集" title="新建采集任务" description="填写基础信息后创建任务。">
      <template #actions>
        <BaseButton to="/acquisition">返回列表</BaseButton>
      </template>
    </PageHeader>

    <PageCard title="任务信息">
      <form class="grid gap-4 md:grid-cols-2" @submit.prevent="handleSubmit">
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">任务名称</span>
          <input v-model="form.taskName" required class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">subjectCode</span>
          <input v-model="form.subjectCode" required class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">actionName</span>
          <input v-model="form.actionName" required class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">设备类型</span>
          <input v-model="form.deviceType" required class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">计划模态</span>
          <input v-model="form.modality" required class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm text-slate-600">采集日期</span>
          <input v-model="form.collectDate" type="date" required class="app-input app-input-compact" />
        </label>
        <div class="md:col-span-2 flex items-center gap-2">
          <BaseButton variant="primary" type="submit" :disabled="submitting">{{ submitting ? "创建中..." : "创建任务" }}</BaseButton>
          <span v-if="errorMessage" class="text-sm text-slate-500">{{ errorMessage }}</span>
        </div>
      </form>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { createTask } from "@/api/tasks";
import BaseButton from "@/components/BaseButton.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import type { CreateTaskRequest } from "@/types/task";

const router = useRouter();
const submitting = ref(false);
const errorMessage = ref("");

const form = reactive<CreateTaskRequest>({
  taskName: "",
  subjectCode: "",
  actionName: "",
  deviceType: "camera",
  modality: "多模态",
  collectDate: "",
  scene: "",
  operatorName: "",
  captureLocation: "",
  remark: ""
});

async function handleSubmit() {
  submitting.value = true;
  errorMessage.value = "";
  try {
    const task = await createTask(form);
    await router.push(`/acquisition/${task.id}`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "创建失败";
  } finally {
    submitting.value = false;
  }
}
</script>
