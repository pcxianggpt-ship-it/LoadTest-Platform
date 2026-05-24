import { http, type ApiResponse } from "./http";

export interface Project {
  id: number;
  name: string;
  description?: string;
  environmentName: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface ProjectPayload {
  name: string;
  description?: string;
  environmentName: string;
}

export async function listProjects() {
  const response = await http.get<ApiResponse<Project[]>>("/api/projects");
  return response.data.data;
}

export async function createProject(payload: ProjectPayload) {
  const response = await http.post<ApiResponse<Project>>("/api/projects", payload);
  return response.data.data;
}

export async function getProject(projectId: number) {
  const response = await http.get<ApiResponse<Project>>(`/api/projects/${projectId}`);
  return response.data.data;
}

export async function deleteProject(projectId: number) {
  await http.delete<ApiResponse<null>>(`/api/projects/${projectId}`);
}
