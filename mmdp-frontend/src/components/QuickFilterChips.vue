<template>
  <div class="chip-group">
    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      class="chip-item"
      :class="modelValue === item.value ? 'chip-item-active' : ''"
      @click="$emit('update:modelValue', modelValue === item.value ? '' : item.value)"
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
