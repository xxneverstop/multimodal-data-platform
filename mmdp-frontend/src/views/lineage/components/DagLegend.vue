<script setup lang="ts">
const nodeTypes = [
  { type: "task", label: "采集任务", shape: "■", color: "#9298a3" },
  { type: "session", label: "采集会话", shape: "◇", color: "#9298a3" },
  { type: "asset", label: "数据资产", shape: "▌", color: "#9298a3" },
  { type: "job", label: "处理作业", shape: "●", color: "#0053e6" },
  { type: "qc", label: "质检报告", shape: "○", color: "#b87a0a" },
];

const statusColors = [
  { label: "成功 / 通过", fill: "#e6f4ea", stroke: "#0d7d3e" },
  { label: "运行中 / 已领取", fill: "#eef3ff", stroke: "#0053e6" },
  { label: "失败", fill: "#fce8e6", stroke: "#c5222f" },
  { label: "警告", fill: "#fef7e0", stroke: "#b87a0a" },
  { label: "等待 / 默认", fill: "#f5f6f8", stroke: "#9298a3" },
];

const edgeStyles = [
  { label: "实线：直接关联（输入/输出/包含）", stroke: "#9298a3", dash: "none" },
  { label: "虚线：间接关联（依赖/归属）", stroke: "#b0b8c1", dash: "6,4" },
];
</script>

<template>
  <div class="dag-legend">
    <div class="legend-section">
      <div class="legend-title">节点类型</div>
      <div class="legend-items">
        <div v-for="nt in nodeTypes" :key="nt.type" class="legend-item">
          <span class="legend-shape" :style="{ color: nt.color }">{{ nt.shape }}</span>
          <span>{{ nt.label }}</span>
        </div>
      </div>
    </div>
    <div class="legend-section">
      <div class="legend-title">状态颜色</div>
      <div class="legend-items">
        <div v-for="sc in statusColors" :key="sc.label" class="legend-item">
          <span class="legend-swatch" :style="{ background: sc.fill, borderColor: sc.stroke }" />
          <span>{{ sc.label }}</span>
        </div>
      </div>
    </div>
    <div class="legend-section">
      <div class="legend-title">连线类型</div>
      <div class="legend-items">
        <div v-for="es in edgeStyles" :key="es.label" class="legend-item">
          <svg width="40" height="16" class="legend-edge-svg">
            <line x1="0" y1="8" x2="36" y2="8" :stroke="es.stroke" stroke-width="1.5" :stroke-dasharray="es.dash" />
          </svg>
          <span>{{ es.label }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dag-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 12px;
  color: #525866;
}
.legend-section {
  min-width: 140px;
}
.legend-title {
  font-weight: 700;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: #9298a3;
  margin-bottom: 6px;
  text-transform: uppercase;
}
.legend-items {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
}
.legend-shape {
  font-size: 14px;
  width: 18px;
  text-align: center;
}
.legend-swatch {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  border: 1.5px solid;
  flex-shrink: 0;
}
.legend-edge-svg {
  flex-shrink: 0;
}
</style>
