<template>
  <component
    :is="componentTag"
    v-bind="componentProps"
    class="inline-flex items-center justify-center gap-2 rounded-[8px] border px-3 py-2 text-sm font-medium transition-colors"
    :class="variantClass"
  >
    <slot />
  </component>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";

const props = withDefaults(
  defineProps<{
    variant?: "primary" | "secondary" | "danger";
    to?: string;
    href?: string;
    type?: "button" | "submit" | "reset";
    disabled?: boolean;
    block?: boolean;
  }>(),
  {
    variant: "secondary",
    type: "button",
    disabled: false,
    block: false
  }
);

const variantClass = computed(() => {
  const widthClass = props.block ? " w-full" : "";
  switch (props.variant) {
    case "primary":
      return `border-slate-900 bg-slate-900 text-white hover:border-slate-800 hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60${widthClass}`;
    case "danger":
      return `border-rose-600 bg-rose-600 text-white hover:border-rose-700 hover:bg-rose-700 disabled:cursor-not-allowed disabled:opacity-60${widthClass}`;
    default:
      return `border-slate-300 bg-white text-slate-700 hover:border-slate-400 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-60${widthClass}`;
  }
});

const componentTag = computed(() => {
  if (props.to) {
    return RouterLink;
  }
  if (props.href) {
    return "a";
  }
  return "button";
});

const componentProps = computed(() => {
  if (props.to) {
    return { to: props.to };
  }
  if (props.href) {
    return { href: props.href };
  }
  return {
    type: props.type,
    disabled: props.disabled
  };
});
</script>
