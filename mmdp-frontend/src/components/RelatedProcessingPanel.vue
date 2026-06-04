<template>
  <section class="workspace-processing-summary">
    <div v-if="items.length" class="space-y-3">
      <article
        v-for="item in items"
        :key="item.title"
        class="workspace-secondary-row app-tone-process"
      >
        <div class="flex items-start gap-3">
          <div class="app-metric-icon mt-0.5">
            <BaseIcon name="workflow" size="sm" />
          </div>
          <div class="min-w-0 space-y-1">
            <div class="text-sm font-semibold text-[var(--color-text-primary)]">{{ item.title }}</div>
            <div class="text-xs text-[var(--color-text-secondary)]">{{ item.subtitle }}</div>
            <p v-if="item.caption" class="text-xs leading-5 text-[var(--color-text-tertiary)]">{{ item.caption }}</p>
          </div>
        </div>
        <div class="flex flex-wrap items-center justify-end gap-2">
          <StatusBadge v-if="item.status" :status="item.status" />
          <BaseButton v-if="item.to" :to="item.to" variant="ghost" size="sm">查看</BaseButton>
        </div>
      </article>
    </div>
    <div v-else class="rounded-[14px] border border-dashed border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] px-4 py-8 text-sm text-[var(--color-text-tertiary)]">
      {{ emptyText }}
    </div>
  </section>
</template>

<script setup lang="ts">
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import StatusBadge from "@/components/StatusBadge.vue";

withDefaults(
  defineProps<{
    items: Array<{
      title: string;
      subtitle: string;
      caption?: string;
      status?: string;
      to?: string;
    }>;
    emptyText?: string;
  }>(),
  {
    emptyText: "当前尚未进入处理阶段。",
  },
);
</script>
