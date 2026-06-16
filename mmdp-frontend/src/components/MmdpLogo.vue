<template>
  <div
    class="inline-flex items-center gap-2.5 text-[color:var(--logo-color,currentColor)]"
    :style="{ height: `${resolvedSize}px` }"
    aria-label="Human+ 多模态数据平台"
  >
    <!-- H+ 图标 -->
    <svg
      :width="resolvedSize"
      :height="resolvedSize"
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="shrink-0"
      aria-hidden="true"
    >
      <rect x="4" y="6" width="6" height="20" fill="currentColor" />
      <rect x="14" y="6" width="6" height="20" fill="currentColor" />
      <rect x="4" y="13" width="16" height="6" fill="currentColor" />
      <rect x="22" y="10" width="6" height="12" fill="currentColor" />
      <rect x="18" y="14" width="14" height="4" fill="currentColor" />
    </svg>

    <!-- 文字：HUMAN+ 在左，多模态数据平台在右，同一行 -->
    <span v-if="showText" class="inline-flex items-baseline gap-2 whitespace-nowrap leading-none">
      <span class="text-[16px] font-bold tracking-[0.16em]">Human+</span>
      <span class="text-[14px] font-bold tracking-[0.04em] opacity-70">多模态数据平台</span>
    </span>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    size?: number | string;
    showText?: boolean;
  }>(),
  {
    size: 32,
    showText: true,
  },
);

const resolvedSize = computed(() => {
  if (typeof props.size === "number") {
    return props.size;
  }
  const parsed = Number.parseFloat(props.size);
  return Number.isFinite(parsed) ? parsed : 32;
});
</script>
