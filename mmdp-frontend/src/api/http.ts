import axios from "axios";
import type { ApiResponse } from "@/types/api";

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL?.trim() || "/",
  timeout: 15000
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
    const message =
      error?.response?.data?.message ||
      error?.message ||
      "网络请求失败";
    return Promise.reject(new Error(message));
  }
);

export default http;
