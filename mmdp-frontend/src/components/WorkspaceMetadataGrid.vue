<template>
  <div class="workspace-metadata-grid">
    <article
      v-for="item in items"
      :key="item.label"
      class="workspace-metadata-muted"
    >
      <div class="text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">{{ item.label }}</div>
      <div class="mt-1 text-sm text-[var(--color-text-primary)]">{{ item.value || "-" }}</div>
    </article>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  items: Array<{ label: string; value: string | number | null | undefined }>;
}>();
</script>
