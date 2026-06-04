<template>
  <section
    class="w-full overflow-hidden rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-white shadow-none"
    :class="secondary ? 'border-0 bg-[var(--color-surface-page)]' : ''"
  >
    <div v-if="title || description || $slots.actions" class="border-b border-[var(--color-border-soft)] px-4 py-2.5">
      <div class="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
        <div class="space-y-0.5">
          <p v-if="eyebrow" class="text-[11px] font-medium tracking-[0.04em] text-[var(--color-text-tertiary)]">{{ eyebrow }}</p>
          <h2 v-if="title" class="text-[15px] font-semibold text-[var(--color-text-primary)]">{{ title }}</h2>
          <p v-if="description" class="max-w-4xl text-[13px] leading-5 text-[var(--color-text-secondary)]">{{ description }}</p>
        </div>
        <div v-if="$slots.actions" class="shrink-0">
          <slot name="actions" />
        </div>
      </div>
    </div>
    <div :class="secondary ? 'px-4 py-2.5' : 'px-4 py-3'">
      <slot />
    </div>
  </section>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string;
    description?: string;
    eyebrow?: string;
    secondary?: boolean;
  }>(),
  {
    secondary: false
  }
);
</script>
