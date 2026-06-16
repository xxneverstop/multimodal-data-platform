<template>
  <section
    class="w-full overflow-hidden rounded-[var(--radius-xl)] border border-[var(--color-border-soft)] bg-white"
    :class="[secondary ? 'border-0 bg-[var(--color-surface-page)] shadow-none' : 'shadow-[var(--shadow-card)]']"
  >
    <div v-if="title || description || $slots.actions" class="border-b border-[var(--color-border-soft)] px-4 py-2.5">
      <div class="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
        <div class="space-y-0.5">
          <p v-if="eyebrow" class="text-[11px] font-semibold tracking-[0.06em] text-[var(--color-text-tertiary)] uppercase">{{ eyebrow }}</p>
          <h2 v-if="title" class="text-[14px] font-bold tracking-[-0.01em] text-[var(--color-text-primary)]">{{ title }}</h2>
          <p v-if="description" class="max-w-4xl text-[12px] leading-5 text-[var(--color-text-secondary)]">{{ description }}</p>
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
