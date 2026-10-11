<script setup lang="ts">
import { computed } from "vue";

const props = defineProps<{
  status?: string;
}>();

const statusMap: Record<string, { label: string; type: "success" | "warning" | "danger" | "info" | "primary" }> = {
  active: { label: "启用", type: "success" },
  inactive: { label: "停用", type: "info" },
  none: { label: "未清理", type: "info" },
  skipped: { label: "已跳过", type: "info" },
  pending: { label: "等待中", type: "info" },
  scheduled: { label: "已定时", type: "info" },
  running: { label: "运行中", type: "warning" },
  success: { label: "成功", type: "success" },
  partial_success: { label: "部分成功", type: "warning" },
  failed: { label: "失败", type: "danger" },
  cancelled: { label: "已取消", type: "info" },
  warning: { label: "警告", type: "warning" },
  critical: { label: "严重", type: "danger" },
  normal: { label: "正常", type: "success" },
};

const view = computed(() => statusMap[props.status || ""] || { label: props.status || "未知", type: "info" as const });
</script>

<template>
  <el-tag :type="view.type" effect="light" round class="status-tag">
    <span class="status-dot" aria-hidden="true"></span>{{ view.label }}
  </el-tag>
</template>
