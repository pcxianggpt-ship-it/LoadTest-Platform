import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const executionListPath = fileURLToPath(new URL("../src/views/ExecutionList.vue", import.meta.url));
const source = await readFile(executionListPath, "utf8");

assert.match(source, /filterable/, "执行管理页的计划下拉框必须支持输入搜索");
assert.match(source, /:filter-method="filterTasks"/, "计划搜索必须使用自定义过滤，覆盖名称、描述和 JMX 文件");
assert.match(source, /filteredTasks/, "计划下拉框必须渲染过滤后的计划列表");
assert.match(source, /task-option-title/, "计划选项必须展示更容易识别的标题区域");
assert.match(source, /task-option-meta/, "计划选项必须展示 JMX、并发数和持续时间等辅助信息");
assert.match(source, /item\.steps\?\.\[0\]\?\.jmxFile/, "计划搜索和展示必须包含首个 JMX 文件名");
