<template>
  <section class="app-shell-panel rounded-[14px] px-4 py-4 md:px-5 md:py-4">
    <div class="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
      <div class="min-w-0 space-y-2.5">
        <div class="inline-flex items-center gap-2 rounded-[8px] bg-slate-100 px-2.5 py-1 text-[11px] font-medium uppercase tracking-[0.14em] text-slate-500">
          <span class="h-1.5 w-1.5 rounded-full bg-slate-500"></span>
          {{ eyebrow }}
        </div>
        <div class="space-y-1.5">
          <h1 class="text-xl font-semibold tracking-tight text-[var(--color-text-900)]">{{ title }}</h1>
          <p class="max-w-3xl text-sm leading-5 text-slate-500">{{ description }}</p>
        </div>
      </div>

      <div v-if="$slots.actions || meta?.length" class="flex flex-col gap-2.5 xl:items-end">
        <div v-if="$slots.actions" class="flex flex-wrap gap-2">
          <slot name="actions" />
        </div>
        <div v-if="meta?.length" class="flex flex-wrap gap-2">
          <div
            v-for="item in meta"
            :key="item.label"
            class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500"
          >
            <div class="font-medium uppercase tracking-[0.14em] text-slate-400">{{ item.label }}</div>
            <div class="mt-1 text-sm font-semibold text-slate-800">{{ item.value }}</div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
interface HeaderMetaItem {
  label: string;
  value: string | number;
}

withDefaults(
  defineProps<{
    eyebrow?: string;
    title: string;
    description: string;
    meta?: HeaderMetaItem[];
  }>(),
  {
    eyebrow: "Data Platform",
    meta: () => []
  }
);
</script>
