<template>
  <section class="workspace-stage-timeline">
    <article
      v-for="(stage, index) in stages"
      :key="stage.label"
      class="workspace-stage-node"
      :class="[
        stage.status === 'current' ? 'workspace-stage-node-active' : '',
        stage.status === 'risk' ? 'workspace-stage-node-risk' : '',
        stage.tone ? `app-tone-${stage.tone}` : ''
      ]"
    >
      <div class="workspace-stage-marker">
        <BaseIcon :name="iconFor(stage.status)" size="sm" />
      </div>
      <div class="min-w-0">
        <div class="text-sm font-semibold text-[var(--color-text-primary)]">{{ stage.label }}</div>
        <div class="mt-1 text-xs text-[var(--color-text-tertiary)]">{{ stage.caption }}</div>
      </div>
      <div
        v-if="index < stages.length - 1"
        class="workspace-stage-connector"
        aria-hidden="true"
      ></div>
    </article>
  </section>
</template>

<script setup lang="ts">
import BaseIcon from "@/components/BaseIcon.vue";
import type { MetricTone } from "@/components/BusinessMetrics.vue";

export type WorkflowStageStatus = "done" | "current" | "waiting" | "risk";

defineProps<{
  stages: Array<{
    label: string;
    caption: string;
    status: WorkflowStageStatus;
    tone?: MetricTone;
  }>;
}>();

function iconFor(status: WorkflowStageStatus) {
  switch (status) {
    case "done":
      return "clipboard-check";
    case "current":
      return "workflow";
    case "risk":
      return "shield-alert";
    default:
      return "clock";
  }
}
</script>
