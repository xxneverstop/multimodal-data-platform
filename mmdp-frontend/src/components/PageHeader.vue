<template>
  <section
    class="w-full"
    :class="surface === 'panel' ? 'app-shell-panel rounded-[12px] px-4 py-4' : 'px-0 py-1'"
  >
    <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
      <div class="min-w-0 space-y-1">
        <div v-if="eyebrow" class="text-[11px] font-medium tracking-[0.08em] text-[var(--color-text-tertiary)]">{{ eyebrow }}</div>
        <h1 class="text-[24px] font-semibold leading-tight text-[var(--color-text-900)]">{{ title }}</h1>
        <p v-if="description" class="max-w-4xl text-sm leading-5 text-[var(--color-text-secondary)]">{{ description }}</p>
      </div>

      <div v-if="$slots.actions || meta?.length" class="flex shrink-0 flex-col gap-2 xl:items-end">
        <div v-if="$slots.actions" class="flex flex-wrap justify-end gap-2">
          <slot name="actions" />
        </div>
        <div v-if="meta?.length" class="flex flex-wrap justify-end gap-2">
          <div
            v-for="item in meta"
            :key="item.label"
            class="rounded-[10px] border border-[var(--color-border-soft)] bg-[var(--color-surface-muted)] px-3 py-2 text-xs text-[var(--color-text-secondary)]"
          >
            <div class="font-medium text-[var(--color-text-tertiary)]">{{ item.label }}</div>
            <div class="mt-0.5 text-sm font-semibold text-[var(--color-text-primary)]">{{ item.value }}</div>
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
    surface?: "panel" | "plain";
  }>(),
  {
    eyebrow: "",
    description: "",
    meta: () => [],
    surface: "panel",
  },
);
</script>
