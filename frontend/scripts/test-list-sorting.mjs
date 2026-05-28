import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

async function readView(name) {
  return readFile(fileURLToPath(new URL(`../src/views/${name}.vue`, import.meta.url)), "utf8");
}

const taskList = await readView("TaskList");
const executionList = await readView("ExecutionList");
const resultArchive = await readView("ResultArchive");

assert.match(taskList, /default-sort="\{ prop: 'name', order: 'ascending' \}"/, "任务管理默认按任务名称正序");
assert.match(taskList, /compareTaskNameThenThreads/, "任务名称默认排序必须在名称相同时按并发数排序");
assert.match(taskList, /prop="name" label="任务名称"[^>]*sortable[^>]*:sort-method="compareTaskNameThenThreads"/, "任务名称列必须支持名称和并发数组合排序");
assert.match(taskList, /label="更新时间"[^>]*sortable/, "任务更新时间列必须支持表头排序");

assert.match(
  executionList,
  /default-sort="\{ prop: 'sortTime', order: 'descending' \}"/,
  "执行管理默认按最近执行或计划时间倒序"
);
assert.match(executionList, /prop="executionName" label="执行名称"[^>]*sortable/, "执行名称列必须支持表头排序");
assert.match(executionList, /:data="executionRows"/, "执行列表必须使用带排序时间的行数据");
assert.match(executionList, /prop="sortTime" label="开始时间"[^>]*sortable/, "执行开始时间列必须支持默认时间排序");

assert.match(
  resultArchive,
  /default-sort="\{ prop: 'createdAt', order: 'descending' \}"/,
  "结果归档分析默认按归档时间倒序"
);
assert.match(resultArchive, /prop="name" label="结果名称"[^>]*sortable/, "结果名称列必须支持表头排序");
assert.match(resultArchive, /prop="createdAt" label="归档时间"[^>]*sortable/, "归档时间列必须支持表头排序");
