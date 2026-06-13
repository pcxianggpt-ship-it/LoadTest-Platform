import { http, type ApiResponse } from "./http";

export interface ResultMetric {
  id: number;
  resultId: number;
  source: string;
  metricCategory: string;
  metricName: string;
  targetName: string;
  statType: string;
  value: number;
  unit?: string;
  thresholdValue?: number;
  thresholdStatus?: string;
  extraTagsJson?: string;
  createdAt: string;
}

export interface TestResult {
  id: number;
  projectId: number;
  executionId: number;
  name: string;
  status: string;
  timeRangeStart: string;
  timeRangeEnd: string;
  summaryJson?: string;
  analysisJson?: string;
  createdBy?: string;
  createdAt: string;
  updatedAt: string;
  metrics: ResultMetric[];
  images: ResultImage[];
}

export interface ResultImage {
  id: number;
  resultId: number;
  projectId: number;
  imageType: string;
  title: string;
  dashboardUid: string;
  dashboardSlug?: string;
  panelId: number;
  grafanaUrl: string;
  downloadUrl: string;
  contentType: string;
  fileSize: number;
  width: number;
  height: number;
  createdAt: string;
}

export interface GenerateResultPayload {
  name: string;
}

export async function generateResult(executionId: number, payload: GenerateResultPayload) {
  const response = await http.post<ApiResponse<TestResult>>(`/api/executions/${executionId}/results`, payload);
  return response.data.data;
}

export async function listResults(projectId: number) {
  const response = await http.get<ApiResponse<TestResult[]>>(`/api/projects/${projectId}/results`);
  return response.data.data;
}

export async function getResult(resultId: number) {
  const response = await http.get<ApiResponse<TestResult>>(`/api/results/${resultId}`);
  return response.data.data;
}

export async function deleteResult(resultId: number) {
  await http.delete<ApiResponse<null>>(`/api/results/${resultId}`);
}

export interface ExportGrafanaImagePayload {
  title?: string;
  dashboardUid?: string;
  dashboardSlug?: string;
  panelId: number;
  orgId?: number;
  width?: number;
  height?: number;
  theme?: string;
}

export async function exportGrafanaImage(resultId: number, payload: ExportGrafanaImagePayload) {
  const response = await http.post<ApiResponse<ResultImage>>(`/api/results/${resultId}/grafana-images`, payload);
  return response.data.data;
}
