import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import * as esbuild from "esbuild";

const { outputFiles } = await esbuild.build({
  entryPoints: [fileURLToPath(new URL("../src/stores/skin.ts", import.meta.url))],
  bundle: true,
  format: "esm",
  platform: "node",
  write: false,
});
const { currentSkinId, initializeSkin, setSkin, skins } = await import(
  `data:text/javascript;base64,${Buffer.from(outputFiles[0].contents).toString("base64")}`
);

const storage = new Map();
globalThis.window = {
  localStorage: {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
  },
};
globalThis.document = { documentElement: { dataset: {} } };

initializeSkin();
assert.equal(currentSkinId.value, "cursor", "首次访问应默认使用皮肤1");
assert.equal(document.documentElement.dataset.skin, "cursor");
assert.equal(skins.find((skin) => skin.id === "cursor").label, "皮肤1 · Cursor 风格");

setSkin("nvidia");
assert.equal(currentSkinId.value, "nvidia");
assert.equal(document.documentElement.dataset.skin, "nvidia");
assert.equal(storage.get("loadtest.skin"), "nvidia");
assert.equal(skins.find((skin) => skin.id === "nvidia").label, "皮肤2 · NVIDIA 风格");

setSkin("cursor");
storage.set("loadtest.skin", "nvidia");
initializeSkin();
assert.equal(currentSkinId.value, "nvidia", "初始化应恢复已保存的皮肤2");

setSkin("tesla");
assert.equal(currentSkinId.value, "tesla");
assert.equal(document.documentElement.dataset.skin, "tesla");
assert.equal(storage.get("loadtest.skin"), "tesla");
assert.equal(skins.find((skin) => skin.id === "tesla").label, "皮肤3 · Tesla 风格");

setSkin("cursor");
storage.set("loadtest.skin", "tesla");
initializeSkin();
assert.equal(currentSkinId.value, "tesla", "初始化应恢复已保存的皮肤3");

setSkin("classic");
assert.equal(currentSkinId.value, "classic");
assert.equal(document.documentElement.dataset.skin, "classic");
assert.equal(storage.get("loadtest.skin"), "classic", "切换皮肤后应保存选择");

storage.set("loadtest.skin", "cursor");
initializeSkin();
storage.set("loadtest.skin", "classic");
initializeSkin();
assert.equal(currentSkinId.value, "classic", "初始化应恢复已保存的皮肤");

setSkin("unsupported");
assert.equal(currentSkinId.value, "classic", "无效的皮肤切换不应改变当前皮肤");
assert.equal(storage.get("loadtest.skin"), "classic");

storage.set("loadtest.skin", "unsupported");
initializeSkin();
assert.equal(currentSkinId.value, "cursor", "已保存皮肤不存在时应回退到皮肤1");

window.localStorage.getItem = () => { throw new Error("storage unavailable"); };
window.localStorage.setItem = () => { throw new Error("storage unavailable"); };
assert.doesNotThrow(initializeSkin, "存储不可用时应正常启动");
assert.equal(currentSkinId.value, "cursor");
assert.doesNotThrow(() => setSkin("classic"), "存储不可用时仍应支持当前页面换肤");
assert.equal(document.documentElement.dataset.skin, "classic");
assert.doesNotThrow(() => setSkin("nvidia"), "存储不可用时仍应支持皮肤2");
assert.equal(document.documentElement.dataset.skin, "nvidia");
assert.doesNotThrow(() => setSkin("tesla"), "存储不可用时仍应支持皮肤3");
assert.equal(document.documentElement.dataset.skin, "tesla");
