<template>
  <article class="rounded-[12px] border border-slate-200 bg-slate-50 px-4 py-3">
    <div class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">{{ label }}</div>
    <div class="mt-2 text-lg font-semibold tracking-tight text-slate-900">{{ value }}</div>
    <p v-if="description" class="mt-1 text-xs leading-5 text-slate-500">{{ description }}</p>
  </article>
</template>

<script setup lang="ts">
defineProps<{
  label: string;
  value: string | number;
  description?: string;
}>();
</script>
