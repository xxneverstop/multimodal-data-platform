import axios from "axios";
import type { ApiResponse } from "@/types/api";

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL?.trim() || "/",
  timeout: 15000,
  withCredentials: true,
});

http.interceptors.response.use(
  (response) => {
    const payload = response.data as ApiResponse<unknown>;
    if (typeof payload?.success !== "boolean") {
      return response.data;
    }
    if (!payload.success) {
      return Promise.reject(new Error(payload.message || "请求失败"));
    }
    return payload.data;
  },
  (error) => {
    const status = error?.response?.status as number | undefined;
    const message = error?.response?.data?.message || error?.message || "网络请求失败";
    const normalizedError = new Error(message) as Error & { status?: number };
    normalizedError.status = status;
    if (status === 401 && typeof window !== "undefined") {
      const requestUrl = String(error?.config?.url || "");
      if (!requestUrl.includes("/api/auth/login") && !requestUrl.includes("/api/auth/me")) {
        window.dispatchEvent(new CustomEvent("mmdp-auth-expired"));
      }
    }
    return Promise.reject(normalizedError);
  }
);

export default http;
