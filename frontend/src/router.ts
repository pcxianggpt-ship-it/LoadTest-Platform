import { createRouter, createWebHistory } from "vue-router";

const ProjectList = {
  template: `
    <section class="workspace-panel">
      <div class="section-heading">
        <h2>项目</h2>
        <p>前端骨架已就绪，下一步会接入项目、任务、执行、结果和报告页面。</p>
      </div>
    </section>
  `,
};

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      name: "projects",
      component: ProjectList,
    },
  ],
});
