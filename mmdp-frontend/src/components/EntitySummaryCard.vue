<template>
  <div class="rounded-[var(--radius-xl)] border border-[var(--color-border-soft)] bg-white px-4 py-3.5 shadow-[var(--shadow-card)] border-l-[3px]" :class="accentBorder">
    <div class="text-[11px] font-semibold tracking-[0.06em] uppercase text-[var(--color-text-tertiary)]">{{ label }}</div>
    <div class="mt-1.5 text-[15px] font-bold text-[var(--color-text-primary)]">{{ value }}</div>
    <p v-if="description" class="mt-1 text-xs leading-5 text-[var(--color-text-secondary)]">{{ description }}</p>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string;
    value: string | number;
    description?: string;
    accentBorder?: string;
  }>(),
  {
    description: "",
    accentBorder: "border-l-[var(--color-brand-500)]",
  },
);
</script>
