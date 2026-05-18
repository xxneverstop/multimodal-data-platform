<template>
  <section
    class="overflow-hidden rounded-[14px] border shadow-[var(--shadow-card)]"
    :class="secondary ? 'border-slate-200 bg-slate-50' : 'app-section-card'"
  >
    <div v-if="title || description || $slots.actions" class="border-b px-4 py-3" :class="secondary ? 'border-slate-200/80' : 'border-slate-200'">
      <div class="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div class="space-y-1">
          <p v-if="eyebrow" class="text-[11px] font-medium uppercase tracking-[0.14em]" :class="secondary ? 'text-slate-400' : 'text-slate-500'">
            {{ eyebrow }}
          </p>
          <h2 v-if="title" class="font-semibold text-slate-900" :class="secondary ? 'text-sm' : 'text-base'">{{ title }}</h2>
          <p v-if="description" class="max-w-3xl leading-5" :class="secondary ? 'text-xs text-slate-500' : 'text-sm text-slate-500'">
            {{ description }}
          </p>
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
