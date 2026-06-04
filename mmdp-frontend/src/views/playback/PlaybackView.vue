<template>
  <div class="playback-container">
    <!-- Top Bar -->
    <header class="playback-header">
      <button class="back-btn" @click="$router.back()">
        <BaseIcon name="arrow-left" size="md" /> 返回
      </button>
      <div class="header-info" v-if="data">
        <span class="session-badge">{{ data.sessionId }}</span>
        <span class="header-sep">|</span>
        <span>{{ data.subjectCode }} / {{ data.actionName }}</span>
        <span class="header-sep">|</span>
        <span>{{ formatDuration(data.durationMs) }}</span>
      </div>
      <div class="header-spacer" />
    </header>

    <div v-if="loading" class="loading-state">加载播放数据中...</div>

    <template v-if="data && !loading">
      <!-- Video Grid -->
      <div class="video-grid">
        <div
          v-for="(src, name) in videoSources"
          :key="name"
          class="video-panel"
        >
          <div class="video-label">{{ src.label }}</div>
          <div class="video-wrapper">
            <video
              v-if="src.videoUrl && !videoErrors[name]"
              :ref="(el) => setVideoRef(name, el)"
              :src="src.videoUrl"
              class="video-player"
              preload="auto"
              @timeupdate="(e) => onTimeUpdate(name, e)"
              @loadedmetadata="(e) => onVideoReady(name, e)"
              @error="(e) => onVideoError(name, e)"
              @ended="onEnded"
              playsinline
            />
            <div v-else-if="videoErrors[name]" class="video-error">
              <div class="video-error-title">视频加载失败</div>
              <div class="video-error-detail">{{ videoErrors[name] }}</div>
            </div>
            <div v-else class="video-placeholder">无视频</div>
          </div>
          <div class="video-overlay" v-if="videoTimes[name] != null">
            <span>{{ src.fps?.toFixed(1) }} fps</span>
            <span>{{ formatTime(videoTimes[name]) }}</span>
          </div>
        </div>
      </div>

      <!-- IMU Panel -->
      <div class="imu-panel" v-if="imuSource">
        <div class="imu-header">
          <span class="imu-title">{{ imuSource.label }}</span>
          <span class="imu-rate" v-if="imuSource.sampleRate">{{ imuSource.sampleRate }}Hz / {{ imuSource.sampleCount }} samples</span>
        </div>
        <div class="imu-grid">
          <div class="imu-group">
            <div class="imu-group-title">Accelerometer (m/s²)</div>
            <div class="imu-values">
              <span class="val-x">X: {{ fmtVal(currentImu.acc?.x) }}</span>
              <span class="val-y">Y: {{ fmtVal(currentImu.acc?.y) }}</span>
              <span class="val-z">Z: {{ fmtVal(currentImu.acc?.z) }}</span>
            </div>
          </div>
          <div class="imu-group">
            <div class="imu-group-title">Gyroscope (rad/s)</div>
            <div class="imu-values">
              <span class="val-x">X: {{ fmtVal(currentImu.gyro?.x) }}</span>
              <span class="val-y">Y: {{ fmtVal(currentImu.gyro?.y) }}</span>
              <span class="val-z">Z: {{ fmtVal(currentImu.gyro?.z) }}</span>
            </div>
          </div>
          <div class="imu-group">
            <div class="imu-group-title">Quaternion</div>
            <div class="imu-values">
              <span>W: {{ fmtVal(currentImu.quat?.w) }}</span>
              <span>X: {{ fmtVal(currentImu.quat?.x) }}</span>
              <span>Y: {{ fmtVal(currentImu.quat?.y) }}</span>
              <span>Z: {{ fmtVal(currentImu.quat?.z) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Loading progress bar (bottom, shown while resources load) -->
      <div v-if="!allLoaded" class="load-progress-bar">
        <div class="load-progress-text">
          <span>正在从 OSS 加载数据...</span>
          <span>{{ loadedResources }} / {{ totalResources }} ({{ loadProgress }}%)</span>
        </div>
        <div class="load-progress-track">
          <div class="load-progress-fill" :style="{ width: loadProgress + '%' }" />
        </div>
      </div>

      <!-- Timeline (shown after all resources loaded) -->
      <div v-if="allLoaded" class="timeline-bar">
        <div class="timeline-label">{{ formatTime(masterTime) }}</div>
        <input
          type="range"
          class="timeline-slider"
          :min="0"
          :max="data.durationMs / 1000"
          :step="0.05"
          :value="masterTime"
          @input="seekAll"
        />
        <div class="timeline-label">{{ formatTime(data.durationMs / 1000) }}</div>
      </div>

      <!-- Playback Controls -->
      <div class="playback-controls">
        <button class="ctrl-btn" @click="skipBackward" title="后退5秒">
          <BaseIcon name="chevron-down" size="md" class="icon-rotate-90" />
          <span class="ctrl-label">后退</span>
        </button>
        <button class="ctrl-btn ctrl-play" @click="togglePlay">
          <BaseIcon :name="playing ? 'pause' : 'play'" size="lg" />
          <span class="ctrl-label">{{ playing ? '暂停' : '播放' }}</span>
        </button>
        <button class="ctrl-btn" @click="skipForward" title="快进5秒">
          <BaseIcon name="chevron-down" size="md" class="icon-rotate-270" />
          <span class="ctrl-label">快进</span>
        </button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, shallowRef, watch, onBeforeUnmount } from "vue";
import { useRoute } from "vue-router";
import { fetchSessionPlayback } from "@/api/sessions";
import type { SessionPlaybackResponse, PlaybackSource } from "@/api/sessions";
import BaseIcon from "@/components/BaseIcon.vue";

const route = useRoute();
const loading = ref(true);
const playing = ref(false);
const ready = ref(false);
const data = shallowRef<SessionPlaybackResponse | null>(null);
const masterTime = ref(0);
const videoRefs: Record<string, HTMLVideoElement | null> = {};
const videoTimes: Record<string, number> = {};
const imuData = shallowRef<any[]>([]);
const videoErrors = ref<Record<string, string>>({});

// Progress tracking
const totalResources = ref(0);
const loadedResources = ref(0);
const allLoaded = computed(() => loadedResources.value >= totalResources.value);
const loadProgress = computed(() => {
  if (!totalResources.value) return 0;
  return Math.round((loadedResources.value / totalResources.value) * 100);
});

function setVideoRef(name: string, el: any) {
  videoRefs[name] = el as HTMLVideoElement | null;
}

const videoSources = computed(() => {
  if (!data.value) return {};
  const result: Record<string, PlaybackSource> = {};
  for (const [name, src] of Object.entries(data.value.sources)) {
    if (src.type === "video") result[name] = src;
  }
  return result;
});

const imuSource = computed(() => {
  if (!data.value) return null;
  for (const src of Object.values(data.value.sources)) {
    if (src.type === "imu") return src;
  }
  return null;
});

const currentImu = computed(() => {
  if (!imuData.value.length) return { acc: {}, gyro: {}, quat: {} };
  const t = masterTime.value * 1000; // ms
  let best = imuData.value[0];
  for (const sample of imuData.value) {
    if (sample._t <= t) {
      best = sample;
    } else {
      break;
    }
  }
  return best;
});

function loadImuData(jsonlUrl: string) {
  fetch(jsonlUrl)
    .then((r) => r.text())
    .then((text) => {
      const lines = text.trim().split("\n").filter(Boolean);
      const parsed = lines.map((line) => {
        const obj = JSON.parse(line);
        const startedAtMs = data.value ? new Date(data.value.startedAt).getTime() : 0;
        return {
          _t: obj.hostReceiveTimestamp - startedAtMs,
          acc: obj.latest?.acc || {},
          gyro: obj.latest?.gyro || {},
          quat: obj.latest?.quat || {},
        };
      });
      imuData.value = parsed;
      loadedResources.value++;
    })
    .catch(() => {
      loadedResources.value++; // count as done even on error
    });
}

const SKIP_SECONDS = 5;

function skipBackward() {
  const t = Math.max(0, masterTime.value - SKIP_SECONDS);
  masterTime.value = t;
  for (const ref of Object.values(videoRefs)) {
    if (ref) ref.currentTime = t;
  }
}

function skipForward() {
  const maxT = data.value ? data.value.durationMs / 1000 : Infinity;
  const t = Math.min(maxT, masterTime.value + SKIP_SECONDS);
  masterTime.value = t;
  for (const ref of Object.values(videoRefs)) {
    if (ref) ref.currentTime = t;
  }
}

function togglePlay() {
  if (playing.value) {
    pauseAll();
  } else {
    playAll();
  }
}

function playAll() {
  playing.value = true;
  for (const ref of Object.values(videoRefs)) {
    if (ref) ref.play().catch(() => {});
  }
}

function pauseAll() {
  playing.value = false;
  for (const ref of Object.values(videoRefs)) {
    if (ref) ref.pause();
  }
}

function onTimeUpdate(name: string, e: Event) {
  const video = e.target as HTMLVideoElement;
  videoTimes[name] = video.currentTime;
  if (name === "left" || (!videoRefs["left"] && Object.keys(videoRefs)[0] === name)) {
    masterTime.value = video.currentTime;
  }
}

function onVideoReady(name: string, _e: Event) {
  loadedResources.value++;
  checkAllReady();
}

function onVideoError(name: string, e: Event) {
  const video = e.target as HTMLVideoElement;
  videoErrors.value[name] = video.error
    ? `MEDIA_ERR_${video.error.code}: ${video.error.message}`
    : "未知错误";
  loadedResources.value++;
  checkAllReady();
}

function checkAllReady() {
  const videoNames = Object.keys(videoSources.value).filter(
    k => videoSources.value[k]?.videoUrl
  );
  const ok = videoNames.filter(k => {
    const ref = videoRefs[k];
    return ref && ref.readyState >= 2;
  });
  const err = Object.keys(videoErrors.value);
  const settled = new Set([...ok, ...err]);
  if (settled.size >= videoNames.length) {
    ready.value = true;
  }
}

function onEnded() {
  if (Object.values(videoRefs).every((ref) => !ref || ref.ended || ref.paused)) {
    playing.value = false;
  }
}

function seekAll(e: Event) {
  const target = e.target as HTMLInputElement;
  const t = parseFloat(target.value);
  masterTime.value = t;
  for (const ref of Object.values(videoRefs)) {
    if (ref) ref.currentTime = t;
  }
}

function formatTime(seconds: number | null): string {
  if (seconds == null || isNaN(seconds)) return "00:00";
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

function formatDuration(ms: number): string {
  const totalSec = ms / 1000;
  return formatTime(totalSec);
}

function fmtVal(v: number | undefined): string {
  return v != null ? v.toFixed(3) : "-";
}

onMounted(async () => {
  const sid = route.params.sessionId as string;
  if (!sid) {
    loading.value = false;
    return;
  }
  try {
    data.value = await fetchSessionPlayback(sid);
    // Count total resources
    let count = 0;
    for (const src of Object.values(data.value.sources)) {
      if (src.type === "video" && src.videoUrl) count++;
      if (src.type === "imu" && src.jsonlUrl) count++;
    }
    totalResources.value = count;
    // Load IMU data
    for (const src of Object.values(data.value.sources)) {
      if (src.type === "imu" && src.jsonlUrl) {
        loadImuData(src.jsonlUrl);
      }
    }
    // Start 30s load timeout for each video source
    for (const [name, src] of Object.entries(data.value.sources)) {
      if (src.type === "video" && src.videoUrl) {
        setTimeout(() => {
          if (!videoRefs[name]?.readyState && !videoErrors.value[name]) {
            videoErrors.value[name] = "加载超时（30秒无响应）";
            loadedResources.value++;
            checkAllReady();
          }
        }, 30000);
      }
    }
  } catch {
    // ignore
  }
  loading.value = false;
});
</script>

<style scoped>
.playback-container {
  min-height: 100vh;
  background: #0f0f0f;
  color: #e0e0e0;
  display: flex;
  flex-direction: column;
}

.playback-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 20px;
  background: #1a1a1a;
  border-bottom: 1px solid #333;
  flex-shrink: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: 1px solid #555;
  color: #ccc;
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.back-btn:hover { background: #333; }

.header-info { font-size: 14px; }
.session-badge { color: #4ade80; font-weight: 600; }
.header-sep { color: #555; margin: 0 8px; }
.header-spacer { flex: 1; }

/* Playback Controls */
.playback-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 24px;
  padding: 12px 20px;
  background: #1a1a1a;
  border-top: 1px solid #333;
  flex-shrink: 0;
}

.ctrl-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  background: none;
  border: none;
  color: #aaa;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: 6px;
  font-size: 12px;
  transition: background 0.15s;
}
.ctrl-btn:hover { background: #333; color: #fff; }

.ctrl-btn.ctrl-play {
  background: #2563eb;
  color: #fff;
  padding: 10px 24px;
  border-radius: 8px;
}
.ctrl-btn.ctrl-play:hover { background: #1d4ed8; }

.ctrl-label { font-size: 12px; margin-top: 2px; }

.icon-rotate-90 { transform: rotate(90deg); }
.icon-rotate-270 { transform: rotate(270deg); }

.loading-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: #888;
}

.video-grid {
  display: flex;
  gap: 8px;
  padding: 12px 20px;
  flex: 1;
  min-height: 0;
}

.video-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.video-label {
  font-size: 12px;
  color: #aaa;
  margin-bottom: 4px;
  font-weight: 500;
}

.video-wrapper {
  flex: 1;
  background: #000;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-player {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.video-placeholder {
  color: #666;
  font-size: 14px;
}

.video-error {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #1a0a0a;
  border: 1px solid #5c1a1a;
  border-radius: 4px;
  padding: 20px;
}
.video-error-title { color: #ef4444; font-size: 14px; font-weight: 600; }
.video-error-detail { color: #888; font-size: 12px; font-family: monospace; }

.video-overlay {
  display: flex;
  justify-content: space-between;
  padding: 4px 8px;
  font-size: 11px;
  color: #888;
  font-family: monospace;
}

/* IMU Panel */
.imu-panel {
  padding: 12px 20px;
  background: #1a1a1a;
  border-top: 1px solid #333;
  border-bottom: 1px solid #333;
  flex-shrink: 0;
}

.imu-header {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 8px;
}

.imu-title { font-size: 14px; font-weight: 600; }
.imu-rate { font-size: 12px; color: #888; }

.imu-grid {
  display: flex;
  gap: 32px;
}

.imu-group-title {
  font-size: 11px;
  color: #888;
  margin-bottom: 4px;
}

.imu-values {
  display: flex;
  gap: 12px;
  font-size: 13px;
  font-family: monospace;
}

.val-x { color: #ef4444; }
.val-y { color: #22c55e; }
.val-z { color: #3b82f6; }

/* Timeline */
.timeline-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 20px;
  background: #1a1a1a;
  flex-shrink: 0;
}

.timeline-label {
  font-size: 12px;
  font-family: monospace;
  color: #aaa;
  white-space: nowrap;
}

.timeline-slider {
  flex: 1;
  accent-color: #2563eb;
  height: 4px;
}

/* Loading progress bar */
.load-progress-bar {
  padding: 14px 20px;
  background: #1a1a1a;
  border-top: 1px solid #333;
  flex-shrink: 0;
  margin-top: auto;
}

.load-progress-text {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #aaa;
  margin-bottom: 8px;
}

.load-progress-track {
  height: 4px;
  background: #333;
  border-radius: 2px;
  overflow: hidden;
}

.load-progress-fill {
  height: 100%;
  background: #2563eb;
  border-radius: 2px;
  transition: width 0.3s ease;
}
</style>
