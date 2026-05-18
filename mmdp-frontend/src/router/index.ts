import { createRouter, createWebHistory } from "vue-router";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      component: () => import("@/layouts/AppLayout.vue"),
      children: [
        {
          path: "",
          redirect: "/tasks"
        },
        {
          path: "tasks",
          name: "task-list",
          component: () => import("@/views/tasks/TaskListView.vue"),
          meta: {
            title: "采集任务",
            description: "统一管理数据采集任务、执行进度与后续质检流转。"
          }
        },
        {
          path: "tasks/new",
          name: "task-new",
          component: () => import("@/views/tasks/TaskCreateView.vue"),
          meta: {
            title: "新建采集任务",
            description: "录入任务元数据并创建新的数据采集工作项。"
          }
        },
        {
          path: "tasks/:taskId",
          name: "task-detail",
          component: () => import("@/views/tasks/TaskDetailView.vue"),
          meta: {
            title: "任务详情",
            description: "查看任务摘要、上传原始文件并进入质检报告。"
          }
        },
        {
          path: "tasks/:taskId/qc-report",
          name: "task-qc-report",
          component: () => import("@/views/reports/QcReportView.vue"),
          meta: {
            title: "质检报告",
            description: "浏览结构化质检结论、告警信息和原始报告内容。"
          }
        },
        {
          path: "placeholder/:module",
          name: "placeholder",
          component: () => import("@/views/placeholder/PlaceholderView.vue"),
          meta: {
            title: "模块规划",
            description: "预留的数据平台模块入口，后续可按业务节奏继续扩展。"
          }
        }
      ]
    }
  ]
});

export default router;
