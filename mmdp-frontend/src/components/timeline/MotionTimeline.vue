<template>
  <div class="mt-root">
    <!-- 标注轨道层 -->
    <div
      ref="tracksEl"
      class="mt-tracks"
      @mousedown="onTrackMouseDown"
      @mousemove="onTrackMouseMove"
      @mouseup="onTrackMouseUp"
      @mouseleave="onTrackMouseLeave"
    >
      <!-- 标注段彩色条块 -->
      <div
        v-for="(seg, idx) in annotationSegments"
        :key="idx"
        class="mt-segment"
        :class="{ 'mt-segment-active': selectedSegmentIdx === idx }"
        :style="segmentStyle(seg)"
        :title="`${seg.defectType || seg.category || '标注'}: ${seg.description} (帧 ${seg.frame}-${seg.endFrame})`"
        @click.stop="onSegmentClick(idx, seg)"
      />
      <!-- 拖拽选区 -->
      <div
        v-if="dragSelecting"
        class="mt-drag-selection"
        :style="dragSelectionStyle"
      />
    </div>

    <!-- 进度条层 -->
    <div ref="progressEl" class="mt-progress" @mousemove="onProgressHover" @mouseleave="tooltipFrame = null">
      <!-- 视觉进度条 -->
      <div class="mt-progress-bg">
        <div class="mt-progress-fill" :style="{ width: progressPercent + '%' }" />
      </div>
      <!-- 透明 range input 覆盖上方 -->
      <input
        type="range"
        class="mt-range"
        :min="0"
        :max="totalFrames - 1"
        :step="1"
        :value="currentFrame"
        @input="onSeek"
      />
      <!-- 悬停指示竖线 -->
      <div
        v-if="tooltipFrame != null"
        class="mt-hover-line"
        :style="{ left: frameToPercent(tooltipFrame) + '%' }"
      />
      <!-- 悬停 tooltip -->
      <div
        v-if="tooltipFrame != null"
        class="mt-tooltip"
        :style="{ left: tooltipLeft + 'px' }"
      >
        <div class="mt-tooltip-frame">帧 #{{ tooltipFrame }}</div>
        <div class="mt-tooltip-time">{{ frameToTime(tooltipFrame) }}</div>
        <div v-if="tooltipIssues.length" class="mt-tooltip-issues">
          <div v-for="(issue, i) in tooltipIssues" :key="i" class="mt-tooltip-issue" :style="{ color: severityColor(issue.severity) }">
            {{ issue.description || issue.defectType || issue.category }}
          </div>
        </div>
      </div>
    </div>

    <!-- 底部控制栏 -->
    <div class="mt-controls">
      <button class="mt-ctrl" @click="$emit('step-backward')" title="后退1帧">⏮</button>
      <button class="mt-ctrl" @click="$emit('skip-backward')" title="后退1秒">⏪</button>
      <button class="mt-ctrl mt-ctrl-play" @click="$emit('toggle-play')" title="播放/暂停">
        {{ playing ? '⏸' : '▶' }}
      </button>
      <button class="mt-ctrl" @click="$emit('skip-forward')" title="前进1秒">⏩</button>
      <button class="mt-ctrl" @click="$emit('step-forward')" title="前进1帧">⏭</button>
      <label class="mt-loop">
        <input type="checkbox" :checked="loop" @change="$emit('toggle-loop')" />
        循环
      </label>
      <span class="mt-frame-label">{{ currentFrame }} / {{ totalFrames }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import type { FrameIssueItem } from "@/types/annotation";
import { SEVERITY_OPTIONS } from "@/types/annotation";

const props = defineProps<{
  totalFrames: number;
  currentFrame: number;
  framerate: number;
  frameIssues: FrameIssueItem[];
  playing: boolean;
  loop: boolean;
}>();

const emit = defineEmits<{
  (e: "seek", frame: number): void;
  (e: "create-annotation", startFrame: number, endFrame: number): void;
  (e: "select-annotation", issue: FrameIssueItem): void;
  (e: "toggle-play"): void;
  (e: "step-forward"): void;
  (e: "step-backward"): void;
  (e: "skip-forward"): void;
  (e: "skip-backward"): void;
  (e: "toggle-loop"): void;
}>();

// ── 标注段 ──
const annotationSegments = computed(() =>
  props.frameIssues.filter((f) => f.endFrame != null && f.endFrame > f.frame)
);

const selectedSegmentIdx = ref<number | null>(null);

const progressPercent = computed(() =>
  props.totalFrames > 1 ? (props.currentFrame / (props.totalFrames - 1)) * 100 : 0
);

function frameToPercent(frame: number): number {
  return props.totalFrames > 1 ? (frame / (props.totalFrames - 1)) * 100 : 0;
}

function frameToTime(frame: number): string {
  const sec = frame / props.framerate;
  const m = Math.floor(sec / 60);
  const s = (sec % 60).toFixed(1);
  return `${m}:${s.padStart(4, "0")}s`;
}

function severityColor(s: string): string {
  return SEVERITY_OPTIONS.find((o) => o.value === s)?.color ?? "#f59e0b";
}

function segmentStyle(seg: FrameIssueItem) {
  const left = frameToPercent(seg.frame);
  const width = ((seg.endFrame! - seg.frame + 1) / (props.totalFrames - 1)) * 100;
  return {
    left: left + "%",
    width: Math.max(width, 0.5) + "%",
    background: severityColor(seg.severity),
    opacity: 0.7,
  };
}

function onSegmentClick(idx: number, seg: FrameIssueItem) {
  selectedSegmentIdx.value = idx;
  emit("select-annotation", seg);
  emit("seek", seg.frame);
}

// ── 拖拽创建标注段 ──
const tracksEl = ref<HTMLElement>();
const dragSelecting = ref(false);
const dragStartFrame = ref(0);
const dragEndFrame = ref(0);

function frameFromEvent(e: MouseEvent): number {
  if (!tracksEl.value) return 0;
  const rect = tracksEl.value.getBoundingClientRect();
  const pct = (e.clientX - rect.left) / rect.width;
  return Math.round(pct * (props.totalFrames - 1));
}

function onTrackMouseDown(e: MouseEvent) {
  if ((e.target as HTMLElement).classList.contains("mt-segment")) return;
  dragSelecting.value = true;
  dragStartFrame.value = frameFromEvent(e);
  dragEndFrame.value = dragStartFrame.value;
}

function onTrackMouseMove(e: MouseEvent) {
  if (!dragSelecting.value) return;
  dragEndFrame.value = Math.max(0, Math.min(props.totalFrames - 1, frameFromEvent(e)));
}

function onTrackMouseUp() {
  if (!dragSelecting.value) return;
  dragSelecting.value = false;
  const start = Math.min(dragStartFrame.value, dragEndFrame.value);
  const end = Math.max(dragStartFrame.value, dragEndFrame.value);
  if (end - start >= 1) {
    emit("create-annotation", start, end);
  }
}

function onTrackMouseLeave() {
  if (dragSelecting.value) {
    dragSelecting.value = false;
  }
}

const dragSelectionStyle = computed(() => {
  const start = Math.min(dragStartFrame.value, dragEndFrame.value);
  const end = Math.max(dragStartFrame.value, dragEndFrame.value);
  return {
    left: frameToPercent(start) + "%",
    width: ((end - start + 1) / (props.totalFrames - 1)) * 100 + "%",
  };
});

// ── 进度条悬停 ──
const progressEl = ref<HTMLElement>();
const tooltipFrame = ref<number | null>(null);
const tooltipLeft = ref(0);

function onProgressHover(e: MouseEvent) {
  if (!progressEl.value) return;
  tooltipFrame.value = frameFromProgressEvent(e);
  tooltipLeft.value = e.clientX - progressEl.value.getBoundingClientRect().left - 40;
}

function frameFromProgressEvent(e: MouseEvent): number {
  if (!progressEl.value) return 0;
  const rect = progressEl.value.getBoundingClientRect();
  const pct = (e.clientX - rect.left) / rect.width;
  return Math.round(pct * (props.totalFrames - 1));
}

// 当前悬停帧的标注问题
const tooltipIssues = computed(() => {
  if (tooltipFrame.value == null) return [];
  return props.frameIssues.filter((f) => {
    const end = f.endFrame ?? f.frame;
    return tooltipFrame.value! >= f.frame && tooltipFrame.value! <= end;
  });
});

function onSeek(e: Event) {
  emit("seek", Number((e.target as HTMLInputElement).value));
}
</script>

<style scoped>
.mt-root {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 16px 8px;
  background: #1e1e1e;
  border-top: 1px solid #333;
  flex-shrink: 0;
  font-family: system-ui, -apple-system, sans-serif;
}

/* 标注轨道层 */
.mt-tracks {
  position: relative;
  height: 28px;
  background: rgba(255, 255, 255, 0.03);
  border-radius: 4px;
  cursor: crosshair;
  overflow: hidden;
}

.mt-segment {
  position: absolute;
  top: 2px;
  height: 24px;
  border-radius: 4px;
  cursor: pointer;
  transition: opacity 0.12s;
  min-width: 3px;
}
.mt-segment:hover {
  opacity: 1 !important;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.3);
}
.mt-segment-active {
  opacity: 1 !important;
  box-shadow: 0 0 0 2px #fff;
}

