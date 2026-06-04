import { createRouter, createWebHistory } from "vue-router";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/play/:sessionId",
      name: "session-playback",
      component: () => import("@/views/playback/PlaybackView.vue"),
      meta: { title: "数据回放", fullscreen: true },
    },
    {
      path: "/",
      component: () => import("@/layouts/AppLayout.vue"),
      children: [
        { path: "", redirect: "/home" },
        {
          path: "home",
          name: "home",
          component: () => import("@/views/home/HomeOverviewView.vue"),
          meta: { title: "平台概览", description: "围绕任务、采集、质检和导出的平台总览工作台。" },
        },
        {
          path: "data",
          redirect: "/sessions",
        },
        {
          path: "data/:assetId",
          name: "data-asset-detail",
          component: () => import("@/views/data/DataAssetDetailView.vue"),
          meta: { title: "资产详情", description: "查看数据资产的基础信息、关联链路与质检结果。" },
        },
        {
          path: "upload",
          name: "upload",
          component: () => import("@/views/upload/UploadEntryView.vue"),
          meta: { title: "上传", description: "针对任务上传数据，兼容当前任务级上传接口。" },
        },
        {
          path: "acquisition",
          name: "acquisition-list",
          component: () => import("@/views/acquisition/AcquisitionListView.vue"),
          meta: { title: "任务", description: "查看和管理采集任务。" },
        },
        {
          path: "acquisition/new",
          name: "acquisition-new",
          component: () => import("@/views/acquisition/AcquisitionCreateView.vue"),
          meta: { title: "新建任务", description: "创建采集任务，为后续采集会话建立业务容器。" },
        },
        {
          path: "acquisition/:taskId",
          name: "acquisition-detail",
          component: () => import("@/views/acquisition/AcquisitionDetailView.vue"),
          meta: { title: "任务详情", description: "查看任务级信息、关联采集和最近质检结果。" },
        },
        {
          path: "sessions",
          name: "session-list",
          component: () => import("@/views/sessions/SessionListView.vue"),
          meta: { title: "采集", description: "按采集会话查看数据、质检、导出和回放状态。" },
        },
        {
          path: "sessions/:sessionId",
          name: "session-detail",
          component: () => import("@/views/sessions/SessionDetailView.vue"),
          meta: { title: "采集详情", description: "查看单次采集会话下的数据、质检、导出和元信息。" },
        },
        {
          path: "processing",
          name: "processing",
          component: () => import("@/views/processing/ProcessingTemplateView.vue"),
          meta: { title: "处理", description: "查看处理模板和执行记录。" },
        },
        {
          path: "annotation",
          name: "annotation",
          component: () => import("@/views/annotation/AnnotationTaskView.vue"),
          meta: { title: "标注", description: "查看标注任务并跳转到外部标注系统。" },
        },
        {
          path: "qc",
          name: "qc",
          component: () => import("@/views/qc/QcWorkspaceView.vue"),
          meta: { title: "质检", description: "分规则和结果两个视角查看当前质检能力。" },
        },
        {
          path: "export",
          name: "export",
          component: () => import("@/views/export/FinalAssetView.vue"),
          meta: { title: "导出", description: "按采集会话组织导出明细和下载入口。" },
        },
        {
          path: "collector",
          name: "collector-prototype",
          component: () => import("@/views/collector/CollectorPrototypeView.vue"),
          meta: { title: "采集原型", description: "Collector 原型页面，用于验证采集与上传链路。" },
        },
        {
          path: "management/:module",
          name: "management-module",
          component: () => import("@/views/management/ManagementModuleView.vue"),
          meta: { title: "管理模块", description: "平台管理模块入口。" },
        },
        { path: "tasks", redirect: "/acquisition" },
        { path: "tasks/new", redirect: "/acquisition/new" },
        { path: "tasks/:taskId", redirect: (to) => `/acquisition/${to.params.taskId}` },
        { path: "tasks/:taskId/qc-report", redirect: (to) => `/qc?taskId=${to.params.taskId}` },
      ],
    },
  ],
});

export default router;
