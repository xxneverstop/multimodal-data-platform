<template>
  <section class="workspace-overview rounded-[16px] border border-[var(--color-border-soft)] bg-white px-4 py-3">
    <div class="flex flex-col gap-2 xl:flex-row xl:items-center xl:justify-between">
      <div class="min-w-0">
        <div class="app-summary-meta-inline text-[13px]">
          <template v-for="(item, index) in items" :key="item.label">
            <span><strong>{{ item.label }}</strong> {{ item.value || "-" }}</span>
            <span v-if="index < items.length - 1" class="text-[var(--color-text-quaternary)]">·</span>
          </template>
        </div>
        <p v-if="secondary" class="mt-2 text-xs leading-5 text-[var(--color-text-tertiary)]">
          {{ secondary }}
        </p>
      </div>

      <div v-if="$slots.trailing" class="shrink-0">
        <slot name="trailing" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  items: Array<{ label: string; value: string | number | null | undefined }>;
  secondary?: string;
}>();
</script>
