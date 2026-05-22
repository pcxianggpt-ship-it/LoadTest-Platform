import axios from "axios";

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
}

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  timeout: 15000,
});
