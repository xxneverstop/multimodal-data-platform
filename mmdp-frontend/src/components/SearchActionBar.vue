<template>
  <div class="app-filter-toolbar">
    <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
      <div class="min-w-0 flex-1 space-y-3">
        <FilterBar
          :fields="resolvedCoreFields"
          :model-value="modelValue"
          columns="core"
          compact
          @update:model-value="$emit('update:modelValue', $event)"
        />

        <button
          v-if="hasAdvanced"
          type="button"
          class="app-filter-toggle"
          @click="$emit('update:advancedOpen', !advancedOpen)"
        >
          <BaseIcon
            name="chevron-down"
            size="sm"
            class="transition"
            :class="advancedOpen ? 'rotate-180' : ''"
          />
          <span>{{ advancedLabel }}</span>
          <span v-if="advancedActive" class="rounded-full bg-[var(--color-brand-50)] px-1.5 py-0.5 text-[10px] text-[var(--color-brand-600)]">已启用</span>
        </button>
      </div>

      <div class="flex shrink-0 flex-wrap items-center justify-end gap-2">
        <BaseButton variant="ghost" size="md" @click="$emit('reset')">
          重置
        </BaseButton>
        <BaseButton variant="soft" :tone="searchTone" size="md" @click="$emit('search')">
          <BaseIcon name="search" size="sm" />
          搜索
        </BaseButton>
        <slot name="actions" />
      </div>
    </div>

    <div v-if="hasAdvanced && advancedOpen" class="app-filter-advanced">
      <FilterBar
        v-if="resolvedAdvancedFields.length"
        :fields="resolvedAdvancedFields"
        :model-value="modelValue"
        columns="advanced"
        compact
        @update:model-value="$emit('update:modelValue', $event)"
      />
      <slot name="advanced" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import FilterBar, { type FilterField } from "@/components/FilterBar.vue";

const props = withDefaults(
  defineProps<{
    fields?: FilterField[];
    coreFields?: FilterField[];
    advancedFields?: FilterField[];
    modelValue: Record<string, string>;
    advancedOpen?: boolean;
    advancedActive?: boolean;
    advancedLabel?: string;
    searchTone?: "brand" | "task" | "session" | "asset" | "process" | "qc" | "export" | "upload";
  }>(),
  {
    fields: () => [],
    coreFields: () => [],
    advancedFields: () => [],
    advancedOpen: false,
    advancedActive: false,
    advancedLabel: "高级筛选",
    searchTone: "brand",
  },
);

defineEmits<{
  "update:modelValue": [value: Record<string, string>];
  "update:advancedOpen": [value: boolean];
  search: [];
  reset: [];
}>();

const resolvedCoreFields = computed(() => (props.coreFields.length ? props.coreFields : props.fields));
const resolvedAdvancedFields = computed(() => props.advancedFields);
const hasAdvanced = computed(() => resolvedAdvancedFields.value.length > 0);
</script>
