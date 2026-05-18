<template>
  <span class="inline-flex items-center gap-1.5 rounded-[8px] px-2.5 py-1 text-xs font-medium ring-1 ring-inset" :class="statusTone(status)">
    <span class="h-1.5 w-1.5 rounded-full bg-current opacity-80"></span>
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
