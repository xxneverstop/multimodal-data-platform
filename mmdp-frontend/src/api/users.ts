import http from "@/api/http";
import type {
  CreateUserRequest,
  UpdateUserRequest,
  UpdateUserStatusRequest,
  UserResponse,
} from "@/types/user";

export async function fetchUsers(): Promise<UserResponse[]> {
  return http.get("/api/admin/users");
}

export async function createUser(payload: CreateUserRequest): Promise<UserResponse> {
  return http.post("/api/admin/users", payload);
}

export async function updateUser(userId: number, payload: UpdateUserRequest): Promise<UserResponse> {
  return http.put(`/api/admin/users/${userId}`, payload);
}

export async function updateUserStatus(userId: number, payload: UpdateUserStatusRequest): Promise<UserResponse> {
  return http.put(`/api/admin/users/${userId}/status`, payload);
}
