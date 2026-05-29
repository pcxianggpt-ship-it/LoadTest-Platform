import { http, type ApiResponse } from "./http";

export type BusinessDatabaseType = "mysql" | "oracle";
export type BusinessDatabaseStatus = "active" | "inactive";

export interface BusinessDatabase {
  id: number;
  projectId: number;
  name: string;
  databaseType: BusinessDatabaseType;
  jdbcUrl: string;
  username?: string;
  passwordEncrypted?: string;
  status: BusinessDatabaseStatus;
  createdAt: string;
  updatedAt: string;
}

export interface BusinessDatabasePayload {
  name: string;
  databaseType: BusinessDatabaseType;
  jdbcUrl: string;
  username?: string;
  passwordEncrypted?: string;
  status?: BusinessDatabaseStatus;
}

export interface CleanupPlan {
  id: number;
  projectId: number;
  businessDatabaseId: number;
  name: string;
  description?: string;
  enabled: boolean;
  status: string;
  sqlStatements: string[];
  createdAt: string;
  updatedAt: string;
}

export interface CleanupPlanPayload {
  name: string;
  description?: string;
  businessDatabaseId: number;
  enabled?: boolean;
  sqlStatements: string[];
}

export async function listBusinessDatabases(projectId: number) {
  const response = await http.get<ApiResponse<BusinessDatabase[]>>(`/api/projects/${projectId}/business-databases`);
  return response.data.data;
}

export async function createBusinessDatabase(projectId: number, payload: BusinessDatabasePayload) {
  const response = await http.post<ApiResponse<BusinessDatabase>>(`/api/projects/${projectId}/business-databases`, payload);
  return response.data.data;
}

export async function updateBusinessDatabase(projectId: number, databaseId: number, payload: BusinessDatabasePayload) {
  const response = await http.put<ApiResponse<BusinessDatabase>>(
    `/api/projects/${projectId}/business-databases/${databaseId}`,
    payload
  );
  return response.data.data;
}

export async function deleteBusinessDatabase(projectId: number, databaseId: number) {
  await http.delete<ApiResponse<null>>(`/api/projects/${projectId}/business-databases/${databaseId}`);
}

export async function listCleanupPlans(projectId: number) {
  const response = await http.get<ApiResponse<CleanupPlan[]>>(`/api/projects/${projectId}/cleanup-plans`);
  return response.data.data;
}

export async function createCleanupPlan(projectId: number, payload: CleanupPlanPayload) {
  const response = await http.post<ApiResponse<CleanupPlan>>(`/api/projects/${projectId}/cleanup-plans`, payload);
  return response.data.data;
}

export async function updateCleanupPlan(projectId: number, planId: number, payload: CleanupPlanPayload) {
  const response = await http.put<ApiResponse<CleanupPlan>>(
    `/api/projects/${projectId}/cleanup-plans/${planId}`,
    payload
  );
  return response.data.data;
}

export async function deleteCleanupPlan(projectId: number, planId: number) {
  await http.delete<ApiResponse<null>>(`/api/projects/${projectId}/cleanup-plans/${planId}`);
}
