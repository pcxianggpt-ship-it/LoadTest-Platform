import { createRouter, createWebHistory } from "vue-router";
import ExecutionList from "./views/ExecutionList.vue";
import ProjectDetail from "./views/ProjectDetail.vue";
import ProjectList from "./views/ProjectList.vue";
import ReportList from "./views/ReportList.vue";
import ReportDetail from "./views/ReportDetail.vue";
import ResultArchive from "./views/ResultArchive.vue";
import ResultDetail from "./views/ResultDetail.vue";
import TaskEditor from "./views/TaskEditor.vue";
import TaskList from "./views/TaskList.vue";

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
      path: "/tasks",
      name: "task-list",
      component: TaskList,
    },
    {
      path: "/tasks/new",
      name: "task-editor-standalone",
      component: TaskEditor,
    },
    {
      path: "/tasks/:taskId/edit",
      name: "task-editor-edit",
      component: TaskEditor,
    },
    {
      path: "/projects/:projectId/executions",
      name: "execution-list",
      component: ExecutionList,
    },
    {
      path: "/executions",
      name: "execution-list-standalone",
      component: ExecutionList,
    },
    {
      path: "/results",
      name: "result-archive",
      component: ResultArchive,
    },
    {
      path: "/results/:resultId",
      name: "result-detail",
      component: ResultDetail,
    },
    {
      path: "/reports",
      name: "report-list",
      component: ReportList,
    },
    {
      path: "/reports/:reportId",
      name: "report-detail",
      component: ReportDetail,
    },
  ],
});
