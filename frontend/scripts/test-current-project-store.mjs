import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const storePath = fileURLToPath(new URL("../src/stores/currentProject.ts", import.meta.url));
const taskListPath = fileURLToPath(new URL("../src/views/TaskList.vue", import.meta.url));
const executionListPath = fileURLToPath(new URL("../src/views/ExecutionList.vue", import.meta.url));
const resultArchivePath = fileURLToPath(new URL("../src/views/ResultArchive.vue", import.meta.url));
const reportListPath = fileURLToPath(new URL("../src/views/ReportList.vue", import.meta.url));
const cleanupManagerPath = fileURLToPath(new URL("../src/views/CleanupManager.vue", import.meta.url));
const projectDetailPath = fileURLToPath(new URL("../src/views/ProjectDetail.vue", import.meta.url));

const [
  storeSource,
  taskListSource,
  executionListSource,
  resultArchiveSource,
  reportListSource,
  cleanupManagerSource,
  projectDetailSource,
] = await Promise.all([
  readFile(storePath, "utf8"),
  readFile(taskListPath, "utf8"),
  readFile(executionListPath, "utf8"),
  readFile(resultArchivePath, "utf8"),
  readFile(reportListPath, "utf8"),
  readFile(cleanupManagerPath, "utf8"),
  readFile(projectDetailPath, "utf8"),
]);

assert.match(storeSource, /const STORAGE_KEY = "loadtest\.currentProjectId"/, "currentProject store must use the shared localStorage key");
assert.match(storeSource, /export function getCurrentProjectId\(\)/, "currentProject store must expose getCurrentProjectId");
assert.match(storeSource, /export function setCurrentProjectId\(projectId\?: number\)/, "currentProject store must expose setCurrentProjectId");
assert.match(storeSource, /export function resolveProjectId\(projects: Project\[\], routeProjectId\?: number\)/, "currentProject store must expose resolveProjectId");
assert.match(
  storeSource,
  /projects\.some\(\(project\) => project\.id === routeId\)[\s\S]*setCurrentProjectId\(routeId\)[\s\S]*return routeId/,
  "resolveProjectId must prefer a valid route project id",
);
assert.match(
  storeSource,
  /const storedId = getCurrentProjectId\(\)[\s\S]*projects\.some\(\(project\) => project\.id === storedId\)[\s\S]*return storedId/,
  "resolveProjectId must use a stored project id when it still exists",
);
assert.match(
  storeSource,
  /const fallbackId = projects\[0\]\?\.id[\s\S]*setCurrentProjectId\(fallbackId\)[\s\S]*return fallbackId/,
  "resolveProjectId must fall back to the first project and persist it",
);

for (const [name, source] of [
  ["TaskList", taskListSource],
  ["ExecutionList", executionListSource],
  ["ResultArchive", resultArchiveSource],
  ["ReportList", reportListSource],
  ["CleanupManager", cleanupManagerSource],
]) {
  assert.match(source, /resolveProjectId/, `${name} must resolve selected project through the shared store`);
  assert.match(source, /setCurrentProjectId/, `${name} must persist project changes`);
  assert.doesNotMatch(source, /selectedProjectId\.value = queryProjectId \|\| projects\.value\[0\]\?\.id/, `${name} must not fall back directly to first project`);
  assert.doesNotMatch(source, /selectedProjectId\.value = projects\.value\[0\]\?\.id/, `${name} must not always select the first project`);
}

assert.match(projectDetailSource, /setCurrentProjectId\(projectId\.value\)/, "ProjectDetail must sync the viewed project as current project");
