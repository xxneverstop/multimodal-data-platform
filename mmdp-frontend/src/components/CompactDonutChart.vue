<template>
  <div class="flex min-h-[180px] items-center gap-4">
    <div class="relative shrink-0">
      <svg viewBox="0 0 120 120" class="h-[112px] w-[112px]" aria-hidden="true">
        <circle cx="60" cy="60" r="38" fill="none" stroke="#e2e8f0" stroke-width="12" />
        <circle
          v-for="segment in segments"
          :key="segment.label"
          cx="60"
          cy="60"
          r="38"
          fill="none"
          :stroke="segment.color"
          stroke-width="12"
          stroke-linecap="round"
          :stroke-dasharray="segment.dashArray"
          :stroke-dashoffset="segment.dashOffset"
          class="-rotate-90 origin-center"
        />
      </svg>
      <div class="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
        <div class="text-[22px] font-semibold leading-none text-slate-900">{{ total }}</div>
        <div class="mt-1 text-[11px] tracking-[0.08em] text-slate-400">{{ centerLabel }}</div>
      </div>
    </div>

    <div class="min-w-0 flex-1 space-y-2">
      <div
        v-for="item in items"
        :key="item.label"
        class="flex items-center justify-between gap-3 rounded-[8px] bg-slate-50 px-2.5 py-2"
      >
        <div class="flex min-w-0 items-center gap-2">
          <span class="h-2.5 w-2.5 shrink-0 rounded-full" :style="{ backgroundColor: item.color }" />
          <span class="truncate text-xs text-slate-600">{{ item.label }}</span>
        </div>
        <span class="shrink-0 text-xs font-medium text-slate-900">{{ item.value }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { OverviewDistributionItem } from "@/types/platform";

const props = withDefaults(
  defineProps<{
    items: OverviewDistributionItem[];
    centerLabel?: string;
  }>(),
  {
    centerLabel: "总量"
  }
);

const total = computed(() => props.items.reduce((sum, item) => sum + item.value, 0));

const segments = computed(() => {
  const circumference = 2 * Math.PI * 38;
  let offset = 0;

  if (total.value <= 0) {
    return [];
  }

  return props.items
    .filter((item) => item.value > 0)
    .map((item) => {
      const length = (item.value / total.value) * circumference;
      const segment = {
        label: item.label,
        color: item.color,
        dashArray: `${Math.max(length - 2, 0)} ${circumference}`,
        dashOffset: -offset
      };
      offset += length;
      return segment;
    });
});
</script>
