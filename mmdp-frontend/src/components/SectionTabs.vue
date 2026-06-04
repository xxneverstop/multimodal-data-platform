<template>
  <div class="flex flex-wrap border-b border-[var(--color-border-default)]">
    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      class="relative -mb-px rounded-none border-0 bg-transparent px-3 py-2 text-[13px] leading-5 transition-colors duration-150 focus-visible:outline-none focus-visible:shadow-[0_0_0_2px_rgba(9,105,218,0.25)]"
      :class="
        modelValue === item.value
          ? 'border-b-2 border-b-[var(--color-brand-500)] font-semibold text-[var(--color-text-primary)]'
          : 'border-b-2 border-b-transparent font-normal text-[var(--color-text-secondary)] hover:bg-[var(--color-hover-subtle)] hover:text-[var(--color-text-primary)]'
      "
      @click="$emit('update:modelValue', item.value)"
    >
      {{ item.label }}
    </button>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  items: Array<{ label: string; value: string }>;
  modelValue: string;
}>();

defineEmits<{
  "update:modelValue": [value: string];
}>();
</script>
