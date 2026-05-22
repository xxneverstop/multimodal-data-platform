<template>
  <div
    class="inline-flex items-center gap-2.5 text-[color:var(--logo-color,currentColor)]"
    :style="{ height: `${resolvedSize}px` }"
    aria-label="MMDP 多模态数据平台"
  >
    <svg
      :width="resolvedSize"
      :height="resolvedSize"
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="shrink-0"
      aria-hidden="true"
    >
      <rect x="4" y="4" width="10" height="10" rx="2.5" fill="currentColor" opacity="0.96" />
      <rect x="18" y="4" width="10" height="10" rx="2.5" fill="currentColor" opacity="0.68" />
      <rect x="4" y="18" width="10" height="10" rx="2.5" fill="currentColor" opacity="0.68" />
      <path
        d="M23 17.5a5.5 5.5 0 1 0 0 11a5.5 5.5 0 0 0 0-11Zm0 2.2a3.3 3.3 0 1 1 0 6.6a3.3 3.3 0 0 1 0-6.6Z"
        fill="currentColor"
      />
      <path d="M14 9h4M9 14v4M18.5 13.5l-2.2 2.2M14 23h3.2" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    </svg>

    <svg
      v-if="showText"
      :height="resolvedSize"
      viewBox="0 0 190 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      class="min-w-0"
      aria-hidden="true"
    >
      <text
        x="0"
        y="13"
        fill="currentColor"
        font-size="12"
        font-weight="700"
        letter-spacing="0.16em"
        font-family="Segoe UI, PingFang SC, Microsoft YaHei UI, sans-serif"
      >
        MMDP
      </text>
      <text
        x="0"
        y="26"
        fill="currentColor"
        opacity="0.72"
        font-size="9"
        font-weight="500"
        letter-spacing="0.04em"
        font-family="Segoe UI, PingFang SC, Microsoft YaHei UI, sans-serif"
      >
        多模态数据平台
      </text>
    </svg>
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
    showText: true
  }
);

const resolvedSize = computed(() => {
  if (typeof props.size === "number") {
    return props.size;
  }
  const parsed = Number.parseFloat(props.size);
  return Number.isFinite(parsed) ? parsed : 32;
});
</script>
