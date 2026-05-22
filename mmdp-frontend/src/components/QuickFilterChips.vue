<template>
  <div class="flex flex-wrap gap-2">
    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      class="rounded-[8px] border px-3 py-1.5 text-sm transition"
      :class="modelValue === item.value ? 'border-slate-300 bg-slate-100 text-slate-900' : 'border-slate-200 bg-white text-slate-600 hover:bg-slate-50'"
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
