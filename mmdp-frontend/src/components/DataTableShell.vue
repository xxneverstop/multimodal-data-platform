<template>
  <div
    class="w-full overflow-hidden"
    :class="flat ? '' : 'rounded-[var(--radius-xl)] border border-[var(--color-border-soft)] bg-white shadow-[var(--shadow-card)]'"
  >
    <div v-if="$slots.toolbar" class="border-b border-[var(--color-border-soft)] bg-[#f5f6f8] px-4 py-2.5">
      <slot name="toolbar" />
    </div>
    <div class="w-full overflow-x-auto">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    flat?: boolean;
  }>(),
  {
    flat: false,
  },
);
</script>
