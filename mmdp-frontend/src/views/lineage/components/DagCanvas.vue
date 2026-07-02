<script setup lang="ts">
import { computed, ref } from "vue";
import type { LayoutedNode, ExecutionGraphEdge } from "@/types/processing";
import { buildEdgePath } from "../composables/useDagLayout";

const props = defineProps<{
  nodes: LayoutedNode[];
  edges: ExecutionGraphEdge[];
  svgWidth: number;
  svgHeight: number;
}>();

const scale = ref(1);
const panX = ref(0);
const panY = ref(0);

const viewBox = computed(() => `0 0 ${props.svgWidth} ${props.svgHeight}`);

function onWheel(e: WheelEvent) {
  e.preventDefault();
  const delta = e.deltaY > 0 ? 0.9 : 1.1;
  scale.value = Math.max(0.2, Math.min(3, scale.value * delta));
}

let dragging = false;
let lastX = 0;
let lastY = 0;
function onMouseDown(e: MouseEvent) { dragging = true; lastX = e.clientX; lastY = e.clientY; }
function onMouseMove(e: MouseEvent) {
  if (!dragging) return;
  panX.value += e.clientX - lastX;
  panY.value += e.clientY - lastY;
  lastX = e.clientX; lastY = e.clientY;
}
function onMouseUp() { dragging = false; }

function resetView() { scale.value = 1; panX.value = 0; panY.value = 0; }
defineExpose({ resetView });

// ── 颜色映射 ──
interface NodeColor { fill: string; stroke: string; text: string }
function nodeColor(type: string, status?: string | null): NodeColor {
  const s = status?.toUpperCase();
  if (s === "SUCCESS" || s === "QC_PASSED") return { fill: "#e6f4ea", stroke: "#0d7d3e", text: "#0d7d3e" };
  if (s === "FAILED" || s === "QC_FAILED") return { fill: "#fce8e6", stroke: "#c5222f", text: "#c5222f" };
  if (s === "RUNNING" || s === "CLAIMED") return { fill: "#eef3ff", stroke: "#0053e6", text: "#0053e6" };
  if (s === "QC_WARNING") return { fill: "#fef7e0", stroke: "#b87a0a", text: "#b87a0a" };
  return { fill: "#f5f6f8", stroke: "#9298a3", text: "#525866" };
}

// ── 带渲染数据的节点 ──
interface RenderNode extends LayoutedNode {
  color: NodeColor;
  shortLabel: string;
  subLabel: string;
  cx: number; cy: number;
  titleY: number; subY: number;
}

const renderNodes = computed<RenderNode[]>(() =>
  props.nodes.map((n) => {
    const color = nodeColor(n.type, n.status);
    let short = n.label || n.type;
    if (short.length > 16) short = short.slice(0, 15) + "…";
    let sub = "";
    if (n.type === "asset") sub = n.assetType || n.sourceType || "";
    else if (n.type === "job") sub = n.pipelineId || n.executorType || "";
    else if (n.type === "qc") sub = n.status || "";
    if (sub.length > 18) sub = sub.slice(0, 17) + "…";
    return {
      ...n,
      color,
      shortLabel: short,
      subLabel: sub,
      cx: n.x + n.width / 2,
      cy: n.y + n.height / 2,
      titleY: n.y + n.height / 2 - 4,
      subY: n.y + n.height / 2 + 12,
    };
  })
);

