<template>
  <div class="flex flex-col gap-3 border-t border-[var(--color-border-soft)] px-4 py-3 text-sm text-[var(--color-text-secondary)] md:flex-row md:items-center md:justify-between">
    <div>第 {{ safePage }} / {{ totalPages }} 页，共 {{ total }} 条</div>
    <div class="flex items-center gap-2">
      <BaseButton size="sm" variant="secondary" :disabled="safePage <= 1" @click="$emit('update:page', safePage - 1)">
        上一页
      </BaseButton>
      <BaseButton size="sm" variant="secondary" :disabled="safePage >= totalPages" @click="$emit('update:page', safePage + 1)">
        下一页
      </BaseButton>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import BaseButton from "@/components/BaseButton.vue";

const props = defineProps<{
  page: number;
  pageSize: number;
  total: number;
}>();

defineEmits<{
  "update:page": [page: number];
}>();

const safePage = computed(() => Math.max(props.page || 1, 1));
const totalPages = computed(() => Math.max(1, Math.ceil((props.total || 0) / Math.max(props.pageSize || 1, 1))));
</script>
