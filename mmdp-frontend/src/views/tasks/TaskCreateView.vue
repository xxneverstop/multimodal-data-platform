<template>
  <div class="space-y-5">
    <PageHeader
      eyebrow="任务建档"
      title="新建任务"
      description="录入采集任务基础元数据。任务本身只承载采集上下文，资产接入、处理记录、质量检查与数据链路在任务详情页继续完成。"
      :meta="headerMeta"
    >
      <template #actions>
        <BaseButton to="/tasks">返回任务列表</BaseButton>
      </template>
    </PageHeader>

    <div class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_320px]">
      <PageCard eyebrow="基础信息" title="任务信息" description="保持任务命名稳定，便于后续资产登记、处理记录和质量检查追踪。">
        <form class="grid gap-4 md:grid-cols-2" @submit.prevent="handleSubmit">
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">任务名称</span>
            <input v-model="form.taskName" required class="app-input" placeholder="例如：步态采集第 02 批" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">被试编号</span>
            <input v-model="form.subjectCode" required class="app-input" placeholder="例如：S-0021" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">动作名称</span>
            <input v-model="form.actionName" required class="app-input" placeholder="例如：行走" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">设备类型</span>
            <input v-model="form.deviceType" required class="app-input" placeholder="例如：IMU_CLOTH" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">数据模态</span>
            <input v-model="form.modality" required class="app-input" placeholder="例如：IMU" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">采集日期</span>
            <input v-model="form.collectDate" type="date" required class="app-input" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">场景</span>
            <input v-model="form.scene" class="app-input" placeholder="例如：RGB 与动捕联合采集" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">采集人员</span>
            <input v-model="form.operatorName" class="app-input" placeholder="例如：实验员 A" />
          </label>
          <label class="block md:col-span-2">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">采集地点</span>
            <input v-model="form.captureLocation" class="app-input" placeholder="例如：动捕棚一区 / 存储节点一" />
          </label>
          <label class="block md:col-span-2">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">备注</span>
            <textarea v-model="form.remark" rows="4" class="app-input resize-y" placeholder="补充采集场景、批次说明或其他备注"></textarea>
          </label>

          <div class="flex items-center gap-3 md:col-span-2">
            <BaseButton variant="primary" type="submit" :disabled="submitting">
              {{ submitting ? "正在创建任务..." : "创建任务" }}
            </BaseButton>
            <span v-if="errorMessage" class="text-xs text-rose-700">{{ errorMessage }}</span>
          </div>
        </form>
      </PageCard>

      <PageCard
        eyebrow="填写建议"
        title="建档提示"
        description="任务页面只记录采集上下文，不在这里决定处理流程。"
        secondary
      >
        <ul class="space-y-3 text-xs leading-5 text-slate-500">
          <li>任务名称建议包含动作、场景或批次信息，便于后续资产、处理记录与链路追踪。</li>
          <li>动作名称尽量保持统一命名，例如“行走”“左转”“云手”。</li>
          <li>创建完成后，建议直接进入任务详情页，继续完成平台上传、外部登记、处理记录和数据链路查看。</li>
          <li>当前阶段的主要操作页面是任务详情页，资产接入、质量检查和处理作业都在详情页完成。</li>
        </ul>
      </PageCard>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
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
  deviceType: "IMU_CLOTH",
  modality: "IMU",
  collectDate: "",
  scene: "",
  operatorName: "",
  captureLocation: "",
  remark: ""
});

const headerMeta = computed(() => [
  { label: "默认设备", value: form.deviceType },
  { label: "默认模态", value: form.modality }
]);

async function handleSubmit() {
  submitting.value = true;
  errorMessage.value = "";
  try {
    const task = await createTask(form);
    await router.push(`/tasks/${task.id}`);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "创建任务失败";
  } finally {
    submitting.value = false;
  }
}
</script>
