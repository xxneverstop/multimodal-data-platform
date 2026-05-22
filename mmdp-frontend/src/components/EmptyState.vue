<template>
  <div class="rounded-[12px] border border-dashed border-slate-300 bg-slate-50 px-6 py-10 text-center">
    <div class="mx-auto flex h-10 w-10 items-center justify-center rounded-[10px] border border-slate-200 bg-white text-slate-500">
      <BaseIcon :name="icon" />
    </div>
    <h3 class="mt-3 text-base font-semibold text-[var(--color-text-900)]">{{ title }}</h3>
    <p class="mx-auto mt-1 max-w-xl text-sm leading-5 text-[var(--color-text-500)]">{{ description }}</p>
  </div>
</template>

<script setup lang="ts">
import BaseIcon from "@/components/BaseIcon.vue";

withDefaults(
  defineProps<{
    title: string;
    description: string;
    icon?: string;
  }>(),
  {
    icon: "info"
  }
);
</script>
