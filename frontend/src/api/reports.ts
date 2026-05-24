import { http, type ApiResponse } from "./http";

export interface TestReport {
  id: number;
  projectId: number;
  title: string;
  reportType: string;
  status: string;
  contentMarkdown: string;
  contentHtml: string;
  resultIdsJson: string;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
}

export async function generateReport(resultId: number) {
  const response = await http.post<ApiResponse<TestReport>>(`/api/results/${resultId}/reports`);
  return response.data.data;
}

export async function listReports(projectId: number) {
  const response = await http.get<ApiResponse<TestReport[]>>(`/api/projects/${projectId}/reports`);
  return response.data.data;
}

export async function getReport(reportId: number) {
  const response = await http.get<ApiResponse<TestReport>>(`/api/reports/${reportId}`);
  return response.data.data;
}

export async function deleteReport(reportId: number) {
  await http.delete<ApiResponse<null>>(`/api/reports/${reportId}`);
}
