<template>
  <div class="space-y-4">
    <PageHeader eyebrow="功能 / 上传" title="上传" description="在当前页面完成文件上传或外部路径登记。" />

    <div class="grid gap-4 xl:grid-cols-[minmax(0,1.2fr)_minmax(280px,0.8fr)]">
      <PageCard title="接入信息">
        <form class="grid gap-4 md:grid-cols-2" @submit.prevent="submitAsset">
          <label class="block">
            <span class="mb-1 block text-sm text-slate-600">存储源</span>
            <select v-model="form.storageSource" class="app-input app-input-compact">
              <option value="minio">MinIO</option>
              <option value="oss">阿里云 OSS</option>
              <option value="cos">腾讯云 COS</option>
              <option value="external">本地路径 / 外部路径登记</option>
            </select>
          </label>
          <label class="block">
            <span class="mb-1 block text-sm text-slate-600">上传文件类型</span>
            <input v-model="form.uploadType" class="app-input app-input-compact" placeholder="请输入文件类型" />
          </label>
          <label class="block">
            <span class="mb-1 block text-sm text-slate-600">taskId</span>
            <select v-model="form.taskId" class="app-input app-input-compact">
              <option value="">请选择任务</option>
              <option v-for="task in tasks" :key="task.id" :value="String(task.id)">{{ task.taskName }} (#{{ task.id }})</option>
            </select>
          </label>
          <label class="block">
            <span class="mb-1 block text-sm text-slate-600">sessionId</span>
            <input v-model="form.sessionId" class="app-input app-input-compact" placeholder="请输入会话编号" />
          </label>
          <label class="block">
            <span class="mb-1 block text-sm text-slate-600">所属设备</span>
            <input v-model="form.device" class="app-input app-input-compact" placeholder="请输入设备名称" />
          </label>
          <label class="block">
            <span class="mb-1 block text-sm text-slate-600">是否创建独立 session</span>
            <select v-model="form.createSession" class="app-input app-input-compact">
              <option value="no">否</option>
              <option value="yes">是</option>
            </select>
          </label>
          <label class="block">
            <span class="mb-1 block text-sm text-slate-600">assetType</span>
            <input v-model="form.assetType" class="app-input app-input-compact" placeholder="请输入资产类型" />
          </label>
          <label class="block">
            <span class="mb-1 block text-sm text-slate-600">modality</span>
            <input v-model="form.modality" class="app-input app-input-compact" placeholder="请输入模态" />
          </label>
          <label class="block">
            <span class="mb-1 block text-sm text-slate-600">fileFormat</span>
            <input v-model="form.fileFormat" class="app-input app-input-compact" placeholder="请输入格式" />
          </label>
          <div class="flex items-end gap-2">
            <BaseButton :variant="entryMode === 'upload' ? 'primary' : 'secondary'" size="md" type="button" @click="entryMode = 'upload'">普通上传</BaseButton>
            <BaseButton :variant="entryMode === 'external' ? 'primary' : 'secondary'" size="md" type="button" @click="entryMode = 'external'">路径登记</BaseButton>
          </div>
          <label v-if="entryMode === 'upload'" class="block md:col-span-2">
            <span class="mb-1 block text-sm text-slate-600">选择文件</span>
            <input type="file" class="app-input app-input-compact" @change="handleFileChange" />
          </label>
          <label v-else class="block md:col-span-2">
            <span class="mb-1 block text-sm text-slate-600">外部路径</span>
            <input v-model="form.externalPath" class="app-input app-input-compact" placeholder="请输入外部路径" />
          </label>
          <div class="md:col-span-2 flex items-center gap-2">
            <BaseButton variant="primary" size="md" type="submit" :disabled="submitting">提交</BaseButton>
            <span v-if="message" class="text-sm text-slate-500">{{ message }}</span>
          </div>
        </form>
      </PageCard>

      <PageCard title="操作说明" secondary>
        <ul class="space-y-2 text-sm leading-6 text-slate-600">
          <li>支持普通文件上传。</li>
          <li>支持外部路径登记。</li>
          <li>支持带 taskId 和 sessionId 进入当前页。</li>
        </ul>
      </PageCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { createExternalAsset } from "@/api/assets";
import { fetchAcquisitionList } from "@/api/platform";
import { uploadTaskFile } from "@/api/tasks";
import BaseButton from "@/components/BaseButton.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import type { TaskResponse } from "@/types/task";

const route = useRoute();
const tasks = ref<TaskResponse[]>([]);
const entryMode = ref<"upload" | "external">("upload");
const selectedFile = ref<File | null>(null);
const submitting = ref(false);
const message = ref("");
const form = reactive({
  storageSource: "minio",
  uploadType: "",
  taskId: String(route.query.taskId ?? ""),
  sessionId: String(route.query.sessionId ?? ""),
  device: "",
  createSession: "no",
  assetType: "OTHER",
  modality: "多模态",
  fileFormat: "",
  externalPath: ""
});

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedFile.value = input.files?.[0] ?? null;
}

const selectedName = computed(() => `${form.assetType || "ASSET"}-${form.sessionId || "UNBOUND"}`);

async function submitAsset() {
  submitting.value = true;
  message.value = "";
  try {
    const taskId = Number(form.taskId);
    if (!taskId) {
      throw new Error("请先选择 taskId");
    }
    if (entryMode.value === "upload") {
      if (!selectedFile.value) {
        throw new Error("请选择上传文件");
      }
      const result = await uploadTaskFile(taskId, selectedFile.value, form.assetType);
      message.value = result.summary;
    } else {
      await createExternalAsset(taskId, {
        assetType: form.assetType as never,
        displayName: selectedName.value,
        externalPath: form.externalPath,
        fileFormat: form.fileFormat,
        description: "",
        operatorRemark: ""
      });
      message.value = "登记成功";
    }
  } catch (error) {
    message.value = error instanceof Error ? error.message : "提交失败";
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  const page = await fetchAcquisitionList();
  tasks.value = page.records;
});
</script>
