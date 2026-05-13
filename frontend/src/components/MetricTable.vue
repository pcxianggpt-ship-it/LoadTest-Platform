<script setup lang="ts">
import type { ResultMetric } from "../api/results";
import StatusTag from "./StatusTag.vue";

defineProps<{
  metrics: ResultMetric[];
}>();
</script>

<template>
  <el-table :data="metrics" border stripe class="metric-table">
    <el-table-column prop="metricCategory" label="类别" min-width="110" />
    <el-table-column prop="metricName" label="指标" min-width="140" />
    <el-table-column prop="targetName" label="对象" min-width="150" />
    <el-table-column prop="statType" label="统计" width="90" />
    <el-table-column label="数值" min-width="120">
      <template #default="{ row }">
        {{ row.value }} {{ row.unit || "" }}
      </template>
    </el-table-column>
    <el-table-column label="阈值" width="110">
      <template #default="{ row }">
        {{ row.thresholdValue ?? "-" }}
      </template>
    </el-table-column>
    <el-table-column label="状态" width="120">
      <template #default="{ row }">
        <StatusTag :status="row.thresholdStatus || 'normal'" />
      </template>
    </el-table-column>
  </el-table>
</template>
