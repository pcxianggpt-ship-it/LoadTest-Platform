import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const projectDetailPath = fileURLToPath(new URL("../src/views/ProjectDetail.vue", import.meta.url));
const viewSource = await readFile(projectDetailPath, "utf8");

assert.match(viewSource, /const grafanaForm = reactive\(\{/, "ProjectDetail must define grafanaForm state");
assert.match(viewSource, /name:\s*"grafana"/, "grafanaForm must default name to grafana");
assert.match(
  viewSource,
  /baseUrl:\s*"http:\/\/192\.168\.123\.141:3000"/,
  "grafanaForm must default to the shared Grafana URL",
);
assert.match(viewSource, /username:\s*"admin"/, "grafanaForm must default username to admin");
assert.match(viewSource, /passwordEncrypted:\s*""/, "grafanaForm must include editable password field");
assert.match(viewSource, /tokenEncrypted:\s*""/, "grafanaForm must include editable token field");
assert.match(viewSource, /"orgId": 1[\s\S]*"theme": "light"[\s\S]*"width": 1200[\s\S]*"height": 700[\s\S]*"panels": \[\]/, "grafanaForm must include pretty JSON defaults");

assert.match(
  viewSource,
  /<el-tab-pane label="Grafana" name="grafana">[\s\S]*?v-model="grafanaForm\.passwordEncrypted"[\s\S]*?v-model="grafanaForm\.tokenEncrypted"[\s\S]*?<\/el-tab-pane>/,
  "the Grafana tab must expose both credential inputs",
);
