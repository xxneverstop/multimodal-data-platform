<template>
  <component
    :is="componentTag"
    v-bind="componentProps"
    class="app-button"
    :class="[sizeClass, variantClass, { 'w-full': block, 'pointer-events-none opacity-50': disabled }]"
    :style="toneStyle"
  >
    <slot />
  </component>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { RouterLink } from "vue-router";

const props = withDefaults(
  defineProps<{
    variant?: "primary" | "secondary" | "danger" | "ghost" | "soft";
    size?: "sm" | "md";
    to?: string;
    href?: string;
    type?: "button" | "submit" | "reset";
    disabled?: boolean;
    block?: boolean;
    tone?: "brand" | "task" | "session" | "asset" | "process" | "qc" | "export" | "upload";
  }>(),
  {
    variant: "secondary",
    size: "md",
    type: "button",
    disabled: false,
    block: false,
    tone: "brand",
  },
);

const sizeClass = computed(() => (props.size === "sm" ? "h-7 px-2.5 text-xs" : "h-8 px-3 text-[13px]"));

const variantClass = computed(() => {
  switch (props.variant) {
    case "primary":
      return "border-[var(--color-brand-500)] bg-[var(--color-brand-500)] text-white shadow-none hover:border-[var(--color-brand-600)] hover:bg-[var(--color-brand-600)] active:border-[var(--color-brand-700)] active:bg-[var(--color-brand-700)]";
    case "soft":
      return "border-[var(--button-soft-border)] bg-[var(--button-soft-bg)] text-[var(--button-soft-text)] shadow-none hover:border-[var(--button-soft-hover-border)] hover:bg-[var(--button-soft-hover-bg)]";
    case "danger":
      return "border-[var(--color-border-default)] bg-white text-[var(--color-danger-700)] shadow-none hover:border-red-300 hover:bg-[var(--color-danger-100)] active:bg-red-200";
    case "ghost":
      return "border-transparent bg-transparent text-[var(--color-text-secondary)] shadow-none hover:bg-[var(--color-hover-subtle)] hover:text-[var(--color-text-primary)] active:bg-[var(--color-active-subtle)]";
    default:
      return "border-[var(--color-border-default)] bg-[var(--color-hover-subtle)] text-[var(--color-text-primary)] shadow-none hover:border-[var(--color-text-tertiary)] hover:bg-[var(--color-active-subtle)] active:bg-[var(--color-border-soft)]";
  }
});

const toneStyle = computed(() => {
  const tone = props.tone ?? "brand";
  return {
    "--button-soft-bg": `var(--module-${tone}-soft-bg)`,
    "--button-soft-border": `var(--module-${tone}-soft-border)`,
    "--button-soft-text": `var(--module-${tone}-soft-text)`,
    "--button-soft-hover-bg": `var(--module-${tone}-hover-bg)`,
    "--button-soft-hover-border": `var(--module-${tone}-accent)`,
  };
});

const componentTag = computed(() => {
  if (props.to && !props.disabled) {
    return RouterLink;
  }
  if (props.href && !props.disabled) {
    return "a";
  }
  return "button";
});

const componentProps = computed(() => {
  if (props.to && !props.disabled) {
    return { to: props.to };
  }
  if (props.href && !props.disabled) {
    return { href: props.href };
  }
  return {
    type: props.type,
    disabled: props.disabled,
  };
});
</script>
