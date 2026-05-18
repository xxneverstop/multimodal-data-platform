<template>
  <div class="rounded-[16px] border border-dashed border-slate-300 bg-slate-50 p-8 text-center">
    <div class="mx-auto flex h-14 w-14 items-center justify-center rounded-[12px] border border-slate-200 bg-white text-lg font-semibold text-slate-700 shadow-[var(--shadow-card)]">
      {{ icon }}
    </div>
    <h3 class="mt-5 text-lg font-semibold text-[var(--color-text-900)]">{{ title }}</h3>
    <p class="mx-auto mt-2 max-w-xl text-sm leading-6 text-[var(--color-text-500)]">{{ description }}</p>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string;
    description: string;
    icon?: string;
  }>(),
  {
    icon: "空"
  }
);
</script>
