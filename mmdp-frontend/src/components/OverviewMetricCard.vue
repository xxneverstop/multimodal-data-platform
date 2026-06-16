<template>
  <article class="app-metric-card">
    <div class="app-metric-accent" />
    <div class="flex items-start justify-between">
      <div class="min-w-0 flex-1">
        <div class="metric-label-sm">{{ label }}</div>
        <div class="metric-value-lg">{{ value }}</div>
        <p v-if="description" class="metric-caption-xs">{{ description }}</p>
      </div>
      <div
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg"
        :class="iconTone"
        aria-hidden="true"
      >
        <BaseIcon :name="icon" size="sm" />
      </div>
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
    iconTone: "bg-blue-50 text-blue-600",
  },
);
</script>
