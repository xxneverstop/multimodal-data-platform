<template>
  <div class="rounded-[12px] border border-slate-200 bg-slate-50 px-4 py-3">
    <div class="text-[11px] font-medium tracking-[0.08em] text-slate-500">{{ label }}</div>
    <div class="mt-1 text-sm font-semibold text-slate-900">{{ value }}</div>
    <p v-if="description" class="mt-1 text-xs leading-5 text-slate-500">{{ description }}</p>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  label: string;
  value: string | number;
  description?: string;
}>();
</script>
