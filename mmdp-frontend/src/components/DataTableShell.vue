<template>
  <div class="overflow-hidden rounded-[12px] border border-slate-200 bg-white shadow-[var(--shadow-card)]">
    <div v-if="$slots.toolbar" class="border-b border-slate-200 bg-slate-50 px-4 py-3">
      <slot name="toolbar" />
    </div>
    <div class="overflow-x-auto">
      <slot />
    </div>
  </div>
</template>
