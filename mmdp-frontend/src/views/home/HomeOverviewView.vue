<template>
  <div class="space-y-4">
    <PageHeader eyebrow="主页" title="平台概览" description="查看数据资产、采集批次、处理任务和质检结果的整体情况。">
      <template #actions>
        <BaseButton variant="ghost" size="sm" @click="loadOverview">
          <BaseIcon name="refresh-cw" size="sm" />
          <span>刷新</span>
        </BaseButton>
        <BaseButton variant="ghost" size="sm" @click="toggleFullscreen">
          <BaseIcon name="maximize" size="sm" />
          <span>全屏</span>
        </BaseButton>
      </template>
    </PageHeader>

    <section class="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-12">
      <div class="xl:col-span-3">
        <OverviewMetricCard
          label="数据资产总数"
          :value="overview.assetCount"
          icon="database"
          icon-tone="bg-blue-50 text-blue-600"
        />
      </div>
      <div class="xl:col-span-3">
        <OverviewMetricCard
          label="采集批次"
          :value="overview.sessionCount"
          icon="camera"
          icon-tone="bg-emerald-50 text-emerald-600"
        />
      </div>
      <div class="xl:col-span-3">
        <OverviewMetricCard
          label="总数据时长"
          :value="overview.totalDuration"
          icon="clock"
          icon-tone="bg-amber-50 text-amber-600"
        />
      </div>
      <div class="xl:col-span-3">
        <OverviewMetricCard
          label="成品资产数"
          :value="overview.finalAssetCount"
          icon="shield"
          icon-tone="bg-slate-100 text-slate-700"
        />
      </div>

      <PageCard title="模态数据分布" class="xl:col-span-3">
        <CompactDonutChart :items="overview.modalityDistribution" center-label="模态" />
      </PageCard>
      <PageCard title="数据来源分布" class="xl:col-span-3">
        <CompactDonutChart :items="overview.sourceDistribution" center-label="来源" />
      </PageCard>
      <PageCard title="处理状态分布" class="xl:col-span-3">
        <CompactDonutChart :items="overview.processingStatusDistribution" center-label="状态" />
      </PageCard>
      <PageCard title="质检结果分布" class="xl:col-span-3">
        <CompactDonutChart :items="overview.qcResultDistribution" center-label="结果" />
      </PageCard>

      <PageCard title="数据资产增长趋势" class="xl:col-span-6">
        <CompactTrendChart
          :labels="overview.assetGrowthTrend30d.labels"
          :series="overview.assetGrowthTrend30d.series"
        />
      </PageCard>
      <PageCard title="采集批次增长趋势" class="xl:col-span-6">
        <CompactTrendChart
          :labels="overview.sessionGrowthTrend30d.labels"
          :series="overview.sessionGrowthTrend30d.series"
        />
      </PageCard>

      <PageCard title="处理任务趋势" class="xl:col-span-6">
        <CompactTrendChart
          :labels="overview.processingTrend30d.labels"
          :series="overview.processingTrend30d.series"
        />
      </PageCard>
      <PageCard title="质检结果趋势" class="xl:col-span-6">
        <CompactTrendChart
          :labels="overview.qcTrend30d.labels"
          :series="overview.qcTrend30d.series"
        />
      </PageCard>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive } from "vue";
import { fetchPlatformOverview } from "@/api/platform";
import BaseButton from "@/components/BaseButton.vue";
import BaseIcon from "@/components/BaseIcon.vue";
import CompactDonutChart from "@/components/CompactDonutChart.vue";
import CompactTrendChart from "@/components/CompactTrendChart.vue";
import OverviewMetricCard from "@/components/OverviewMetricCard.vue";
import PageCard from "@/components/PageCard.vue";
import PageHeader from "@/components/PageHeader.vue";
import type { PlatformOverview } from "@/types/platform";

function createEmptyOverview(): PlatformOverview {
  return {
    taskCount: 0,
    sessionCount: 0,
    assetCount: 0,
    processingCount: 0,
    qcPassRate: "0%",
    totalDuration: "0m",
    finalAssetCount: 0,
    modalityDistribution: [],
    sourceDistribution: [],
    processingStatusDistribution: [],
    qcResultDistribution: [],
    assetGrowthTrend30d: { labels: [], series: [] },
    sessionGrowthTrend30d: { labels: [], series: [] },
    processingTrend30d: { labels: [], series: [] },
    qcTrend30d: { labels: [], series: [] },
    recentSessions: [],
    recentAssets: [],
    recentJobs: []
  };
}

const overview = reactive<PlatformOverview>(createEmptyOverview());

async function loadOverview() {
  Object.assign(overview, await fetchPlatformOverview());
}

async function toggleFullscreen() {
  if (!document.fullscreenElement) {
    await document.documentElement.requestFullscreen?.();
    return;
  }
  await document.exitFullscreen?.();
}

onMounted(loadOverview);
</script>
