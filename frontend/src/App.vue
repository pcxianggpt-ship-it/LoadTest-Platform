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
      <el-menu router default-active="/" class="nav-menu">
        <el-menu-item index="/">
          <span>项目管理</span>
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
