import { createRouter, createWebHistory } from "vue-router";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/",
      component: () => import("@/layouts/AppLayout.vue"),
      children: [
        { path: "", redirect: "/home" },
        {
          path: "home",
          name: "home",
          component: () => import("@/views/home/HomeOverviewView.vue"),
          meta: { title: "平台概览", description: "围绕业务任务、采集会话、数据资产与处理执行的总览工作台。" }
        },
        {
          path: "data",
          name: "data-assets",
          component: () => import("@/views/data/DataAssetListView.vue"),
          meta: { title: "数据资产", description: "以 data_asset 为核心组织全平台数据资产，默认带 taskId 与 sessionId 两个维度。" }
        },
        {
          path: "data/:assetId",
          name: "data-asset-detail",
          component: () => import("@/views/data/DataAssetDetailView.vue"),
          meta: { title: "资产详情", description: "查看数据资产的基础信息、关联链路、处理记录与质检结果。" }
        },
        {
          path: "upload",
          name: "upload",
          component: () => import("@/views/upload/UploadEntryView.vue"),
          meta: { title: "资产接入", description: "统一处理普通上传与外部路径登记，资产接入始终围绕 taskId 与 sessionId。" }
        },
        {
          path: "acquisition",
          name: "acquisition-list",
          component: () => import("@/views/acquisition/AcquisitionListView.vue"),
          meta: { title: "采集任务", description: "业务任务列表，用于组织多个真实采集 session 与其下游数据资产。" }
        },
        {
          path: "acquisition/new",
          name: "acquisition-new",
          component: () => import("@/views/acquisition/AcquisitionCreateView.vue"),
          meta: { title: "新建采集任务", description: "创建 acquisition_task 业务任务，为后续 session 与资产接入建立上游容器。" }
        },
        {
          path: "acquisition/:taskId",
          name: "acquisition-detail",
          component: () => import("@/views/acquisition/AcquisitionDetailView.vue"),
          meta: { title: "任务详情", description: "查看业务任务下的 session、资产概览、处理记录与最近质检结果。" }
        },
        {
          path: "sessions",
          name: "session-list",
          component: () => import("@/views/sessions/SessionListView.vue"),
          meta: { title: "Session", description: "真实采集会话列表，承接资产、处理、标注和质检的核心上下文。" }
        },
        {
          path: "sessions/:sessionId",
          name: "session-detail",
          component: () => import("@/views/sessions/SessionDetailView.vue"),
          meta: { title: "Session 详情", description: "查看单次真实采集会话下的资产、派生结果、处理记录与质检情况。" }
        },
        {
          path: "processing",
          name: "processing",
          component: () => import("@/views/processing/ProcessingTemplateView.vue"),
          meta: { title: "处理", description: "默认展示 processing_template，执行记录作为 processing_job 辅助展示。" }
        },
        {
          path: "annotation",
          name: "annotation",
          component: () => import("@/views/annotation/AnnotationTaskView.vue"),
          meta: { title: "标注", description: "轻量管理 annotation_task、annotation_tag 与 annotation_status，并跳转外部标注 H5。" }
        },
        {
          path: "qc",
          name: "qc",
          component: () => import("@/views/qc/QcWorkspaceView.vue"),
          meta: { title: "质检", description: "通过规则与日志两个视角查看质检模板、质检记录与资产质量结论。" }
        },
        {
          path: "export",
          name: "export",
          component: () => import("@/views/export/FinalAssetView.vue"),
          meta: { title: "成品资产", description: "Final Asset 视图，聚焦可交付数据资产，而非单纯下载列表。" }
        },
        {
          path: "management/:module",
          name: "management-module",
          component: () => import("@/views/management/ManagementModuleView.vue"),
          meta: { title: "管理模块", description: "平台管理骨架页，承载用户、设备、流程、存储与字典管理入口。" }
        },
        { path: "tasks", redirect: "/acquisition" },
        { path: "tasks/new", redirect: "/acquisition/new" },
        { path: "tasks/:taskId", redirect: (to) => `/acquisition/${to.params.taskId}` },
        { path: "tasks/:taskId/qc-report", redirect: (to) => `/qc?taskId=${to.params.taskId}` }
      ]
    }
  ]
});

export default router;
