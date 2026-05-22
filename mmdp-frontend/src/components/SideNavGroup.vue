<template>
  <button
    type="button"
    class="nav-rail-button"
    :class="active ? 'nav-rail-button-active' : ''"
    :title="label"
    @click="$emit('toggle')"
  >
    <BaseIcon :name="icon" />
  </button>
</template>

<script setup lang="ts">
import BaseIcon from "@/components/BaseIcon.vue";

defineProps<{
  label: string;
  icon: string;
  active: boolean;
}>();

defineEmits<{
  toggle: [];
}>();
</script>