.mt-drag-selection {
  position: absolute;
  top: 2px;
  height: 24px;
  background: rgba(0, 83, 230, 0.35);
  border: 1px dashed rgba(0, 83, 230, 0.6);
  border-radius: 4px;
  pointer-events: none;
}

/* 进度条层 */
.mt-progress {
  position: relative;
  height: 22px;
  display: flex;
  align-items: center;
}
.mt-progress-bg {
  position: absolute;
  inset: 8px 0;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 3px;
  overflow: hidden;
}
.mt-progress-fill {
  height: 100%;
  background: #2563eb;
  border-radius: 3px;
  transition: width 0.05s linear;
}
.mt-range {
  position: relative;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
  margin: 0;
}

/* 悬停指示器 */
.mt-hover-line {
  position: absolute;
  top: 2px;
  bottom: 2px;
  width: 1px;
  background: rgba(255, 255, 255, 0.6);
  pointer-events: none;
  z-index: 2;
}

/* 悬停 tooltip */
.mt-tooltip {
  position: absolute;
  bottom: 100%;
  z-index: 10;
  background: rgba(20, 20, 20, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 6px;
  padding: 6px 10px;
  font-size: 11px;
  pointer-events: none;
  white-space: nowrap;
  transform: translateX(-50%);
  margin-bottom: 6px;
}
.mt-tooltip-frame {
  font-weight: 600;
  color: #e0e0e0;
}
.mt-tooltip-time {
  color: #888;
  font-size: 10px;
}
.mt-tooltip-issues {
  margin-top: 3px;
}
.mt-tooltip-issue {
  font-size: 10px;
  line-height: 1.4;
}

/* 控制栏 */
.mt-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}
.mt-ctrl {
  background: #2a2a2a;
  border: 1px solid #444;
  color: #ccc;
  padding: 4px 12px;
  border-radius: 5px;
  cursor: pointer;
  font-size: 13px;
  font-family: inherit;
}
.mt-ctrl:hover {
  background: #333;
}
.mt-ctrl-play {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
  font-weight: 600;
}
.mt-ctrl-play:hover {
  background: #1d4ed8;
}
.mt-loop {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #888;
  cursor: pointer;
}
.mt-loop input {
  accent-color: #2563eb;
}
.mt-frame-label {
  font-size: 11px;
  color: #888;
  font-family: monospace;
  min-width: 80px;
  text-align: right;
}
</style>
