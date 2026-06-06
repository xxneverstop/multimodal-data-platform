<template>
  <section class="workspace-processing-summary">
    <button
      v-if="collapsible"
      type="button"
      class="workspace-disclosure"
      @click="expanded = !expanded"
    >
      <div class="min-w-0">
        <div class="text-sm font-semibold text-[var(--color-text-primary)]">{{ title }}</div>
        <div class="mt-1 text-xs text-[var(--color-text-tertiary)]">{{ summaryText }}</div>
      </div>
      <div class="text-xs font-medium text-[var(--color-text-secondary)]">{{ expanded ? "收起" : "展开" }}</div>
    </button>

    <div v-if="(!collapsible || expanded) && items.length" class="space-y-3">
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

    <div
      v-else-if="!collapsible || expanded"
      class="rounded-[14px] border border-dashed border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] px-4 py-5 text-sm text-[var(--color-text-tertiary)]"
    >
      {{ emptyText }}
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import StatusBadge from "@/components/StatusBadge.vue";

const props = withDefaults(
  defineProps<{
    items: Array<{
      title: string;
      subtitle: string;
      caption?: string;
      status?: string;
      to?: string;
    }>;
    emptyText?: string;
    collapsible?: boolean;
    defaultExpanded?: boolean;
    title?: string;
  }>(),
  {
    emptyText: "当前尚未进入处理阶段。",
    collapsible: false,
    defaultExpanded: false,
    title: "相关处理",
  },
);

const expanded = ref(props.defaultExpanded);

const summaryText = computed(() => {
  if (props.items.length) {
    const first = props.items[0];
    return `最近 ${props.items.length} 条处理记录${first?.status ? ` · 最近状态 ${first.status}` : ""}`;
  }
  return props.emptyText;
});
</script>
