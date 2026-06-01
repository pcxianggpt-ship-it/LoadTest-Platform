import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const executionApiPath = fileURLToPath(new URL("../src/api/executions.ts", import.meta.url));
const executionListPath = fileURLToPath(new URL("../src/views/ExecutionList.vue", import.meta.url));
const apiSource = await readFile(executionApiPath, "utf8");
const viewSource = await readFile(executionListPath, "utf8");

assert.match(apiSource, /remark\?: string/, "执行记录类型必须包含备注字段");
assert.match(apiSource, /updateExecution/, "必须提供更新执行名称和备注的 API 方法");
assert.match(apiSource, /put<ApiResponse<TestExecution>>\(`\/api\/executions\/\$\{executionId\}`/, "更新执行必须调用 PUT /api/executions/{executionId}");

assert.match(viewSource, /editDialogVisible/, "执行管理页必须包含编辑弹窗状态");
assert.match(viewSource, /openEditDialog/, "执行管理页必须提供打开编辑弹窗的方法");
assert.match(viewSource, /submitEdit/, "执行管理页必须提供保存编辑的方法");
assert.match(viewSource, /label="备注"/, "执行管理列表必须展示备注列");
assert.match(viewSource, />编辑</, "执行管理列表操作区必须提供编辑入口");
assert.match(viewSource, /v-model="editForm\.executionName"/, "编辑弹窗必须允许修改执行名称");
assert.match(viewSource, /v-model="editForm\.remark"/, "编辑弹窗必须允许修改备注");
