<template>
  <article class="relative overflow-hidden rounded-[12px] border border-slate-200 bg-white px-4 py-3">
    <div
      class="absolute right-3 top-3 flex h-10 w-10 items-center justify-center rounded-full"
      :class="iconTone"
      aria-hidden="true"
    >
      <BaseIcon :name="icon" />
    </div>
    <div class="pr-12">
      <div class="text-[11px] font-medium tracking-[0.08em] text-slate-500">{{ label }}</div>
      <div class="mt-2 text-[28px] font-semibold leading-none text-slate-900">{{ value }}</div>
      <p v-if="description" class="mt-2 text-xs leading-5 text-slate-500">{{ description }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import BaseIcon from "@/components/BaseIcon.vue";

withDefaults(
  defineProps<{
    label: string;
    value: string | number;
    description?: string;
    icon: string;
    iconTone?: string;
  }>(),
  {
    description: "",
    iconTone: "bg-slate-100 text-slate-500"
  }
);
</script>
