<template>
  <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
    <div class="min-w-0 flex-1">
      <FilterBar :fields="fields" :model-value="modelValue" @update:model-value="$emit('update:modelValue', $event)" />
    </div>
    <div class="flex shrink-0 flex-wrap items-center justify-end gap-2">
      <BaseButton variant="secondary" size="md" @click="$emit('search')">
        <BaseIcon name="search" size="sm" />
        搜索
      </BaseButton>
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup lang="ts">
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import FilterBar, { type FilterField } from "@/components/FilterBar.vue";

defineProps<{
  fields: FilterField[];
  modelValue: Record<string, string>;
}>();

defineEmits<{
  "update:modelValue": [value: Record<string, string>];
  search: [];
}>();
</script>
