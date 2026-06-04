<template>
  <div class="app-metric-grid">
    <article
      v-for="item in items"
      :key="item.label"
      class="app-metric-card"
      :class="toneClass(item.tone)"
    >
      <div class="app-metric-accent"></div>
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0 space-y-1">
          <div class="app-metric-label">{{ item.label }}</div>
          <div class="app-metric-value">{{ item.value }}</div>
          <p v-if="item.caption" class="app-metric-caption">{{ item.caption }}</p>
        </div>
        <div class="app-metric-icon">
          <BaseIcon :name="item.icon" size="sm" />
        </div>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import BaseIcon from "@/components/BaseIcon.vue";

export type MetricTone =
  | "task"
  | "session"
  | "asset"
  | "process"
  | "qc"
  | "export"
  | "upload";

export interface MetricItem {
  label: string;
  value: string | number;
  caption?: string;
  icon: string;
  tone: MetricTone;
}

defineProps<{
  items: MetricItem[];
}>();

function toneClass(tone: MetricTone) {
  return `app-tone-${tone}`;
}
</script>
