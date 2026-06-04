<template>
  <span class="inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium leading-5" :class="statusTone(status)">
    <span class="h-1 w-1 rounded-full bg-current opacity-70"></span>
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
