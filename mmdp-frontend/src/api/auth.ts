import http from "@/api/http";
import type { CurrentUser, LoginRequest } from "@/types/auth";

export async function login(payload: LoginRequest): Promise<CurrentUser> {
  return http.post("/api/auth/login", payload);
}

export async function logout(): Promise<void> {
  return http.post("/api/auth/logout");
}

export async function fetchCurrentUser(): Promise<CurrentUser> {
  return http.get("/api/auth/me");
}