// ── 去重边 ──
const uniqueEdges = computed(() => {
  const seen = new Set<string>();
  return props.edges.filter((e) => {
    const key = `${e.source}|${e.target}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
});

const nodeMap = computed(() => {
  const m = new Map<string, LayoutedNode>();
  props.nodes.forEach((n) => m.set(n.id, n));
  return m;
});

interface EdgeRender { d: string; stroke: string; dash: string; width: number; marker: string }
const edgeRenderList = computed<EdgeRender[]>(() =>
  uniqueEdges.value.map((edge) => {
    const src = nodeMap.value.get(edge.source);
    const tgt = nodeMap.value.get(edge.target);
    const d = src && tgt ? buildEdgePath(src, tgt) : "";
    const isDashed = edge.style === "dashed";
    return {
      d,
      stroke: isDashed ? "#b0b8c1" : "#9298a3",
      dash: isDashed ? "6,4" : "none",
      width: isDashed ? 1.2 : 1.6,
      marker: isDashed ? "url(#arrow-dashed)" : "url(#arrow-solid)",
    };
  })
);

function xmlEscape(s: string) {
  return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

// ── SVG shape generators (per type) ──
function taskShape(n: RenderNode): string {
  const { x, y, width: w, height: h, color } = n;
  return `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="10" fill="${color.fill}" stroke="${color.stroke}" stroke-width="2"/><rect x="${x}" y="${y}" width="${w}" height="5" rx="2" fill="${color.stroke}"/>`;
}
function sessionShape(n: RenderNode): string {
  return `<rect x="${n.x}" y="${n.y}" width="${n.width}" height="${n.height}" rx="10" fill="${n.color.fill}" stroke="${n.color.stroke}" stroke-width="1.5" stroke-dasharray="6,3"/>`;
}
function assetShape(n: RenderNode): string {
  return `<rect x="${n.x}" y="${n.y}" width="${n.width}" height="${n.height}" rx="10" fill="${n.color.fill}" stroke="${n.color.stroke}" stroke-width="1.5"/><rect x="${n.x}" y="${n.y}" width="4" height="${n.height}" rx="2" fill="${n.color.stroke}"/>`;
}
function jobShape(n: RenderNode): string {
  return `<rect x="${n.x}" y="${n.y}" width="${n.width}" height="${n.height}" rx="8" fill="${n.color.fill}" stroke="${n.color.stroke}" stroke-width="2.5"/>`;
}
function qcShape(n: RenderNode): string {
  return `<rect x="${n.x}" y="${n.y}" width="${n.width}" height="${n.height}" rx="20" fill="${n.color.fill}" stroke="${n.color.stroke}" stroke-width="1.5" stroke-dasharray="4,2"/>`;
}
function nodeShapeHtml(n: RenderNode): string {
  switch (n.type) {
    case "task": return taskShape(n);
    case "session": return sessionShape(n);
    case "asset": return assetShape(n);
    case "job": return jobShape(n);
    case "qc": return qcShape(n);
    default: return `<rect x="${n.x}" y="${n.y}" width="${n.width}" height="${n.height}" rx="10" fill="${n.color.fill}" stroke="${n.color.stroke}" stroke-width="1.5"/>`;
  }
}
function nodeLabelHtml(n: RenderNode): string {
  const title = xmlEscape(n.shortLabel);
  let html = `<text x="${n.cx}" y="${n.titleY}" text-anchor="middle" fill="#17181a" font-size="12" font-weight="600" font-family="system-ui, sans-serif">${title}</text>`;
  if (n.subLabel) {
    const sub = xmlEscape(n.subLabel);
    html += `<text x="${n.cx}" y="${n.subY}" text-anchor="middle" fill="${n.color.text}" font-size="10" font-weight="500" font-family="system-ui, sans-serif">${sub}</text>`;
  }
  return html;
}
</script>

<template>
  <div
    class="dag-canvas-container"
    @wheel="onWheel"
    @mousedown="onMouseDown"
    @mousemove="onMouseMove"
    @mouseup="onMouseUp"
    @mouseleave="onMouseUp"
  >
    <svg
      :viewBox="viewBox"
      :style="{
        transform: `scale(${scale}) translate(${panX}px, ${panY}px)`,
        transformOrigin: 'center center',
      }"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <marker id="arrow-solid" viewBox="0 0 10 8" refX="9" refY="4" markerWidth="8" markerHeight="6" orient="auto">
          <path d="M 0 0 L 10 4 L 0 8 Z" fill="#9298a3" />
        </marker>
        <marker id="arrow-dashed" viewBox="0 0 10 8" refX="9" refY="4" markerWidth="8" markerHeight="6" orient="auto">
          <path d="M 0 0 L 10 4 L 0 8 Z" fill="#b0b8c1" />
        </marker>
      </defs>

      <!-- 边 -->
      <g class="dag-edges">
        <path
          v-for="(er, i) in edgeRenderList"
          :key="'e' + i"
          :d="er.d"
          fill="none"
          :stroke="er.stroke"
          :stroke-width="er.width"
          :stroke-dasharray="er.dash"
          :marker-end="er.marker"
        />
      </g>

      <!-- 节点 -->
      <g class="dag-nodes">
        <g v-for="n in renderNodes" :key="n.id">
          <g v-html="nodeShapeHtml(n)" />
          <g v-html="nodeLabelHtml(n)" />
        </g>
      </g>
    </svg>
  </div>
</template>

<style scoped>
.dag-canvas-container {
  width: 100%;
  height: 100%;
  min-height: 500px;
  overflow: hidden;
  cursor: grab;
  background: #fafbfc;
  border-radius: 14px;
  border: 1px solid #e5e7eb;
}
.dag-canvas-container:active {
  cursor: grabbing;
}
.dag-canvas-container svg {
  width: 100%;
  height: 100%;
  transition: transform 0.08s ease-out;
}
</style>
