<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { currentSkinId, setSkin, skins } from "./stores/skin";

const route = useRoute();
const navigationOpen = ref(false);
const currentSkin = computed(() => skins.find((skin) => skin.id === currentSkinId.value) || skins[0]);
const navigation = [
  { path: "/", label: "项目管理", icon: "M3 7h6l2-3h10v16H3V7Z" },
  { path: "/tasks", label: "任务管理", icon: "M9 5h12M9 12h12M9 19h12M3 5h1M3 12h1M3 19h1" },
  { path: "/cleanup", label: "数据清理", icon: "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5" },
  { path: "/executions", label: "执行管理", icon: "m9 5 11 7-11 7V5Z" },
  { path: "/results", label: "结果归档分析", icon: "M4 20V4M4 20h16M8 16v-5M13 16V7M18 16v-8" },
  { path: "/reports", label: "报告生成", icon: "M14 3H5v18h14V8l-5-5ZM14 3v5h5M9 12h6M9 16h6" },
];
const activeMenu = computed(() => {
  if (route.path.startsWith("/tasks") || route.path.includes("/tasks/")) {
    return "/tasks";
  }
  if (route.path.startsWith("/cleanup")) {
    return "/cleanup";
  }
  if (route.path.startsWith("/executions") || route.path.includes("/executions")) {
    return "/executions";
  }
  if (route.path.startsWith("/results")) {
    return "/results";
  }
  if (route.path.startsWith("/reports")) {
    return "/reports";
  }
  return "/";
});
const currentSection = computed(() => navigation.find((item) => item.path === activeMenu.value)?.label);

watch(() => route.fullPath, () => {
  navigationOpen.value = false;
});
</script>

<template>
  <a class="skip-link" href="#main-content">跳转到主要内容</a>
  <el-container class="app-shell">
    <el-aside width="224px" class="sidebar">
      <router-link to="/" class="brand" aria-label="压力测试平台首页">
        <span class="brand-mark" aria-hidden="true">LT<span>.</span></span>
        <div>
          <strong>LoadTest</strong>
          <small>压力测试平台</small>
        </div>
      </router-link>
      <p class="nav-label">工作空间</p>
      <nav class="nav-menu" aria-label="主导航">
        <router-link
          v-for="item in navigation"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ 'is-active': activeMenu === item.path }"
          :aria-current="activeMenu === item.path ? 'page' : undefined"
        >
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path :d="item.icon" />
          </svg>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
      <div class="sidebar-footer"><span>LoadTest Platform</span><small>配置 · 执行 · 分析</small></div>
    </el-aside>

    <el-container class="workspace">
      <el-header class="topbar">
        <div class="topbar-location">
          <button
            class="icon-button navigation-toggle"
            type="button"
            aria-label="打开导航菜单"
            aria-controls="mobile-navigation"
            :aria-expanded="navigationOpen"
            @click="navigationOpen = true"
          >
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" /></svg>
          </button>
          <span class="topbar-workspace">工作台</span>
          <span class="breadcrumb-divider" aria-hidden="true">/</span>
          <span>{{ currentSection }}</span>
        </div>
        <div class="topbar-actions">
          <span class="topbar-caption">压力测试工作台</span>
          <el-dropdown trigger="click" @command="setSkin">
            <el-button class="skin-switcher" :aria-label="`换肤，当前：${currentSkin.label}`" :title="`换肤，当前：${currentSkin.label}`">
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M12 3a9 9 0 1 0 0 18h1a2 2 0 0 0 1-3.7 1.6 1.6 0 0 1 .8-3H17a4 4 0 0 0 4-4C21 6.5 17 3 12 3Z" />
                <circle cx="7.5" cy="10" r="1" /><circle cx="10.5" cy="6.5" r="1" /><circle cx="15" cy="7" r="1" />
              </svg>
              <span class="skin-label">{{ currentSkin.label }}</span>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu class="skin-menu">
                <el-dropdown-item v-for="skin in skins" :key="skin.id" :command="skin.id">
                  <span>{{ skin.label }}</span>
                  <span v-if="skin.id === currentSkinId" class="skin-selected">（当前）</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main id="main-content" class="content" tabindex="-1">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <el-drawer v-model="navigationOpen" direction="ltr" size="280px" :with-header="false" class="navigation-drawer" aria-label="主导航">
    <div id="mobile-navigation" class="mobile-navigation">
      <div class="mobile-brand">
        <span class="brand-mark" aria-hidden="true">LT<span>.</span></span>
        <strong>LoadTest</strong>
        <button class="icon-button" type="button" aria-label="关闭导航菜单" @click="navigationOpen = false">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg>
        </button>
      </div>
      <p class="nav-label">工作空间</p>
      <nav class="nav-menu" aria-label="移动端主导航">
        <router-link
          v-for="item in navigation"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ 'is-active': activeMenu === item.path }"
          :aria-current="activeMenu === item.path ? 'page' : undefined"
          @click="navigationOpen = false"
        >
          <svg class="nav-icon" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path :d="item.icon" /></svg>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>
    </div>
  </el-drawer>
</template>
