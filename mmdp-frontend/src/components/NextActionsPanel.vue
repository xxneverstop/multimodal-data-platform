<template>
  <section class="workspace-next-actions">
    <article
      v-for="action in actions"
      :key="action.label"
      class="workspace-next-action-item"
      :class="action.primary ? `app-tone-${action.tone || 'brand'}` : ''"
    >
      <div class="min-w-0 space-y-1">
        <div class="flex items-center gap-2">
          <span v-if="action.primary" class="rounded-full border border-[var(--module-current-soft-border)] bg-[var(--module-current-soft-bg)] px-2 py-0.5 text-[11px] font-medium text-[var(--module-current-soft-text)]">
            推荐
          </span>
          <div class="text-sm font-semibold text-[var(--color-text-primary)]">{{ action.label }}</div>
        </div>
        <p class="text-xs leading-5 text-[var(--color-text-tertiary)]">{{ action.description }}</p>
      </div>
      <div class="shrink-0">
        <BaseButton
          :to="action.to"
          :variant="action.primary ? 'soft' : 'ghost'"
          :tone="action.tone || 'brand'"
          size="sm"
        >
          {{ action.cta || action.label }}
        </BaseButton>
      </div>
    </article>
  </section>
</template>

<script setup lang="ts">
import BaseButton from "@/components/BaseButton.vue";
import type { MetricTone } from "@/components/BusinessMetrics.vue";

defineProps<{
  actions: Array<{
    label: string;
    description: string;
    to: string;
    cta?: string;
    primary?: boolean;
    tone?: MetricTone;
  }>;
}>();
</script>
