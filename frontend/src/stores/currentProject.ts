import type { Project } from "../api/projects";

const STORAGE_KEY = "loadtest.currentProjectId";

export function getCurrentProjectId() {
  const rawValue = window.localStorage.getItem(STORAGE_KEY);
  const value = Number(rawValue);
  return Number.isFinite(value) && value > 0 ? value : undefined;
}

export function setCurrentProjectId(projectId?: number) {
  if (!projectId) {
    window.localStorage.removeItem(STORAGE_KEY);
    return;
  }
  window.localStorage.setItem(STORAGE_KEY, String(projectId));
}

export function resolveProjectId(projects: Project[], routeProjectId?: number) {
  const routeId = Number(routeProjectId);
  if (Number.isFinite(routeId) && projects.some((project) => project.id === routeId)) {
    setCurrentProjectId(routeId);
    return routeId;
  }
  const storedId = getCurrentProjectId();
  if (storedId && projects.some((project) => project.id === storedId)) {
    return storedId;
  }
  const fallbackId = projects[0]?.id;
  setCurrentProjectId(fallbackId);
  return fallbackId;
}
