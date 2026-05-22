<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-[60] flex items-center justify-center p-4">
      <button type="button" class="absolute inset-0 bg-slate-900/20" @click="$emit('close')"></button>
      <section class="relative z-10 flex max-h-[min(88vh,760px)] w-full flex-col overflow-hidden rounded-[12px] border border-slate-200 bg-white shadow-[0_24px_80px_rgba(15,23,42,0.16)]" :class="sizeClass">
        <header class="border-b border-slate-200 px-5 py-4">
          <div class="flex items-start justify-between gap-4">
            <div class="min-w-0">
              <h2 class="text-base font-semibold text-slate-900">{{ title }}</h2>
              <p v-if="description" class="mt-1 text-sm text-slate-500">{{ description }}</p>
            </div>
            <button type="button" class="rounded-[8px] border border-slate-200 px-2.5 py-1 text-xs text-slate-600 transition hover:bg-slate-50" @click="$emit('close')">关闭</button>
          </div>
        </header>

        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <slot />
        </div>

        <footer class="flex items-center justify-end gap-2 border-t border-slate-200 px-5 py-3">
          <slot name="footer">
            <BaseButton size="sm" @click="$emit('close')">{{ cancelText }}</BaseButton>
            <BaseButton size="sm" variant="primary" :disabled="loading" @click="$emit('confirm')">{{ loading ? "处理中..." : confirmText }}</BaseButton>
          </slot>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from "vue";
import BaseButton from "@/components/BaseButton.vue";

const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    description?: string;
    size?: "sm" | "md" | "lg";
    confirmText?: string;
    cancelText?: string;
    loading?: boolean;
  }>(),
  {
    size: "md",
    confirmText: "确定",
    cancelText: "取消",
    loading: false
  }
);

defineEmits<{
  close: [];
  confirm: [];
}>();

const sizeClass = computed(() => {
  if (props.size === "sm") {
    return "max-w-[480px]";
  }
  if (props.size === "lg") {
    return "max-w-[840px]";
  }
  return "max-w-[640px]";
});
</script>
