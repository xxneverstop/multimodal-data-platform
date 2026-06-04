<template>
  <section class="workspace-health-grid">
    <article
      v-for="item in items"
      :key="item.label"
      class="workspace-health-item"
      :class="item.tone ? `app-tone-${item.tone}` : ''"
    >
      <div class="flex items-start gap-3">
        <div class="app-metric-icon mt-0.5">
          <BaseIcon :name="item.icon" size="sm" />
        </div>
        <div class="min-w-0">
          <div class="text-xs font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">{{ item.label }}</div>
          <div class="mt-1 text-sm font-semibold text-[var(--color-text-primary)]">{{ item.value }}</div>
          <p v-if="item.caption" class="mt-1 text-xs leading-5 text-[var(--color-text-tertiary)]">{{ item.caption }}</p>
        </div>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import BaseIcon from "@/components/BaseIcon.vue";
import type { MetricTone } from "@/components/BusinessMetrics.vue";

defineProps<{
  items: Array<{
    label: string;
    value: string | number;
    caption?: string;
    icon: string;
    tone?: MetricTone;
  }>;
}>();
</script>
