import { readonly, ref } from "vue";

const STORAGE_KEY = "loadtest.skin";

export const skins = [
  { id: "cursor", label: "皮肤1 · Cursor 风格" },
  { id: "nvidia", label: "皮肤2 · NVIDIA 风格" },
  { id: "tesla", label: "皮肤3 · Tesla 风格" },
  { id: "classic", label: "经典皮肤 · 蓝灰风格" },
] as const;

export type SkinId = typeof skins[number]["id"];

const selectedSkinId = ref<SkinId>("cursor");
export const currentSkinId = readonly(selectedSkinId);

export function setSkin(skinId: SkinId) {
  if (!skins.some((skin) => skin.id === skinId)) {
    return;
  }
  selectedSkinId.value = skinId;
  document.documentElement.dataset.skin = skinId;
  try {
    window.localStorage.setItem(STORAGE_KEY, skinId);
  } catch {
    return;
  }
}

export function initializeSkin() {
  let storedId: string | null;
  try {
    storedId = window.localStorage.getItem(STORAGE_KEY);
  } catch {
    setSkin("cursor");
    return;
  }
  setSkin(skins.find((skin) => skin.id === storedId)?.id || "cursor");
}
