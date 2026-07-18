import { createRouter, createWebHistory } from "vue-router";
import { useAuthStore } from "@/stores/auth";

declare module "vue-router" {
  interface RouteMeta {
    title?: string;
    description?: string;
    fullscreen?: boolean;
    requiresAuth?: boolean;
    requiresAdmin?: boolean;
    hideForAuthenticated?: boolean;
  }
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      name: "login",
      component: () => import("@/views/auth/LoginView.vue"),
      meta: {
        title: "登录",
        hideForAuthenticated: true,
      },
    },
    {
      path: "/play/:sessionId",
      name: "session-playback",
      component: () => import("@/views/playback/PlaybackView.vue"),
      meta: { title: "数据回放", fullscreen: true, requiresAuth: true },
    },
    {
      path: "/motion-view/:sessionId",
      name: "motion-viewer",
      component: () => import("@/views/motion/MotionViewer.vue"),
      meta: { title: "3D 动作查看", fullscreen: true, requiresAuth: true },
    },
    {
      path: "/acquisition/:taskId/dag",
      name: "execution-graph",
      component: () => import("@/views/lineage/ExecutionGraphView.vue"),
      meta: { title: "数据链路 DAG", fullscreen: true, requiresAuth: true },
    },
    {
      path: "/",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { requiresAuth: true },
      children: [
        { path: "", redirect: "/home" },
        {
          path: "home",
          name: "home",
          component: () => import("@/views/home/HomeOverviewView.vue"),
          meta: { title: "平台概览" },
        },
        { path: "data", redirect: "/sessions" },
        {
          path: "data/:assetId",
          name: "data-asset-detail",
          component: () => import("@/views/data/DataAssetDetailView.vue"),
          meta: { title: "资产详情" },
        },
        {
          path: "upload",
          name: "upload",
          component: () => import("@/views/upload/UploadEntryView.vue"),
          meta: { title: "数据资产接入" },
        },
        {
          path: "acquisition",
          name: "acquisition-list",
          component: () => import("@/views/acquisition/AcquisitionListView.vue"),
          meta: { title: "任务" },
        },
        {
          path: "acquisition/new",
          name: "acquisition-new",
          component: () => import("@/views/acquisition/AcquisitionCreateView.vue"),
          meta: { title: "新建任务" },
        },
        {
          path: "acquisition/:taskId",
          name: "acquisition-detail",
          component: () => import("@/views/acquisition/AcquisitionDetailView.vue"),
          meta: { title: "任务详情" },
        },
        {
          path: "sessions",
          name: "session-list",
          component: () => import("@/views/sessions/SessionListView.vue"),
          meta: { title: "采集" },
        },
        {
          path: "sessions/:sessionId",
          name: "session-detail",
          component: () => import("@/views/sessions/SessionDetailView.vue"),
          meta: { title: "采集详情" },
        },
        {
          path: "processing",
          name: "processing",
          component: () => import("@/views/processing/ProcessingTemplateView.vue"),
          meta: { title: "处理" },
        },
        {
          path: "annotation",
          name: "annotation",
          component: () => import("@/views/annotation/AnnotationTaskView.vue"),
          meta: { title: "标注" },
        },
        {
          path: "qc",
          name: "qc",
          component: () => import("@/views/qc/QcWorkspaceView.vue"),
          meta: { title: "质检" },
        },
        {
          path: "export",
          name: "export",
          component: () => import("@/views/export/FinalAssetView.vue"),
          meta: { title: "导出" },
        },
        {
          path: "collector",
          name: "collector-prototype",
          component: () => import("@/views/collector/CollectorPrototypeView.vue"),
          meta: { title: "采集原型" },
        },
        {
          path: "management/users",
          name: "management-users",
          component: () => import("@/views/management/UserManagementView.vue"),
          meta: { title: "用户管理", requiresAdmin: true },
        },
        {
          path: "management/profiles",
          name: "management-profiles",
          component: () => import("@/views/management/ProfileManagementView.vue"),
          meta: { title: "Profile 管理", requiresAdmin: true },
        },
        {
          path: "management/:module",
          name: "management-module",
          component: () => import("@/views/management/ManagementModuleView.vue"),
          meta: { title: "管理模块", requiresAdmin: true },
        },
        {
          path: "tools/session-organizer",
          name: "session-organizer",
          component: () => import("@/views/tools/SessionOrganizerView.vue"),
          meta: { title: "Session 整理工具" },
        },
        { path: "tasks", redirect: "/acquisition" },
        { path: "tasks/new", redirect: "/acquisition/new" },
        { path: "tasks/:taskId", redirect: (to) => `/acquisition/${to.params.taskId}` },
        { path: "tasks/:taskId/qc-report", redirect: (to) => `/qc?taskId=${to.params.taskId}` },
      ],
    },
  ],
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  await authStore.initialize();

  if (to.meta.hideForAuthenticated && authStore.isAuthenticated.value) {
    return "/";
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated.value) {
    return {
      path: "/login",
      query: { redirect: to.fullPath },
    };
  }

  if (to.meta.requiresAdmin && !authStore.isAdmin.value) {
    return "/";
  }

  return true;
});

export default router;
