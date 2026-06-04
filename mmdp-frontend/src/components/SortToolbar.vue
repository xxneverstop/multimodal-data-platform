<template>
  <div class="space-y-2">
    <div v-if="!compact" class="space-y-1">
      <div class="text-[11px] font-medium uppercase tracking-[0.12em] text-[var(--color-text-tertiary)]">
        排序与分页
      </div>
      <p v-if="description" class="text-sm text-[var(--color-text-secondary)]">
        {{ description }}
      </p>
    </div>
    <div class="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
      <label class="block min-w-[168px]">
        <span class="mb-1 block text-[11px] font-medium text-[var(--color-text-tertiary)]">排序字段</span>
        <select :value="sortField" class="app-input app-input-compact" @change="$emit('update:sortField', ($event.target as HTMLSelectElement).value)">
          <option v-for="option in sortOptions" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label class="block min-w-[132px]">
        <span class="mb-1 block text-[11px] font-medium text-[var(--color-text-tertiary)]">排序方式</span>
        <select :value="sortOrder" class="app-input app-input-compact" @change="$emit('update:sortOrder', ($event.target as HTMLSelectElement).value as 'asc' | 'desc')">
          <option value="desc">降序</option>
          <option value="asc">升序</option>
        </select>
      </label>
      <label class="block min-w-[120px]">
        <span class="mb-1 block text-[11px] font-medium text-[var(--color-text-tertiary)]">每页条数</span>
        <select :value="String(pageSize)" class="app-input app-input-compact" @change="$emit('update:pageSize', Number(($event.target as HTMLSelectElement).value))">
          <option v-for="size in pageSizeOptions" :key="size" :value="size">{{ size }} 条</option>
        </select>
      </label>
    </div>
  </div>
</template>

<script setup lang="ts">
export interface SortOption {
  label: string;
  value: string;
}

withDefaults(
  defineProps<{
    description?: string;
    sortField: string;
    sortOrder: "asc" | "desc";
    pageSize: number;
    sortOptions: SortOption[];
    pageSizeOptions?: number[];
    compact?: boolean;
  }>(),
  {
    description: "",
    pageSizeOptions: () => [10, 20, 50],
    compact: false,
  },
);

defineEmits<{
  "update:sortField": [value: string];
  "update:sortOrder": [value: "asc" | "desc"];
  "update:pageSize": [value: number];
}>();
</script>
