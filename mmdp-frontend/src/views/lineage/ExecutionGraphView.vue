<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { fetchExecutionGraph } from "@/api/processing";
import { fetchTask } from "@/api/tasks";
import { useDagLayout } from "./composables/useDagLayout";
import DagCanvas from "./components/DagCanvas.vue";
import DagLegend from "./components/DagLegend.vue";
import type { ExecutionGraphResponse } from "@/types/processing";
import type { TaskResponse } from "@/types/task";

const route = useRoute();
const router = useRouter();

const taskId = computed(() => Number(route.params.taskId));

const task = ref<TaskResponse | null>(null);
const graphData = ref<ExecutionGraphResponse | null>(null);
const loading = ref(true);
const error = ref<string | null>(null);
const dagCanvasRef = ref<InstanceType<typeof DagCanvas> | null>(null);

// 指标统计
const metrics = computed(() => {
  if (!graphData.value) return { jobs: 0, assets: 0, sessions: 0, qcCount: 0 };
  const nodes = graphData.value.nodes;
  return {
    jobs: nodes.filter((n) => n.type === "job").length,
    assets: nodes.filter((n) => n.type === "asset").length,
    sessions: nodes.filter((n) => n.type === "session").length,
    qcCount: nodes.filter((n) => n.type === "qc").length,
  };
});

const jobStats = computed(() => {
  if (!graphData.value) return { success: 0, running: 0, failed: 0, pending: 0 };
  const jobs = graphData.value.nodes.filter((n) => n.type === "job");
  return {
    success: jobs.filter((j) => j.status === "SUCCESS").length,
    running: jobs.filter((j) => j.status === "RUNNING" || j.status === "CLAIMED").length,
    failed: jobs.filter((j) => j.status === "FAILED").length,
    pending: jobs.filter((j) => !j.status || j.status === "CREATED").length,
  };
});

const layoutResult = computed(() => {
  if (!graphData.value || graphData.value.nodes.length === 0) {
    return { layoutedNodes: [] as any[], svgWidth: 0, svgHeight: 0 };
  }
  return useDagLayout(graphData.value.nodes, graphData.value.edges);
});

const layoutedNodes = computed(() => layoutResult.value.layoutedNodes);
const svgWidth = computed(() => layoutResult.value.svgWidth);
const svgHeight = computed(() => layoutResult.value.svgHeight);

async function loadData() {
  loading.value = true;
  error.value = null;
  try {
    const [taskResult, graphResult] = await Promise.all([
      fetchTask(taskId.value).catch(() => null),
      fetchExecutionGraph(taskId.value),
    ]);
    task.value = taskResult;
    graphData.value = graphResult;
  } catch (e: any) {
    error.value = e?.message || "加载数据链路图失败";
  } finally {
    loading.value = false;
  }
}

function handleRefresh() {
  loadData();
}

function goBack() {
  router.push(`/acquisition/${taskId.value}`);
}

onMounted(() => {
  loadData();
});
</script>

