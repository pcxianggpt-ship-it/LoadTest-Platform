import { createRouter, createWebHistory } from "vue-router";
import ExecutionList from "./views/ExecutionList.vue";
import ProjectDetail from "./views/ProjectDetail.vue";
import ProjectList from "./views/ProjectList.vue";
import ReportDetail from "./views/ReportDetail.vue";
import ResultDetail from "./views/ResultDetail.vue";
import TaskEditor from "./views/TaskEditor.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "projects",
      component: ProjectList,
    },
    {
      path: "/projects/:projectId",
      name: "project-detail",
      component: ProjectDetail,
    },
    {
      path: "/projects/:projectId/tasks/new",
      name: "task-editor",
      component: TaskEditor,
    },
    {
      path: "/projects/:projectId/executions",
      name: "execution-list",
      component: ExecutionList,
    },
    {
      path: "/results/:resultId",
      name: "result-detail",
      component: ResultDetail,
    },
    {
      path: "/reports/:reportId",
      name: "report-detail",
      component: ReportDetail,
    },
  ],
});
