<template>
  <div class="login-page flex min-h-screen items-center justify-center px-4 py-10">
    <div class="grid w-full max-w-[1040px] items-center gap-8 lg:grid-cols-[1fr_420px]">
      <section class="login-brand flex flex-col items-center justify-center px-4 py-8 text-center lg:items-start lg:px-10 lg:text-left">
        <div class="login-brand-mark flex items-center gap-5">
          <div class="login-brand-logo-wrap">
            <MmdpLogo :size="64" :show-text="false" />
          </div>
          <span class="login-brand-title text-[38px] font-bold tracking-[0.08em] text-[var(--color-text-primary)]">
            Human+
          </span>
        </div>
        <div class="login-brand-subtitle mt-5 text-lg font-medium tracking-[0.06em] text-[var(--color-text-secondary)]">
          多模态数据平台
        </div>
      </section>

      <section class="rounded-[20px] border border-[var(--color-border-soft)] bg-white p-6 shadow-[var(--shadow-card)] md:p-8">
        <div>
          <h1 class="text-2xl font-semibold text-[var(--color-text-primary)]">登录</h1>
        </div>

        <form class="mt-8 space-y-4" @submit.prevent="submitLogin">
          <label class="block">
            <span class="mb-2 block text-sm font-medium text-[var(--color-text-primary)]">登录账号</span>
            <input
              v-model="form.username"
              class="app-input"
              autocomplete="username"
              placeholder="请输入登录账号"
            />
          </label>

          <label class="block">
            <span class="mb-2 block text-sm font-medium text-[var(--color-text-primary)]">登录密码</span>
            <input
              v-model="form.password"
              class="app-input"
              type="password"
              autocomplete="current-password"
              placeholder="请输入登录密码"
            />
          </label>

          <p v-if="errorMessage" class="rounded-[12px] border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-600">
            {{ errorMessage }}
          </p>

          <BaseButton type="submit" variant="primary" block :disabled="submitting">
            {{ submitting ? "登录中..." : "登录" }}
          </BaseButton>
        </form>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import BaseButton from "@/components/BaseButton.vue";
import MmdpLogo from "@/components/MmdpLogo.vue";
import { useAuthStore } from "@/stores/auth";

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();

const form = reactive({
  username: "",
  password: "",
});
const submitting = ref(false);
const errorMessage = ref("");

async function submitLogin() {
  errorMessage.value = "";
  if (!form.username.trim() || !form.password.trim()) {
    errorMessage.value = "请输入完整的账号和密码";
    return;
  }

  submitting.value = true;
  try {
    await authStore.login({
      username: form.username.trim(),
      password: form.password,
    });
    const redirect = typeof route.query.redirect === "string" && route.query.redirect ? route.query.redirect : "/";
    router.replace(redirect);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "登录失败";
  } finally {
    submitting.value = false;
  }
}
</script>
