<template>
  <div class="space-y-6">
    <PageHeader
      eyebrow="Roadmap Module"
      :title="title"
      description="该模块入口已经纳入统一后台系统，当前阶段先保留规划位，后续可按业务节奏逐步接入实际功能。"
      :meta="headerMeta"
    />

    <PageCard eyebrow="Module Planning" :title="`${title} 模块`" description="保持规划模块与已上线模块相同的视觉层级，便于后续持续扩展。">
      <EmptyState
        :title="`${title} 正在规划中`"
        description="当前版本优先完善采集任务、文件上传和质检报告主链路。该模块的入口和结构已经预留，可在后续迭代中直接接入。"
        icon="规"
      />
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";
import EmptyState from "@/components/EmptyState.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";

const route = useRoute();

const titleMap: Record<string, string> = {
  "data-files": "数据文件",
  "qc-reports": "质检中心",
  datasets: "数据集管理",
  pipelines: "处理流水线",
  settings: "系统设置"
};

const title = computed(() => titleMap[String(route.params.module)] ?? "规划模块");

const headerMeta = computed(() => [
  { label: "当前状态", value: "规划中" },
  { label: "模块类型", value: "预留入口" }
]);
</script>
