<template>
  <component :is="componentTag" v-bind="componentProps" class="app-button" :class="[sizeClass, variantClass, { 'w-full': block }]">
    <slot />
  </component>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";

const props = withDefaults(
  defineProps<{
    variant?: "primary" | "secondary" | "danger" | "ghost";
    size?: "sm" | "md";
    to?: string;
    href?: string;
    type?: "button" | "submit" | "reset";
    disabled?: boolean;
    block?: boolean;
  }>(),
  {
    variant: "secondary",
    size: "md",
    type: "button",
    disabled: false,
    block: false
  }
);

const sizeClass = computed(() => (props.size === "sm" ? "h-8 px-3 text-xs" : "h-9 px-3.5 text-sm"));

const variantClass = computed(() => {
  switch (props.variant) {
    case "primary":
      return "border-[var(--color-brand-500)] bg-[var(--color-brand-500)] text-white hover:border-[var(--color-brand-600)] hover:bg-[var(--color-brand-600)]";
    case "danger":
      return "border-rose-300 bg-rose-50 text-rose-700 hover:border-rose-400 hover:bg-rose-100";
    case "ghost":
      return "border-transparent bg-transparent text-slate-600 hover:border-slate-200 hover:bg-slate-50";
    default:
      return "border-slate-300 bg-white text-slate-700 hover:border-slate-400 hover:bg-slate-50";
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
