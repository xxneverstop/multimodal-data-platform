import http from "@/api/http";

export interface CollectionProfileSourceResponse {
  id: number;
  sourceKey: string;
  sourceName: string;
  sourceType: string;
  deviceRoleCode?: string | null;
  requiredFlag: boolean;
  filePattern?: string | null;
  parsedAssetType: string;
  playbackKind?: string | null;
  expectedFps?: number | null;
  expectedSampleRate?: number | null;
  sortOrder: number;
}

export interface CollectionProfileResponse {
  id: number;
  profileCode: string;
  profileName: string;
  taskTypeCode: string;
  modalityGroupCode: string;
  deviceGroupCode: string;
  packageRuleCode: string;
  parserRuleCode: string;
  archiveRuleCode: string;
  playbackRuleCode: string;
  version: string;
  sources: CollectionProfileSourceResponse[];
}

export function fetchCollectionProfiles(): Promise<CollectionProfileResponse[]> {
  return http.get("/api/collection-profiles") as any;
}

export function fetchCollectionProfile(profileId: number): Promise<CollectionProfileResponse> {
  return http.get(`/api/collection-profiles/${profileId}`) as any;
}
