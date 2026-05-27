import { http, type ApiResponse } from "./http";

export interface TestExecution {
  id: number;
  projectId: number;
  taskId: number;
  executionName: string;
  triggerType: string;
  scheduledAt?: string;
  status: string;
  startedAt?: string;
  endedAt?: string;
  durationSeconds?: number;
  currentStepOrder?: number;
  errorMessage?: string;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
}

export interface ScheduledExecutionPayload {
  scheduledAt: string;
}

export async function createManualExecution(taskId: number) {
  const response = await http.post<ApiResponse<TestExecution>>(`/api/tasks/${taskId}/executions/manual`);
  return response.data.data;
}

export async function createScheduledExecution(taskId: number, payload: ScheduledExecutionPayload) {
  const response = await http.post<ApiResponse<TestExecution>>(`/api/tasks/${taskId}/executions/scheduled`, payload);
  return response.data.data;
}

export async function cancelExecution(executionId: number) {
  const response = await http.post<ApiResponse<TestExecution>>(`/api/executions/${executionId}/cancel`);
  return response.data.data;
}

export async function stopExecution(executionId: number) {
  const response = await http.post<ApiResponse<TestExecution>>(`/api/executions/${executionId}/stop`);
  return response.data.data;
}

export async function listExecutions(projectId: number) {
  const response = await http.get<ApiResponse<TestExecution[]>>(`/api/projects/${projectId}/executions`);
  return response.data.data;
}

export async function deleteExecution(executionId: number) {
  await http.delete<ApiResponse<null>>(`/api/executions/${executionId}`);
}
