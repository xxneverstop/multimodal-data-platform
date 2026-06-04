<template>
  <div :class="gridClass">
    <label v-for="field in fields" :key="field.key" class="block">
      <span class="mb-1 block text-[11px] font-medium text-[var(--color-text-tertiary)]">{{ field.label }}</span>
      <input
        v-if="field.type !== 'select'"
        :value="modelValue[field.key] ?? ''"
        :placeholder="field.placeholder"
        :type="field.type === 'date' ? 'date' : 'text'"
        class="app-input"
        :class="compact ? 'app-input-compact' : ''"
        @input="updateField(field.key, ($event.target as HTMLInputElement).value)"
      />
      <select
        v-else
        :value="modelValue[field.key] ?? ''"
        class="app-input"
        :class="compact ? 'app-input-compact' : ''"
        @change="updateField(field.key, ($event.target as HTMLSelectElement).value)"
      >
        <option value="">全部</option>
        <option v-for="option in field.options ?? []" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
    </label>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";

export interface FilterFieldOption {
  label: string;
  value: string;
}

export interface FilterField {
  key: string;
  label: string;
  placeholder?: string;
  type?: "text" | "select" | "date";
  options?: FilterFieldOption[];
}

const props = withDefaults(
  defineProps<{
    fields: FilterField[];
    modelValue: Record<string, string>;
    compact?: boolean;
    columns?: "core" | "advanced" | "default";
  }>(),
  {
    compact: false,
    columns: "default",
  },
);

const emit = defineEmits<{
  "update:modelValue": [value: Record<string, string>];
}>();

const gridClass = computed(() => {
  if (props.columns === "core") {
    return "grid gap-3 md:grid-cols-3";
  }
  if (props.columns === "advanced") {
    return "grid gap-3 md:grid-cols-2 xl:grid-cols-4";
  }
  return "grid gap-3 md:grid-cols-2 xl:grid-cols-4";
});

function updateField(key: string, value: string) {
  emit("update:modelValue", { ...props.modelValue, [key]: value });
}
</script>
