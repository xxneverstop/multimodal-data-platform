<template>
  <section class="app-shell-panel w-full rounded-[12px] px-4 py-4">
    <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
      <div class="min-w-0 space-y-1">
        <div v-if="eyebrow" class="text-[11px] font-medium tracking-[0.08em] text-slate-500">{{ eyebrow }}</div>
        <h1 class="text-[22px] font-semibold leading-tight text-[var(--color-text-900)]">{{ title }}</h1>
        <p v-if="description" class="max-w-4xl text-sm leading-5 text-slate-500">{{ description }}</p>
      </div>

      <div v-if="$slots.actions || meta?.length" class="flex shrink-0 flex-col gap-2 xl:items-end">
        <div v-if="$slots.actions" class="flex flex-wrap justify-end gap-2">
          <slot name="actions" />
        </div>
        <div v-if="meta?.length" class="flex flex-wrap justify-end gap-2">
          <div v-for="item in meta" :key="item.label" class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
            <div class="font-medium text-slate-400">{{ item.label }}</div>
            <div class="mt-0.5 text-sm font-semibold text-slate-800">{{ item.value }}</div>
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
    description?: string;
    meta?: HeaderMetaItem[];
  }>(),
  {
    eyebrow: "",
    description: "",
    meta: () => []
  }
);
</script>
