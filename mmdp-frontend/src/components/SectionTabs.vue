<template>
  <div class="flex flex-wrap gap-1 border-b border-slate-200">
    <button
      v-for="item in items"
      :key="item.value"
      type="button"
      class="rounded-t-[8px] border border-b-0 px-3 py-1.5 text-sm transition"
      :class="modelValue === item.value ? 'border-slate-200 bg-white font-medium text-slate-900' : 'border-transparent bg-transparent text-slate-500 hover:text-slate-900'"
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
