<template>
  <Teleport to="body">
    <div v-if="open" class="fixed inset-0 z-50 flex justify-end">
      <button type="button" class="absolute inset-0 bg-slate-900/20" @click="$emit('close')"></button>
      <aside class="relative z-10 flex h-full w-full max-w-[560px] flex-col border-l border-slate-200 bg-white shadow-[0_20px_60px_rgba(15,23,42,0.16)]">
        <div class="border-b border-slate-200 px-5 py-4">
          <div class="flex items-start justify-between gap-4">
            <div class="space-y-1">
              <div v-if="eyebrow" class="text-[11px] font-medium text-slate-400">{{ eyebrow }}</div>
              <h2 class="text-base font-semibold text-slate-900">{{ title }}</h2>
              <p v-if="description" class="text-sm leading-5 text-slate-500">{{ description }}</p>
            </div>
            <button type="button" class="rounded-[8px] border border-slate-200 px-2.5 py-1 text-xs text-slate-600 transition hover:bg-slate-50" @click="$emit('close')">关闭</button>
          </div>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto px-5 py-4">
          <slot />
        </div>
      </aside>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
defineProps<{
  open: boolean;
  title: string;
  description?: string;
  eyebrow?: string;
}>();

defineEmits<{
  close: [];
}>();
</script>
