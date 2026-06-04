<template>
  <div class="w-full overflow-hidden rounded-[var(--radius-lg)] border border-[var(--color-border-default)] bg-white shadow-none">
    <div v-if="$slots.toolbar" class="border-b border-[var(--color-border-soft)] bg-[var(--color-surface-page)] px-4 py-2.5">
      <slot name="toolbar" />
    </div>
    <div class="w-full overflow-x-auto">
      <slot />
    </div>
  </div>
</template>
