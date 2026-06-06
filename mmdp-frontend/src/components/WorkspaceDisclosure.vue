<template>
  <section class="workspace-disclosure-panel">
    <button type="button" class="workspace-disclosure" @click="expanded = !expanded">
      <div class="min-w-0">
        <div class="text-sm font-semibold text-[var(--color-text-primary)]">{{ title }}</div>
        <div v-if="summary" class="mt-1 text-xs text-[var(--color-text-tertiary)]">{{ summary }}</div>
      </div>
      <div class="text-xs font-medium text-[var(--color-text-secondary)]">{{ expanded ? "收起" : "展开" }}</div>
    </button>
    <div v-if="expanded" class="mt-3">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from "vue";

const props = withDefaults(
  defineProps<{
    title: string;
    summary?: string;
    defaultExpanded?: boolean;
  }>(),
  {
    summary: "",
    defaultExpanded: false,
  },
);

const expanded = ref(props.defaultExpanded);
</script>
