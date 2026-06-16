import { computed, reactive } from "vue";
import { fetchCurrentUser, login as loginRequest, logout as logoutRequest } from "@/api/auth";
import type { CurrentUser, LoginRequest } from "@/types/auth";

type AuthState = {
  user: CurrentUser | null;
  initialized: boolean;
  loading: boolean;
  initPromise: Promise<CurrentUser | null> | null;
};

const state = reactive<AuthState>({
  user: null,
  initialized: false,
  loading: false,
  initPromise: null,
});

async function initialize(): Promise<CurrentUser | null> {
  if (state.initialized) {
    return state.user;
  }
  if (state.initPromise) {
    return state.initPromise;
  }

  state.initPromise = (async () => {
    state.loading = true;
    try {
      const currentUser = await fetchCurrentUser();
      state.user = currentUser;
      return currentUser;
    } catch {
      state.user = null;
      return null;
    } finally {
      state.loading = false;
      state.initialized = true;
      state.initPromise = null;
    }
  })();

  return state.initPromise;
}

async function login(payload: LoginRequest): Promise<CurrentUser> {
  const currentUser = await loginRequest(payload);
  state.user = currentUser;
  state.initialized = true;
  return currentUser;
}

async function logout(): Promise<void> {
  try {
    await logoutRequest();
  } finally {
    clear();
  }
}

function clear() {
  state.user = null;
  state.initialized = true;
}

export function useAuthStore() {
  return {
    state,
    user: computed(() => state.user),
    isAuthenticated: computed(() => !!state.user),
    isAdmin: computed(() => !!state.user?.isAdmin),
    initialize,
    login,
    logout,
    clear,
  };
}
