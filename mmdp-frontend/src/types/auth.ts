export interface CurrentUser {
  id: number;
  username: string;
  displayName: string;
  roleCode: string;
  isAdmin: boolean;
}

export interface LoginRequest {
  username: string;
  password: string;
}
