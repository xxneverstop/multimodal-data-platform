<template>
  <div class="tab-bar">
    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      class="tab-bar-item"
      :class="
        modelValue === item.value
          ? 'tab-bar-item-active'
          : ''
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
