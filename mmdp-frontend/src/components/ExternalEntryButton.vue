<template>
  <BaseButton :href="href" size="sm" variant="ghost">
    进入标注
  </BaseButton>
</template>

<script setup lang="ts">
import BaseButton from "@/components/BaseButton.vue";

defineProps<{
  href: string;
}>();
</script>