<template>
  <div class="execution-graph-page">
    <!-- 页面头部 -->
    <header class="graph-header">
      <div class="graph-hdr-left">
        <button type="button" class="graph-back-btn" @click="goBack">
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M10 3L5 8l5 5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
          返回任务详情
        </button>
        <div v-if="task" class="graph-task-info">
          <h1 class="graph-task-title">{{ task.taskName }}</h1>
          <div class="graph-task-meta">
            <span class="light2-badge light2-badge-neutral">
              <span class="light2-bdot" style="background:#9298a3" />
              {{ task.taskCode || `#${task.id}` }}
            </span>
            <span v-if="task.subjectCode" class="graph-meta-text">{{ task.subjectCode }}</span>
            <span v-if="task.collectDate" class="graph-meta-text">{{ task.collectDate }}</span>
          </div>
        </div>
      </div>
      <div class="graph-hdr-right">
        <button type="button" class="light2-btn light2-btn-sec light2-btn-sm" @click="handleRefresh">
          刷新
        </button>
      </div>
    </header>

    <!-- 加载中 -->
    <div v-if="loading" class="graph-state">
      <p class="graph-state-text">正在构建数据链路图...</p>
    </div>

    <!-- 错误 -->
    <div v-else-if="error" class="graph-state graph-state-error">
      <p class="graph-state-text">{{ error }}</p>
      <button type="button" class="light2-btn light2-btn-primary light2-btn-sm" @click="handleRefresh">重试</button>
    </div>

    <!-- 空状态 -->
    <div v-else-if="!graphData || graphData.nodes.length === 0" class="graph-state">
      <p class="graph-state-text">暂无数据链路信息。请先接入数据并创建处理作业。</p>
    </div>

    <!-- 正常渲染 -->
    <template v-else>
      <!-- 指标卡片 -->
      <div class="light2-metrics">
        <div class="light2-mcard">
          <div class="light2-mstripe" style="background:#0053e6" />
          <div class="light2-mlabel">处理作业</div>
          <div class="light2-mvalue">{{ metrics.jobs }}</div>
          <div class="light2-tsub mt-2">
            成功 {{ jobStats.success }} · 运行 {{ jobStats.running }} · 失败 {{ jobStats.failed }}
          </div>
        </div>
        <div class="light2-mcard">
          <div class="light2-mstripe" style="background:#0d7d3e" />
          <div class="light2-mlabel">数据资产</div>
          <div class="light2-mvalue">{{ metrics.assets }}</div>
          <div class="light2-tsub mt-2">含原始资产与派生资产</div>
        </div>
        <div class="light2-mcard">
          <div class="light2-mstripe" style="background:#9298a3" />
          <div class="light2-mlabel">采集会话</div>
          <div class="light2-mvalue">{{ metrics.sessions }}</div>
          <div class="light2-tsub mt-2">数据来源</div>
        </div>
        <div class="light2-mcard">
          <div class="light2-mstripe" style="background:#b87a0a" />
          <div class="light2-mlabel">质检报告</div>
          <div class="light2-mvalue">{{ metrics.qcCount }}</div>
          <div class="light2-tsub mt-2">质量检查记录</div>
        </div>
      </div>

      <!-- 图例 + 工具栏 -->
      <div class="graph-toolbar">
        <DagLegend />
        <div class="graph-toolbar-actions">
          <button type="button" class="light2-btn light2-btn-sec light2-btn-sm" @click="dagCanvasRef?.resetView()">
            适应屏幕
          </button>
        </div>
      </div>

      <!-- DAG 画布 -->
      <div class="graph-canvas-wrapper">
        <DagCanvas
          ref="dagCanvasRef"
          :nodes="layoutedNodes"
          :edges="graphData.edges"
          :svg-width="svgWidth"
          :svg-height="svgHeight"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.execution-graph-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px 28px 40px;
  min-height: 100vh;
  background: #fafbfc;
}

/* ── 头部 ── */
.graph-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
}
.graph-hdr-left {
  min-width: 0;
}
.graph-back-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  font-weight: 600;
  color: #525866;
  background: none;
  border: none;
  cursor: pointer;
  padding: 4px 0;
  margin-bottom: 8px;
  transition: color 0.12s;
}
.graph-back-btn:hover {
  color: #0053e6;
}
.graph-task-title {
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.03em;
  color: #17181a;
  line-height: 1.15;
}
.graph-task-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}
.graph-meta-text {
  font-size: 13px;
  color: #525866;
}
.graph-hdr-right {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ── 状态提示 ── */
.graph-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 64px 20px;
  gap: 12px;
}
.graph-state-error {
  color: #c5222f;
}
.graph-state-text {
  font-size: 14px;
  color: #525866;
}

/* ── 工具栏 ── */
.graph-toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 16px;
}
.graph-toolbar-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ── 画布 ── */
.graph-canvas-wrapper {
  height: 600px;
  overflow: hidden;
  border-radius: 14px;
}

/* ── 复用 light2 ── */
.light2-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}
.light2-mcard {
  background: #fff;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04), 0 0 0 1px rgba(0,0,0,0.03);
  position: relative;
  overflow: hidden;
}
.light2-mstripe {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  border-radius: 3px 3px 0 0;
}
.light2-mlabel { font-size: 12px; color: #9298a3; font-weight: 500; margin-bottom: 6px; }
.light2-mvalue { font-size: 28px; font-weight: 800; letter-spacing: -0.02em; color: #17181a; }
.light2-tsub { font-size: 11px; color: #9298a3; margin-top: 2px; }

.light2-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.12s;
  text-decoration: none;
  font-family: inherit;
  white-space: nowrap;
  background: #fff;
  color: #17181a;
}
.light2-btn-sec { border-color: rgba(0,0,0,0.07); }
.light2-btn-sec:hover { background: #f5f6f8; }
.light2-btn-primary { background: #0053e6; color: #fff; border-color: #0053e6; }
.light2-btn-primary:hover { background: #0046c0; }
.light2-btn-sm { padding: 5px 12px; font-size: 12px; }

.light2-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}
.light2-badge-neutral { background: #f5f6f8; color: #525866; }
.light2-bdot { width: 5px; height: 5px; border-radius: 50%; flex-shrink: 0; }

@media (max-width: 768px) {
  .execution-graph-page { padding: 16px; }
  .light2-metrics { grid-template-columns: repeat(2, 1fr); }
  .graph-canvas-wrapper { height: 400px; }
}
</style>
