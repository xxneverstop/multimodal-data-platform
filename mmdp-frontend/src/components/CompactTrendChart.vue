<template>
  <div class="space-y-3">
    <div class="flex flex-wrap items-center gap-3">
      <div v-for="seriesItem in series" :key="seriesItem.label" class="flex items-center gap-2">
        <span class="h-2.5 w-2.5 rounded-full" :style="{ backgroundColor: seriesItem.color }" />
        <span class="text-xs text-slate-500">{{ seriesItem.label }}</span>
      </div>
    </div>

    <div class="rounded-[10px] bg-slate-50 px-3 py-3">
      <svg viewBox="0 0 640 220" class="h-[200px] w-full" aria-hidden="true" preserveAspectRatio="none">
        <line v-for="grid in [0, 1, 2, 3]" :key="grid" x1="0" :y1="20 + grid * 55" x2="640" :y2="20 + grid * 55" stroke="#e2e8f0" stroke-width="1" />

        <polyline
          v-for="seriesItem in series"
          :key="seriesItem.label"
          fill="none"
          :stroke="seriesItem.color"
          stroke-width="3"
          stroke-linecap="round"
          stroke-linejoin="round"
          :points="buildPoints(seriesItem.values)"
        />

        <circle
          v-for="seriesItem in series"
          :key="`${seriesItem.label}-last`"
          :cx="lastPoint(seriesItem.values).x"
          :cy="lastPoint(seriesItem.values).y"
          r="4"
          :fill="seriesItem.color"
        />
      </svg>

      <div class="mt-2 flex items-center justify-between gap-3 text-[11px] text-slate-400">
        <span>{{ labels[0] }}</span>
        <span>{{ midLabel }}</span>
        <span>{{ labels[labels.length - 1] }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import type { OverviewTrendSeries } from "@/types/platform";

const props = defineProps<{
  labels: string[];
  series: OverviewTrendSeries[];
}>();

const chartMax = computed(() => {
  const maxValue = Math.max(1, ...props.series.flatMap((item) => item.values));
  return maxValue;
});

const midLabel = computed(() => {
  if (!props.labels.length) {
    return "";
  }
  return props.labels[Math.floor(props.labels.length / 2)];
});

function pointAt(index: number, value: number) {
  const width = 640;
  const height = 220;
  const left = 12;
  const right = 12;
  const top = 20;
  const bottom = 20;
  const drawableWidth = width - left - right;
  const drawableHeight = height - top - bottom;
  const x = left + (drawableWidth * index) / Math.max(props.labels.length - 1, 1);
  const y = top + drawableHeight - (value / chartMax.value) * drawableHeight;
  return { x, y };
}

function buildPoints(values: number[]) {
  return values.map((value, index) => {
    const point = pointAt(index, value);
    return `${point.x},${point.y}`;
  }).join(" ");
}

function lastPoint(values: number[]) {
  const index = Math.max(values.length - 1, 0);
  return pointAt(index, values[index] ?? 0);
}
</script>
