<template>
  <div class="space-y-5">
    <PageHeader
      eyebrow="Task Intake"
      title="新建采集任务"
      description="录入采集任务元数据。任务只描述采集本身，后续资产和处理流程在详情页继续补充。"
      :meta="headerMeta"
    >
      <template #actions>
        <BaseButton to="/tasks">返回任务列表</BaseButton>
      </template>
    </PageHeader>

    <div class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_300px]">
      <PageCard eyebrow="Primary Panel" title="任务信息" description="保持任务命名稳定，便于后续资产登记、处理流程和 QC 追踪。">
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
            <input v-model="form.actionName" required class="app-input" placeholder="例如：walking" />
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
            <input v-model="form.scene" class="app-input" placeholder="例如：RGB + 动捕服联合采集" />
          </label>
          <label class="block">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">采集人员</span>
            <input v-model="form.operatorName" class="app-input" placeholder="例如：Lab Operator" />
          </label>
          <label class="block md:col-span-2">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">采集地点</span>
            <input v-model="form.captureLocation" class="app-input" placeholder="例如：动捕棚 A 区 / NAS-01" />
          </label>
          <label class="block md:col-span-2">
            <span class="mb-1.5 block text-sm font-medium text-slate-700">备注</span>
            <textarea v-model="form.remark" rows="4" class="app-input resize-y" placeholder="补充采集场景、批次说明或其他备注"></textarea>
          </label>

          <div class="flex items-center gap-3 md:col-span-2">
            <BaseButton variant="primary" type="submit" :disabled="submitting">
              {{ submitting ? "正在创建任务..." : "创建采集任务" }}
            </BaseButton>
            <span v-if="errorMessage" class="text-xs text-rose-700">{{ errorMessage }}</span>
          </div>
        </form>
      </PageCard>

      <PageCard
        eyebrow="Secondary Panel"
        title="填写建议"
        description="本轮任务只记录采集上下文，不在创建时绑定固定 pipeline。"
        secondary
      >
        <ul class="space-y-3 text-xs leading-5 text-slate-500">
          <li>任务名称建议包含场景、动作或批次信息，便于后续资产和 job 追踪。</li>
          <li>动作名称尽量保持统一命名，例如 `walking`、`turn_left`、`yunshou`。</li>
          <li>创建设备与模态后，详情页可继续上传资产、登记外部路径并查看可用 pipeline。</li>
          <li>这一页不要求决定后续处理流程，真正的流程判断由任务下资产决定。</li>
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
