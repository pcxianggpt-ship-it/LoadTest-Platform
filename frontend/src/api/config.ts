import { http, type ApiResponse } from "./http";

export interface JMeterServer {
  id: number;
  projectId: number;
  name: string;
  host: string;
  sshPort: number;
  sshUsername: string;
  sshAuthType: "password" | "private_key";
  jmeterHome: string;
  scriptDir: string;
  resultDir?: string;
  logDir: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface JMeterServerPayload {
  name: string;
  host: string;
  sshPort: number;
  sshUsername: string;
  sshAuthType: "password" | "private_key";
  sshPasswordEncrypted?: string;
  sshPrivateKeyEncrypted?: string;
  jmeterHome: string;
  scriptDir: string;
  resultDir?: string;
  logDir: string;
}

export interface Datasource {
  id: number;
  projectId: number;
  type: string;
  name: string;
  baseUrl: string;
  databaseName?: string;
  username?: string;
  extraConfigJson?: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

export interface DatasourcePayload {
  name: string;
  baseUrl: string;
  databaseName?: string;
  username?: string;
  passwordEncrypted?: string;
  tokenEncrypted?: string;
  extraConfigJson?: string;
}

export async function getJMeterServer(projectId: number) {
  const response = await http.get<ApiResponse<JMeterServer>>(`/api/projects/${projectId}/jmeter-server`);
  return response.data.data;
}

export async function upsertJMeterServer(projectId: number, payload: JMeterServerPayload) {
  const response = await http.put<ApiResponse<JMeterServer>>(`/api/projects/${projectId}/jmeter-server`, payload);
  return response.data.data;
}

export async function listDatasources(projectId: number) {
  const response = await http.get<ApiResponse<Datasource[]>>(`/api/projects/${projectId}/datasources`);
  return response.data.data;
}

export async function upsertDatasource(projectId: number, datasourceType: string, payload: DatasourcePayload) {
  const response = await http.put<ApiResponse<Datasource>>(
    `/api/projects/${projectId}/datasources/${datasourceType}`,
    payload,
  );
  return response.data.data;
}
