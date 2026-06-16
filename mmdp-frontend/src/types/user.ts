export type UserStatus = "ACTIVE" | "DISABLED";
export type UserRoleCode = "ADMIN" | "COLLECTOR" | "ANNOTATOR" | "VIEWER";

export interface UserResponse {
  id: number;
  username: string;
  displayName: string;
  roleCode: UserRoleCode;
  isAdmin: boolean;
  status: UserStatus;
  phone: string | null;
  email: string | null;
  remark: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateUserRequest {
  username: string;
  password: string;
  displayName: string;
  roleCode: UserRoleCode;
  isAdmin: boolean;
  status: UserStatus;
  phone?: string;
  email?: string;
  remark?: string;
}

export interface UpdateUserRequest {
  displayName: string;
  roleCode: UserRoleCode;
  isAdmin: boolean;
  status: UserStatus;
  phone?: string;
  email?: string;
  remark?: string;
}

export interface UpdateUserStatusRequest {
  status: UserStatus;
}

export const USER_ROLE_OPTIONS: Array<{ value: UserRoleCode; label: string }> = [
  { value: "ADMIN", label: "管理员" },
  { value: "COLLECTOR", label: "数据采集员" },
  { value: "ANNOTATOR", label: "标注员" },
  { value: "VIEWER", label: "查看者" },
];

export const USER_STATUS_OPTIONS: Array<{ value: UserStatus; label: string }> = [
  { value: "ACTIVE", label: "启用" },
  { value: "DISABLED", label: "停用" },
];

export function getRoleLabel(roleCode: string): string {
  return USER_ROLE_OPTIONS.find((item) => item.value === roleCode)?.label ?? roleCode;
}

export function getUserStatusLabel(status: string): string {
  return USER_STATUS_OPTIONS.find((item) => item.value === status)?.label ?? status;
}
