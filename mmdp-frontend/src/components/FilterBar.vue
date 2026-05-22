<template>
  <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
    <label v-for="field in fields" :key="field.key" class="block">
      <span class="mb-1 block text-xs font-medium text-slate-500">{{ field.label }}</span>
      <input
        v-if="field.type !== 'select'"
        :value="modelValue[field.key] ?? ''"
        :placeholder="field.placeholder"
        class="app-input app-input-compact"
        @input="updateField(field.key, ($event.target as HTMLInputElement).value)"
      />
      <select
        v-else
        :value="modelValue[field.key] ?? ''"
        class="app-input app-input-compact"
        @change="updateField(field.key, ($event.target as HTMLSelectElement).value)"
      >
        <option value="">全部</option>
        <option v-for="option in field.options ?? []" :key="option.value" :value="option.value">{{ option.label }}</option>
      </select>
    </label>
  </div>
</template>

<script setup lang="ts">
export interface FilterFieldOption {
  label: string;
  value: string;
}

export interface FilterField {
  key: string;
  label: string;
  placeholder?: string;
  type?: "text" | "select";
  options?: FilterFieldOption[];
}

const props = defineProps<{
  fields: FilterField[];
  modelValue: Record<string, string>;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: Record<string, string>];
}>();

function updateField(key: string, value: string) {
  emit("update:modelValue", { ...props.modelValue, [key]: value });
}
</script>
