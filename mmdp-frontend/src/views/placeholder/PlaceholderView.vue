<template>
  <div class="space-y-6">
    <PageHeader eyebrow="平台模块" :title="title" :description="description" :meta="headerMeta" />

    <PageCard eyebrow="模块规划" :title="`${title} 模块`" description="当前阶段保留导航语义和统一视觉，后续可在这里继续接入真实功能。">
      <EmptyState
        :title="`${title} 正在规划中`"
        :description="emptyDescription"
        icon="模"
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

const moduleMap: Record<string, { title: string; description: string; emptyDescription: string }> = {
  "data-assets": {
    title: "数据资产",
    description: "统一管理任务下的原始资产、外部路径资产和派生资产。",
    emptyDescription: "当前阶段请先在任务详情页完成资产接入与查看。后续可以在本模块接入跨任务资产检索与管理能力。"
  },
  processing: {
    title: "处理流程",
    description: "集中查看处理记录、作业执行结果和输出资产生成情况。",
    emptyDescription: "当前阶段处理流程以任务详情页内的自动处理与人工登记为主，后续可在这里扩展全局处理管理。"
  },
  "qc-reports": {
    title: "质量检查",
    description: "查看上传文件质量检查结果、基础告警和报告入口。",
    emptyDescription: "当前阶段质量检查结果仍通过任务详情页或质量检查报告页查看，后续可在本模块增加全局质量检查聚合。"
  },
  lineage: {
    title: "数据链路",
    description: "展示任务、资产、处理作业和派生资产之间的关系。",
    emptyDescription: "当前阶段链路视图已经内置在任务详情页，后续可在本模块扩展为跨任务链路总览。"
  },
  settings: {
    title: "系统配置",
    description: "维护平台环境、规则说明和后续扩展配置。",
    emptyDescription: "当前阶段系统配置以说明和预留入口为主，后续可接入真实配置页面。"
  }
};

const resolved = computed(
  () =>
    moduleMap[String(route.params.module)] ?? {
      title: "平台模块",
      description: "当前模块保持统一视觉与平台化导航语义。",
      emptyDescription: "该模块入口已预留，后续可以在不破坏整体结构的前提下接入实际功能。"
    }
);

const title = computed(() => resolved.value.title);
const description = computed(() => resolved.value.description);
const emptyDescription = computed(() => resolved.value.emptyDescription);

const headerMeta = computed(() => [
  { label: "当前状态", value: "规划中" },
  { label: "模块类型", value: "平台入口" }
]);
</script>
