<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>数据标注</h1>
        <p>管理动作数据的人工标注工作，标注入口在 Session 详情页或 3D 动作查看器中。</p>
      </div>
    </div>

    <hr class="light2-divider" />

    <!-- filters -->
    <div class="light2-filters">
      <input v-model="filters.sessionCode" type="text" placeholder="搜索 sessionCode..." class="light2-input" @keyup.enter="searchCount += 1" />
      <input v-model="filters.taskName" type="text" placeholder="搜索任务名..." class="light2-input" @keyup.enter="searchCount += 1" />
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="searchCount += 1">搜索</button>
    </div>

    <!-- loading -->
    <div v-if="loading" class="light2-empty-state">正在加载标注数据...</div>

    <!-- empty -->
    <div v-else-if="!rows.length" class="light2-empty-state">
      暂无需要标注的动作数据。请先在 Session 中运行「生成3D查看数据」处理任务。
    </div>

    <!-- table -->
    <div v-else class="light2-tbl">
      <table>
        <thead>
          <tr>
            <th>Session</th>
            <th>任务名称</th>
            <th>被试/动作</th>
            <th>标注进度</th>
            <th>评级分布</th>
            <th>状态</th>
            <th style="width:110px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in filteredRows" :key="row.sessionId">
            <td>
              <RouterLink
                :to="`/sessions/${row.sessionId}`"
                class="light2-info-card-link"
              >
                {{ row.sessionCode }}
              </RouterLink>
            </td>
            <td>{{ row.taskName }}</td>
            <td>{{ row.subjectCode }} / {{ row.actionName }}</td>
            <td>
              <div class="flex items-center gap-2">
                <div class="light2-progress" style="width:80px">
                  <div
                    class="light2-progress-fill"
                    :style="{ width: `${progressPercent(row)}%`, background: progressColor(progressPercent(row)) }"
                  />
                </div>
                <span style="font-size:12px;color:var(--color-text-secondary)">{{ row.annotatedCount }}/{{ row.totalAssets }}</span>
              </div>
            </td>
            <td style="font-size:12px">{{ distributionText(row) }}</td>
            <td>
              <span class="light2-badge" :class="badgeClass(row)">
                <span class="light2-bdot" :style="{ background: badgeColor(row) }" />
                {{ statusLabel(row) }}
              </span>
            </td>
            <td>
              <RouterLink
                :to="`/sessions/${row.sessionId}`"
                class="light2-btn light2-btn-primary light2-btn-sm"
                style="text-decoration:none"
              >
                进入标注
              </RouterLink>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { RouterLink } from "vue-router";
import { fetchAllSessions } from "@/api/sessions";
import { fetchSessionAnnotationProgress } from "@/api/annotation";
import type { AnnotationProgressResponse } from "@/types/annotation";
import type { SessionResponse } from "@/api/sessions";
import { formatDateTime } from "@/utils/format";

interface AnnotationSessionRow {
  sessionId: number;
  sessionCode: string;
  taskId: number;
  taskName: string;
  subjectCode: string;
  actionName: string;
  totalAssets: number;
  annotatedCount: number;
  inProgressCount: number;
  unannotatedCount: number;
  ratingDistribution: Record<string, number>;
  updatedAt: string;
}

const rows = ref<AnnotationSessionRow[]>([]);
const loading = ref(true);
const searchCount = ref(0);
const filters = reactive({ sessionCode: "", taskName: "" });

const filteredRows = computed(() =>
  rows.value.filter(
    (r) =>
      (!filters.sessionCode ||
        r.sessionCode
          .toLowerCase()
          .includes(filters.sessionCode.trim().toLowerCase())) &&
      (!filters.taskName ||
        r.taskName
          .toLowerCase()
          .includes(filters.taskName.trim().toLowerCase()))
  )
);

const progressPercent = (row: AnnotationSessionRow) =>
  row.totalAssets > 0
    ? Math.round((row.annotatedCount / row.totalAssets) * 100)
    : 0;

const progressColor = (pct: number) => {
  if (pct >= 80) return "#0d9444";
  if (pct >= 40) return "#f59e0b";
  return "#9298a3";
};

function badgeClass(
  row: Pick<AnnotationSessionRow, "totalAssets" | "annotatedCount">
) {
  if (row.annotatedCount >= row.totalAssets && row.totalAssets > 0)
    return "light2-badge-ok";
  if (row.annotatedCount > 0) return "light2-badge-info";
  return "light2-badge-neutral";
}

function badgeColor(
  row: Pick<AnnotationSessionRow, "totalAssets" | "annotatedCount">
) {
  if (row.annotatedCount >= row.totalAssets && row.totalAssets > 0)
    return "#0d7d3e";
  if (row.annotatedCount > 0) return "var(--color-brand-500)";
  return "#9298a3";
}

function statusLabel(
  row: Pick<AnnotationSessionRow, "totalAssets" | "annotatedCount" | "inProgressCount">
) {
  if (row.annotatedCount >= row.totalAssets && row.totalAssets > 0) return "已完成";
  if (row.annotatedCount > 0 || row.inProgressCount > 0) return "进行中";
  return "未开始";
}

function distributionText(row: AnnotationSessionRow) {
  if (!row.ratingDistribution || Object.keys(row.ratingDistribution).length === 0) return "-";
  return Object.entries(row.ratingDistribution)
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([r, c]) => `${r}:${c}`)
    .join(" ");
}

async function loadData() {
  loading.value = true;
  try {
    const sessions: SessionResponse[] = await fetchAllSessions();
    const progressResults = await Promise.allSettled(
      sessions.map((s) => fetchSessionAnnotationProgress(s.id))
    );

    const result: AnnotationSessionRow[] = [];
    for (let i = 0; i < sessions.length; i++) {
      const s = sessions[i];
      const p = progressResults[i];
      const progress: AnnotationProgressResponse | null =
        p.status === "fulfilled" ? p.value : null;

      // 只显示有动作资产的 session（totalAssets > 0）
      if (progress && progress.totalAssets > 0) {
        result.push({
          sessionId: s.id,
          sessionCode: s.sessionCode || `#${s.id}`,
          taskId: s.taskId,
          taskName: s.taskName || "-",
          subjectCode: s.subjectCode || "-",
          actionName: s.actionName || "-",
          totalAssets: progress.totalAssets,
          annotatedCount: progress.annotatedCount,
          inProgressCount: progress.inProgressCount,
          unannotatedCount: progress.unannotatedCount,
          ratingDistribution: progress.ratingDistribution,
          updatedAt: "", // session 没有 updatedAt
        });
      }
    }
    rows.value = result;
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  loadData();
});
</script>
