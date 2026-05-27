import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const taskEditorPath = fileURLToPath(new URL("../src/views/TaskEditor.vue", import.meta.url));
const source = await readFile(taskEditorPath, "utf8");

assert.match(
  source,
  /form\.rampUpSeconds\s*=\s*step\?\.rampUpSeconds\s*\?\?\s*60/,
  "预热时间回填必须保留 0，只能在值为空时使用默认 60"
);

assert.doesNotMatch(
  source,
  /form\.rampUpSeconds\s*=\s*step\?\.rampUpSeconds\s*\|\|\s*60/,
  "预热时间回填不能使用 || 60，否则 0 会被覆盖成 60"
);
