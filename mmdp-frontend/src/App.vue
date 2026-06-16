<template>
  <RouterView />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { useRouter } from "vue-router";
import { useAuthStore } from "@/stores/auth";

const router = useRouter();
const authStore = useAuthStore();

function handleAuthExpired() {
  authStore.clear();
  if (window.location.pathname === "/login") {
    return;
  }
  const redirect = `${window.location.pathname}${window.location.search}`;
  router.replace({ path: "/login", query: { redirect } });
}

onMounted(() => {
  window.addEventListener("mmdp-auth-expired", handleAuthExpired);
});

onUnmounted(() => {
  window.removeEventListener("mmdp-auth-expired", handleAuthExpired);
});
</script>
