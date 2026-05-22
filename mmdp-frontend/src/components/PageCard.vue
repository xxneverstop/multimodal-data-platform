<template>
  <section class="w-full overflow-hidden rounded-[12px] border border-slate-200 bg-white" :class="secondary ? 'bg-slate-50' : ''">
    <div v-if="title || description || $slots.actions" class="border-b border-slate-200 px-4 py-3">
      <div class="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div class="space-y-1">
          <p v-if="eyebrow" class="text-[11px] font-medium tracking-[0.08em] text-slate-500">{{ eyebrow }}</p>
          <h2 v-if="title" class="text-sm font-semibold text-slate-900">{{ title }}</h2>
          <p v-if="description" class="max-w-4xl text-sm leading-5 text-slate-500">{{ description }}</p>
        </div>
        <div v-if="$slots.actions" class="shrink-0">
          <slot name="actions" />
        </div>
      </div>
    </div>
    <div :class="secondary ? 'px-4 py-3' : 'px-4 py-4'">
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
