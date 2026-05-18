<template>
  <div class="space-y-5">
    <PageHeader
      eyebrow="QC Reports"
      title="质检报告"
      description="按任务维度查看质检结论、检查项详情、告警与错误信息，并保留原始 report_json 便于进一步排查。"
      :meta="headerMeta"
    >
      <template #actions>
        <BaseButton :to="`/tasks/${taskId}`">返回任务详情</BaseButton>
      </template>
    </PageHeader>

    <PageCard eyebrow="Primary Panel" title="报告列表" description="优先展示结构化结果，再展示告警、错误和原始 JSON，便于快速定位质检问题。">
      <div v-if="loading" class="py-12 text-center text-sm text-slate-500">正在加载质检报告...</div>
      <EmptyState
        v-else-if="!filteredReports.length"
        title="暂无质检报告"
        description="请先在任务详情页上传文件并等待后端生成质检结果，之后即可在这里查看结构化报告。"
        icon="质"
      />
      <div v-else class="space-y-5">
        <article v-for="report in filteredReports" :key="report.id" class="app-section-card rounded-[14px] p-4">
          <div class="flex flex-col gap-4">
            <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
              <div class="space-y-2.5">
                <div class="flex flex-wrap items-center gap-2">
                  <StatusBadge :status="report.reportJson.overallStatus || report.qcStatus" />
                  <span class="rounded-[8px] bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-500">文件 ID {{ report.fileId }}</span>
                </div>
                <div>
                  <h3 class="text-base font-semibold text-slate-900">报告 #{{ report.id }}</h3>
                  <p class="mt-1 text-sm leading-5 text-slate-500">{{ report.reportJson.summary || report.summary }}</p>
                </div>
              </div>

              <div class="flex flex-wrap gap-2">
                <div class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                  <div class="font-medium uppercase tracking-[0.14em] text-slate-400">创建时间</div>
                  <div class="mt-1 text-sm font-semibold text-slate-800">{{ formatDateTime(report.createdAt) }}</div>
                </div>
                <div class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-2 text-xs text-slate-500">
                  <div class="font-medium uppercase tracking-[0.14em] text-slate-400">检查项数量</div>
                  <div class="mt-1 text-sm font-semibold text-slate-800">{{ report.reportJson.checks?.length ?? 0 }}</div>
                </div>
              </div>
            </div>

            <div class="grid gap-4 xl:grid-cols-[minmax(0,1.3fr)_minmax(280px,0.7fr)]">
              <div class="rounded-[12px] border border-slate-200 bg-white p-4 shadow-[var(--shadow-card)]">
                <div class="flex items-center justify-between gap-3">
                  <h4 class="text-sm font-semibold uppercase tracking-[0.14em] text-slate-500">检查项</h4>
                  <span class="text-xs text-slate-500">{{ report.reportJson.checks?.length ?? 0 }} 项</span>
                </div>

                <div v-if="report.reportJson.checks?.length" class="mt-3 space-y-2.5">
                  <div
                    v-for="check in report.reportJson.checks"
                    :key="`${report.id}-${check.name}`"
                    class="rounded-[10px] border border-slate-200 bg-slate-50 px-3 py-3"
                  >
                    <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                      <div>
                        <div class="text-sm font-medium text-slate-900">{{ check.name }}</div>
                        <p class="mt-1 text-xs leading-5 text-slate-500">{{ check.message }}</p>
                      </div>
                      <StatusBadge :status="check.status" />
                    </div>
                  </div>
                </div>
                <p v-else class="mt-3 text-xs text-slate-500">当前报告未返回结构化检查项列表。</p>
              </div>

              <div class="space-y-3">
                <PageCard eyebrow="Secondary Panel" title="Warnings" secondary>
                  <ul v-if="report.reportJson.warnings?.length" class="space-y-2 text-xs leading-5 text-amber-800">
                    <li v-for="warning in report.reportJson.warnings" :key="warning">{{ warning }}</li>
                  </ul>
                  <p v-else class="text-xs text-slate-500">无告警信息。</p>
                </PageCard>

                <PageCard eyebrow="Secondary Panel" title="Errors" secondary>
                  <ul v-if="report.reportJson.errors?.length" class="space-y-2 text-xs leading-5 text-rose-800">
                    <li v-for="error in report.reportJson.errors" :key="error">{{ error }}</li>
                  </ul>
                  <p v-else class="text-xs text-slate-500">无错误信息。</p>
                </PageCard>

                <div class="rounded-[12px] border border-slate-200 bg-slate-50 px-4 py-3">
                  <div class="text-[11px] font-medium uppercase tracking-[0.14em] text-slate-400">原始 report_json</div>
                  <pre class="mt-2 overflow-x-auto rounded-[10px] bg-slate-900 px-3 py-3 text-xs leading-5 text-slate-100">{{ stringifyReport(report.reportJson) }}</pre>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>

      <p v-if="errorMessage" class="mt-3 rounded-[10px] border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{{ errorMessage }}</p>
    </PageCard>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { fetchTaskQcReports } from "@/api/qc";
import BaseButton from "@/components/BaseButton.vue";
import EmptyState from "@/components/EmptyState.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import StatusBadge from "@/components/StatusBadge.vue";
import type { QcReportResponse, StructuredQcReport } from "@/types/qc";
import { formatDateTime } from "@/utils/format";

const route = useRoute();
const taskId = Number(route.params.taskId);
const fileId = route.query.fileId ? Number(route.query.fileId) : null;

const reports = ref<QcReportResponse[]>([]);
const loading = ref(false);
const errorMessage = ref("");

const filteredReports = computed(() => {
  if (!fileId) {
    return reports.value;
  }
  return reports.value.filter((report) => report.fileId === fileId);
});

const headerMeta = computed(() => [
  { label: "任务编号", value: taskId },
  { label: "筛选文件", value: fileId ?? "全部文件" },
  { label: "报告数量", value: filteredReports.value.length }
]);

function stringifyReport(report: StructuredQcReport) {
  return JSON.stringify(report, null, 2);
}

async function loadReports() {
  loading.value = true;
  errorMessage.value = "";
  try {
    reports.value = await fetchTaskQcReports(taskId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : "加载质检报告失败";
  } finally {
    loading.value = false;
  }
}

onMounted(loadReports);
</script>
