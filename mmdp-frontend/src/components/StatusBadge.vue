<template>
  <span class="status-badge" :class="statusTone(status)">
    <span class="status-badge-dot bg-current opacity-70"></span>
    {{ resolvedLabel }}
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { formatStatusLabel, statusTone } from "@/utils/format";

const props = defineProps<{
  status?: string;
  label?: string;
}>();

const resolvedLabel = computed(() => props.label ?? formatStatusLabel(props.status));
</script>
