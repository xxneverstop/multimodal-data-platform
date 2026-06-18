import http from "@/api/http";
import type {
  CollectionProfileResponse,
  CollectionProfileSourceResponse,
  CreateProfileRequest,
  CreateProfileSourceRequest,
  UpdateProfileRequest,
  UpdateProfileSourceRequest,
} from "@/types/profile";

// --- 原有只读接口（保持向下兼容，用于任务创建页等的 Profile 下拉）---

export function fetchCollectionProfiles(): Promise<CollectionProfileResponse[]> {
  return http.get("/api/collection-profiles") as any;
}

export function fetchCollectionProfile(profileId: number): Promise<CollectionProfileResponse> {
  return http.get(`/api/collection-profiles/${profileId}`) as any;
}

// --- 管理员 CRUD 接口 ---

/** 获取所有 Profile（含已禁用），仅管理页使用 */
export function fetchAllProfiles(): Promise<CollectionProfileResponse[]> {
  return http.get("/api/collection-profiles", { params: { includeDisabled: true } }) as any;
}

export function createProfile(payload: CreateProfileRequest): Promise<CollectionProfileResponse> {
  return http.post("/api/collection-profiles", payload) as any;
}

export function updateProfile(profileId: number, payload: UpdateProfileRequest): Promise<CollectionProfileResponse> {
  return http.put(`/api/collection-profiles/${profileId}`, payload) as any;
}

export function disableProfile(profileId: number): Promise<void> {
  return http.delete(`/api/collection-profiles/${profileId}`) as any;
}

export function addSource(profileId: number, payload: CreateProfileSourceRequest): Promise<CollectionProfileSourceResponse> {
  return http.post(`/api/collection-profiles/${profileId}/sources`, payload) as any;
}

export function updateSource(profileId: number, sourceId: number, payload: UpdateProfileSourceRequest): Promise<CollectionProfileSourceResponse> {
  return http.put(`/api/collection-profiles/${profileId}/sources/${sourceId}`, payload) as any;
}

export function disableSource(profileId: number, sourceId: number): Promise<void> {
  return http.delete(`/api/collection-profiles/${profileId}/sources/${sourceId}`) as any;
}

// 重新导出类型，方便其他模块引用（保持向下兼容）
export type { CollectionProfileResponse, CollectionProfileSourceResponse };
