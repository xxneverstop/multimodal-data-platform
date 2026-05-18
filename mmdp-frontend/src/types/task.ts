export interface TaskResponse {
  id: number;
  taskName: string;
  subjectCode: string;
  actionName: string;
  deviceType: string;
  modality: string;
  collectDate: string;
  scene?: string;
  operatorName?: string;
  captureLocation?: string;
  status: string;
  remark?: string;
  createdAt: string;
  updatedAt: string;
}

export interface TaskPageResponse {
  current: number;
  size: number;
  total: number;
  records: TaskResponse[];
}

export interface CreateTaskRequest {
  taskName: string;
  subjectCode: string;
  actionName: string;
  deviceType: string;
  modality: string;
  collectDate: string;
  scene?: string;
  operatorName?: string;
  captureLocation?: string;
  remark: string;
}
