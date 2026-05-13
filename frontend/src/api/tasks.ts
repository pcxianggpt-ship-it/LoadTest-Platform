import { http, type ApiResponse } from "./http";

export interface TaskStep {
  id: number;
  taskId: number;
  stepOrder: number;
  stepName: string;
  jmxFile: string;
  threads: number;
  durationSeconds: number;
  rampUpSeconds: number;
  saveJtl: boolean;
  jmeterArgsJson?: string;
  enabled: boolean;
}

export interface TestTask {
  id: number;
  projectId: number;
  name: string;
  description?: string;
  defaultSaveJtl: boolean;
  status: string;
  createdAt: string;
  updatedAt: string;
  steps: TaskStep[];
}

export interface TaskPayload {
  name: string;
  description?: string;
  defaultSaveJtl?: boolean;
  step: {
    stepName: string;
    jmxFile: string;
    threads: number;
    durationSeconds: number;
    rampUpSeconds: number;
    saveJtl?: boolean;
    jmeterArgsJson?: string;
  };
}

export async function listTasks(projectId: number) {
  const response = await http.get<ApiResponse<TestTask[]>>(`/api/projects/${projectId}/tasks`);
  return response.data.data;
}

export async function createTask(projectId: number, payload: TaskPayload) {
  const response = await http.post<ApiResponse<TestTask>>(`/api/projects/${projectId}/tasks`, payload);
  return response.data.data;
}
