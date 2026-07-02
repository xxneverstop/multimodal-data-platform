<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>数据标注</h1>
        <p>查看标注任务并跳转到外部标注系统</p>
      </div>
      <button class="light2-btn light2-btn-primary" @click="dialogOpen = true">+ 新建标注</button>
    </div>

    <hr class="light2-divider" />

    <!-- filters -->
    <div class="light2-filters">
      <input v-model="filters.taskId" type="text" placeholder="搜索 taskId..." class="light2-input" @keyup.enter="searchCount += 1" />
      <input v-model="filters.sessionId" type="text" placeholder="搜索 sessionId..." class="light2-input" @keyup.enter="searchCount += 1" />
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="searchCount += 1">搜索</button>
    </div>

    <!-- table -->
    <div class="light2-tbl">
      <table>
        <thead>
          <tr>
            <th>任务名称</th>
            <th>taskId</th>
            <th>sessionId</th>
            <th>关联资产</th>
            <th>状态</th>
            <th>标签</th>
            <th>更新时间</th>
            <th style="width:110px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in filteredTasks" :key="item.id">
            <td style="font-weight:600">{{ item.name }}</td>
            <td>{{ item.taskId }}</td>
            <td>{{ item.sessionId }}</td>
            <td>{{ item.assetName }}</td>
            <td><span class="light2-badge" :class="badgeClass(item.annotationStatus)"><span class="light2-bdot" :style="{background:badgeColor(item.annotationStatus)}" />{{ item.annotationStatus }}</span></td>
            <td>{{ item.annotationTag }}</td>
            <td>{{ formatDateTime(item.updatedAt) }}</td>
            <td><ExternalEntryButton :href="item.entryUrl" /></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- dialog -->
    <AppDialog :open="dialogOpen" title="新建标注任务" description="先保留轻量字段。" @close="dialogOpen = false" @confirm="submitMock('标注任务已创建')">
      <div class="grid gap-4 md:grid-cols-2">
        <label class="block">
          <span class="mb-1 block text-sm" style="color:var(--color-text-secondary)">任务名称</span>
          <input v-model="dialogForm.name" class="app-input app-input-compact" />
        </label>
        <label class="block">
          <span class="mb-1 block text-sm" style="color:var(--color-text-secondary)">标签</span>
          <select v-model="dialogForm.tag" class="app-input app-input-compact">
            <option value="有效">有效</option>
            <option value="无效">无效</option>
            <option value="异常动作">异常动作</option>
            <option value="时间漂移">时间漂移</option>
          </select>
        </label>
      </div>
      <p v-if="dialogMessage" class="mt-3 text-sm" style="color:var(--color-text-tertiary)">{{ dialogMessage }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { fetchAnnotationTasks } from "@/api/platform";
import AppDialog from "@/components/AppDialog.vue";
import ExternalEntryButton from "@/components/ExternalEntryButton.vue";
import type { AnnotationTaskRecord } from "@/types/platform";
import { formatDateTime } from "@/utils/format";

const tasks = ref<AnnotationTaskRecord[]>([]);
const dialogOpen = ref(false);
const dialogMessage = ref("");
const searchCount = ref(0);
const filters = reactive({ taskId: "", sessionId: "" });
const dialogForm = reactive({ name: "", tag: "有效" });
const filteredTasks = computed(() => tasks.value.filter((task) => (!filters.taskId || String(task.taskId).includes(filters.taskId.trim())) && (!filters.sessionId || task.sessionId.includes(filters.sessionId.trim()))));

const BADGE_MAP: Record<string, { cls: string; color: string }> = {
  进行中: { cls: "light2-badge-info", color: "var(--color-brand-500)" },
  已完成: { cls: "light2-badge-ok", color: "#0d7d3e" },
};
function badgeClass(s: string) { return BADGE_MAP[s]?.cls ?? "light2-badge-neutral"; }
function badgeColor(s: string) { return BADGE_MAP[s]?.color ?? "#9298a3"; }

function submitMock(message: string) {
  dialogMessage.value = message;
  window.setTimeout(() => { dialogOpen.value = false; dialogMessage.value = ""; }, 600);
}

onMounted(async () => {
  tasks.value = await fetchAnnotationTasks();
});
</script>
