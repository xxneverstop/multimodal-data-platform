<template>
  <span :class="wrapperClass" aria-hidden="true">
    <svg
      v-if="name === 'home'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M3 10.5 12 3l9 7.5" />
      <path d="M5 9.5V21h14V9.5" />
      <path d="M9.5 21v-6h5v6" />
    </svg>
    <svg
      v-else-if="name === 'grid'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <rect x="3" y="3" width="7" height="7" rx="1.5" />
      <rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" />
      <rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg>
    <svg
      v-else-if="name === 'settings'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M12 3v3" />
      <path d="M12 18v3" />
      <path d="M4.9 4.9l2.1 2.1" />
      <path d="M17 17l2.1 2.1" />
      <path d="M3 12h3" />
      <path d="M18 12h3" />
      <path d="M4.9 19.1 7 17" />
      <path d="M17 7l2.1-2.1" />
      <circle cx="12" cy="12" r="4" />
    </svg>
    <svg
      v-else-if="name === 'database'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <ellipse cx="12" cy="5" rx="7" ry="3" />
      <path d="M5 5v14c0 1.7 3.1 3 7 3s7-1.3 7-3V5" />
      <path d="M5 12c0 1.7 3.1 3 7 3s7-1.3 7-3" />
    </svg>
    <svg
      v-else-if="name === 'upload'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M12 16V4" />
      <path d="m7 9 5-5 5 5" />
      <path d="M4 18v1c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-1" />
    </svg>
    <svg
      v-else-if="name === 'clipboard-check'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <rect x="5" y="4" width="14" height="17" rx="2" />
      <path d="M9 4.5h6" />
      <path d="M9 11.5l2 2 4-4" />
      <path d="M9 17h6" />
    </svg>
    <svg
      v-else-if="name === 'camera'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M4 8h11a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2Z" />
      <path d="m17 10 5-3v14l-5-3" />
      <circle cx="9.5" cy="14" r="2.5" />
    </svg>
    <svg
      v-else-if="name === 'clock'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
    <svg
      v-else-if="name === 'workflow'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <rect x="3" y="4" width="6" height="6" rx="1.5" />
      <rect x="15" y="4" width="6" height="6" rx="1.5" />
      <rect x="15" y="14" width="6" height="6" rx="1.5" />
      <path d="M9 7h6" />
      <path d="M18 10v4" />
      <path d="M12 17h3" />
    </svg>
    <svg
      v-else-if="name === 'tags'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M20 13 11 4H4v7l9 9a2.8 2.8 0 0 0 4 0l3-3a2.8 2.8 0 0 0 0-4Z" />
      <circle cx="7.5" cy="7.5" r="1" />
    </svg>
    <svg
      v-else-if="name === 'shield'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M12 3 5 6v5c0 4.2 2.6 8.1 7 10 4.4-1.9 7-5.8 7-10V6l-7-3Z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
    <svg
      v-else-if="name === 'shield-alert'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M12 3 5 6v5c0 4.2 2.6 8.1 7 10 4.4-1.9 7-5.8 7-10V6l-7-3Z" />
      <path d="M12 8v5" />
      <path d="M12 16h.01" />
    </svg>
    <svg
      v-else-if="name === 'download'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M12 4v10" />
      <path d="m8 10 4 4 4-4" />
      <path d="M4 18v1c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2v-1" />
    </svg>
    <svg
      v-else-if="name === 'users'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <circle cx="9" cy="8" r="3" />
      <circle cx="17" cy="10" r="2.5" />
      <path d="M3 19c0-2.8 2.7-5 6-5s6 2.2 6 5" />
      <path d="M14.5 18c.4-1.8 2.2-3.3 4.5-3.3 1 0 2 .3 2.8.9" />
    </svg>
    <svg
      v-else-if="name === 'user-circle'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <circle cx="12" cy="12" r="9" />
      <circle cx="12" cy="9" r="2.8" />
      <path d="M7.5 18a5.3 5.3 0 0 1 9 0" />
    </svg>
    <svg
      v-else-if="name === 'log-out'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4" />
      <path d="M16 17l5-5-5-5" />
      <path d="M21 12H9" />
    </svg>
    <svg
      v-else-if="name === 'chevron-down'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="m6 9 6 6 6-6" />
    </svg>
    <svg
      v-else-if="name === 'hard-drive'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="M7 9h10" />
      <path d="M7 15h.01" />
      <path d="M11 15h.01" />
    </svg>
    <svg
      v-else-if="name === 'git-branch'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <circle cx="6" cy="6" r="2.5" />
      <circle cx="18" cy="6" r="2.5" />
      <circle cx="18" cy="18" r="2.5" />
      <path d="M8.5 6H12a4 4 0 0 1 4 4v5.5" />
      <path d="M6 8.5v7A4.5 4.5 0 0 0 10.5 20H15.5" />
    </svg>
    <svg
      v-else-if="name === 'server'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <rect x="3" y="4" width="18" height="6" rx="1.5" />
      <rect x="3" y="14" width="18" height="6" rx="1.5" />
      <path d="M7 7h.01" />
      <path d="M7 17h.01" />
    </svg>
    <svg
      v-else-if="name === 'book'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v17H6.5A2.5 2.5 0 0 0 4 22Z" />
      <path d="M4 5.5v14" />
      <path d="M9 7h6" />
      <path d="M9 11h6" />
    </svg>
    <svg
      v-else-if="name === 'search'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <circle cx="11" cy="11" r="6.5" />
      <path d="m20 20-3.5-3.5" />
    </svg>
    <svg
      v-else-if="name === 'refresh-cw'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M21 12a9 9 0 0 1-15.4 6.4" />
      <path d="M3 12A9 9 0 0 1 18.4 5.6" />
      <path d="M3 17v-4h4" />
      <path d="M21 7v4h-4" />
    </svg>
    <svg
      v-else-if="name === 'maximize'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M8 3H3v5" />
      <path d="M16 3h5v5" />
      <path d="M21 16v5h-5" />
      <path d="M8 21H3v-5" />
      <path d="m3 8 6-6" />
      <path d="m21 8-6-6" />
      <path d="m3 16 6 6" />
      <path d="m21 16-6 6" />
    </svg>
    <svg
      v-else-if="name === 'plus'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M12 5v14" />
      <path d="M5 12h14" />
    </svg>
    <svg
      v-else-if="name === 'play'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <polygon points="8 6 18 12 8 18 8 6" />
    </svg>
    <svg
      v-else-if="name === 'file'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z" />
      <path d="M14 3v5h5" />
    </svg>
    <svg
      v-else-if="name === 'file-text'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8Z" />
      <path d="M14 3v5h5" />
      <path d="M9 13h6" />
      <path d="M9 17h6" />
    </svg>
    <svg
      v-else-if="name === 'archive'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <rect x="3" y="4" width="18" height="5" rx="1.5" />
      <path d="M5 9h14v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2Z" />
      <path d="M10 13h4" />
    </svg>
    <svg
      v-else-if="name === 'sheet'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <rect x="4" y="3" width="16" height="18" rx="2" />
      <path d="M8 8h8" />
      <path d="M8 12h8" />
      <path d="M8 16h5" />
    </svg>
    <svg
      v-else-if="name === 'info'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M12 10v5" />
      <path d="M12 7h.01" />
    </svg>
    <svg
      v-else-if="name === 'edit'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M12 20h9" />
      <path d="m16.5 3.5 4 4L8 20l-5 1 1-5 12.5-12.5Z" />
    </svg>
    <svg
      v-else-if="name === 'trash'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
      <path d="M10 10v6" />
      <path d="M14 10v6" />
    </svg>
    <svg
      v-else-if="name === 'wrench'"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z" />
    </svg>
    <svg
      v-else
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      stroke-width="1.8"
      stroke-linecap="round"
      stroke-linejoin="round"
      class="h-full w-full"
    >
      <circle cx="12" cy="12" r="9" />
      <path d="M9 12h6" />
    </svg>
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";

const props = withDefaults(
  defineProps<{
    name: string;
    size?: "sm" | "md" | "lg";
  }>(),
  {
    size: "md"
  }
);

const wrapperClass = computed(() => {
  if (props.size === "sm") {
    return "inline-flex h-4 w-4 shrink-0";
  }
  if (props.size === "lg") {
    return "inline-flex h-6 w-6 shrink-0";
  }
  return "inline-flex h-5 w-5 shrink-0";
});
</script>
