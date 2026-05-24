<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const activeMenu = computed(() => {
  if (route.path.startsWith("/tasks")) {
    return "/tasks";
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
</script>

<template>
  <el-container class="app-shell">
    <el-aside width="220px" class="sidebar">
      <div class="brand">
        <span class="brand-mark">LT</span>
        <div>
          <strong>压力测试平台</strong>
          <small>MVP 工作台</small>
        </div>
      </div>
      <el-menu router :default-active="activeMenu" class="nav-menu">
        <el-menu-item index="/">
          <span>项目管理</span>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <span>任务管理</span>
        </el-menu-item>
        <el-menu-item index="/executions">
          <span>执行管理</span>
        </el-menu-item>
        <el-menu-item index="/results">
          <span>结果归档分析</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <span>报告生成</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div>
          <h1>压力测试平台</h1>
          <p>项目、任务、执行、结果、报告的最小闭环</p>
        </div>
        <el-tag type="success" effect="plain">Spring Boot API</el-tag>
      </el-header>

      <el-main class="content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>
