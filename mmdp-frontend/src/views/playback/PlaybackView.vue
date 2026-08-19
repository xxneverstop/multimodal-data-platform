<template>
  <div class="pb-root">
    <!-- top bar -->
    <header class="pb-topbar">
      <button class="pb-back" @click="closeWindow">← 关闭</button>
      <div class="pb-brand"><span class="pb-brand-mark">MMDP</span><strong>STEREO LAB</strong></div>
      <div class="pb-info" v-if="data">
        <span class="pb-session">{{ data.sessionId }}</span>
        <span class="pb-sep">|</span>
        <span>{{ data.subjectCode }} / {{ data.actionName }}</span>
        <span class="pb-sep">|</span>
        <span>{{ fmt(data.durationMs) }}</span>
        <span class="pb-sep">|</span>
        <span>帧 {{ currentFrame }} / {{ totalFrames }}</span>
        <span class="pb-sep">|</span>
        <span>{{ (masterTime * 1000).toFixed(0) }}ms</span>
        <span class="pb-sep">|</span>
        <span>{{ playbackRate }}x</span>
      </div>
      <span class="pb-kb-hint">空格暂停 ←→跳帧</span>
      <button class="pb-toggle-sidebar" @click="sidebarOpen = !sidebarOpen">☰</button>
    </header>

    <div class="pb-body">
      <!-- sidebar -->
      <aside class="pb-sidebar" :class="{ collapsed: !sidebarOpen }">
        <div class="pb-sb-section">
          <div class="pb-sb-title">Session 信息</div>
          <dl class="pb-sb-meta">
            <dt>Subject</dt><dd>{{ data?.subjectCode ?? '-' }}</dd>
            <dt>Action</dt><dd>{{ data?.actionName ?? '-' }}</dd>
            <dt>Profile</dt><dd>{{ data?.profileCode ?? '-' }}</dd>
            <dt>时长</dt><dd>{{ fmt(data?.durationMs) }}</dd>
            <dt>时间策略</dt><dd>{{ data?.timestampPolicy ?? '-' }}</dd>
          </dl>
        </div>

        <div class="pb-sb-section">
          <div class="pb-sb-title">播放源</div>
          <ul class="pb-src-list">
            <li v-for="(src, key) in data?.sources ?? {}" :key="key" class="pb-src-item">
              <span class="pb-src-icon" :class="src.type === 'video' ? 'pb-src-video' : src.type === 'imu' || src.type === 'imu_curve' ? 'pb-src-imu' : 'pb-src-other'"></span>
              <span class="pb-src-label">{{ src.label || key }}</span>
              <span class="pb-src-type">{{ src.type }}</span>
            </li>
          </ul>
        </div>

        <div class="pb-sb-section">
          <div class="pb-sb-title">播放设置</div>
          <div class="pb-speed-row">
            <span class="pb-speed-label">速度</span>
            <button v-for="s in [0.5, 1, 1.5, 2]" :key="s" class="pb-speed-btn" :class="{ active: playbackRate === s }" @click="setSpeed(s)">{{ s }}x</button>
          </div>
        </div>

        <div class="pb-sb-section" v-if="videoSourcesCount > 0">
          <div class="pb-sb-title">布局</div>
          <select v-model.number="videoCols" class="pb-layout-sel">
            <option :value="1">1 列</option>
            <option :value="2" v-if="videoSourcesCount >= 2">2 列</option>
            <option :value="3" v-if="videoSourcesCount >= 3">3 列</option>
            <option :value="4" v-if="videoSourcesCount >= 4">4 列</option>
          </select>
        </div>

        <div class="pb-sb-section pb-sb-annotation">
          <div class="pb-sb-title">标注</div>
          <p class="pb-sb-hint">标注功能即将上线</p>
        </div>
      </aside>

      <!-- main -->
      <main class="pb-main">
        <!-- loading / error -->
        <div v-if="loading" class="pb-state">加载播放数据中...</div>
        <div v-else-if="apiError" class="pb-state pb-state-err">{{ apiError }}</div>

        <template v-if="data && !loading && !apiError">
          <!-- videos -->
          <div class="pb-video-area">
            <div v-if="!videoEntries.length" class="pb-state">
              此 Session 没有可播放的视频
              <div style="font-size:12px;color:#666;margin-top:8px">
                共 {{ Object.keys(data.sources).length }} 个 source，
                其中 video 类型 {{ Object.values(data.sources).filter(s=>s.type==='video').length }} 个
              </div>
            </div>
            <template v-else>
              <div class="pb-modebar">
                <div class="pb-modebar-title">
                  <span class="pb-eyebrow">STEREOSCOPIC VIEW</span>
                  <strong>{{ activeModeLabel }}</strong>
                </div>
                <div class="pb-mode-tabs" role="tablist" aria-label="双目预览模式">
                  <button
                    v-for="mode in stereoModes"
                    :key="mode.id"
                    class="pb-mode-tab"
                    :class="{ active: stereoMode === mode.id }"
                    :disabled="mode.requiresStereo && !hasStereoPair"
                    :title="mode.description"
                    @click="setStereoMode(mode.id)"
                  >
                    <span class="pb-mode-icon">{{ mode.icon }}</span>
                    {{ mode.label }}
                  </button>
                  <button class="pb-mode-tab pb-mode-disabled" disabled title="当前资产不包含 ZED depth/disparity 数据">
                    <span class="pb-mode-icon">▦</span>深度图<span class="pb-soon">需深度资产</span>
                  </button>
                </div>
                <div class="pb-mode-adjust" v-if="stereoMode === 'wipe'">
                  <label>分割位置</label>
                  <input v-model.number="wipePosition" type="range" min="5" max="95" step="1" />
                  <output>{{ wipePosition }}%</output>
                </div>
                <div class="pb-mode-adjust" v-if="stereoMode === 'blink'">
                  <label>切换频率</label>
                  <input v-model.number="blinkHz" type="range" min="1" max="8" step="0.5" />
                  <output>{{ blinkHz.toFixed(1) }} Hz</output>
                </div>
                <button v-if="stereoMode === 'anaglyph'" class="pb-swap-btn" @click="swapAnaglyphEyes = !swapAnaglyphEyes">
                  ⇄ {{ swapAnaglyphEyes ? '已交换眼位' : '交换眼位' }}
                </button>
              </div>

              <div
                ref="stageRef"
                class="pb-stereo-stage"
                :class="`mode-${stereoMode}`"
                :style="stereoMode === 'pair' ? { gridTemplateColumns: `repeat(${videoCols}, 1fr)` } : undefined"
                @pointerdown="onStagePointerDown"
                @pointermove="onStagePointerMove"
                @pointerup="onStagePointerUp"
                @pointercancel="onStagePointerUp"
              >
                <div
                  v-for="([key, src], index) in videoEntries"
                  v-show="stereoMode === 'pair' || index < 2"
                  :key="key"
                  class="pb-eye-layer"
                  :class="{ 'is-left': index === 0, 'is-right': index === 1 }"
                  :style="eyeLayerStyle(index)"
                >
                  <div v-if="stereoMode === 'pair'" class="pb-video-label">
                    <span class="pb-eye-index">{{ index === 0 ? 'L' : index === 1 ? 'R' : index + 1 }}</span>
                    <strong>{{ src.label || key }}</strong>
                    <span class="pb-source-key">{{ key }}</span>
                    <span class="pb-sync-dot" :class="videoLoaded[key] ? 'pb-sync-ok' : 'pb-sync-wait'" :title="videoLoaded[key] ? '已同步' : '加载中'"></span>
                  </div>
                  <div class="pb-video-wrap">
                  <video
                    :ref="el => setVidRef(key, el)"
                    :src="src.videoUrl ?? undefined"
                    class="pb-video"
                    preload="auto"
                    @timeupdate="e => onVidTime(key, e)"
                    @loadedmetadata="e => onVidReady(key, e)"
                    @canplay="() => onVidCanPlay(key)"
                    @error="e => onVidErr(key, e)"
                    @ended="onEnded"
                    playsinline
                  ></video>
                  </div>
                  <div v-if="videoErrors[key]" class="pb-err-msg">{{ videoErrors[key] }}</div>
                </div>

                <canvas v-show="stereoMode === 'anaglyph'" ref="anaglyphCanvas" class="pb-anaglyph-canvas"></canvas>

                <div v-if="stereoMode === 'wipe'" class="pb-wipe-divider" :style="{ left: `${wipePosition}%` }">
                  <span class="pb-wipe-handle">↔</span>
                </div>
                <div v-if="stereoMode !== 'pair'" class="pb-corner-label pb-corner-left">L · {{ videoEntries[0]?.[1].label || '左眼' }}</div>
                <div v-if="stereoMode !== 'pair'" class="pb-corner-label pb-corner-right">R · {{ videoEntries[1]?.[1].label || '右眼' }}</div>
                <div v-if="stereoMode === 'blink'" class="pb-blink-indicator" :class="blinkEye">{{ blinkEye === 'left' ? 'LEFT / L' : 'RIGHT / R' }}</div>

                <div class="pb-stage-hud">
                  <span :class="allVideosReady ? 'is-ready' : 'is-waiting'">● {{ allVideosReady ? 'STEREO LOCK' : 'BUFFERING' }}</span>
                  <span>{{ videoResolution }}</span>
                  <span>{{ videoFps.toFixed(2) }} FPS</span>
                  <span>Δ {{ syncDeltaMs.toFixed(1) }} ms</span>
                  <span>FRAME {{ String(currentFrame).padStart(4, '0') }}</span>
                </div>
              </div>

              <div class="pb-mode-note">
                <span class="pb-mode-note-mark">i</span>
                <span>{{ activeModeDescription }}</span>
              </div>
            </template>
          </div>

          <!-- IMU -->
          <div class="pb-imu" v-if="imuEntries.length">
            <div class="pb-imu-hdr">
              <span class="pb-imu-title">IMU 数据</span>
              <span class="pb-imu-meta">{{ imuEntries.length }} 源</span>
            </div>
            <div class="pb-imu-grid">
              <div v-for="[key, src] in imuEntries" :key="key" class="pb-imu-card">
                <div class="pb-imu-card-title">{{ src.label || key }}</div>
                <div class="pb-imu-vals">
                  <span class="pb-v-x">X: {{ fmtVal(currentImu[key]?.acc?.x) }}</span>
                  <span class="pb-v-y">Y: {{ fmtVal(currentImu[key]?.acc?.y) }}</span>
                  <span class="pb-v-z">Z: {{ fmtVal(currentImu[key]?.acc?.z) }}</span>
                </div>
              </div>
            </div>
          </div>
        </template>
      </main>
    </div>

    <!-- footer: timeline + controls -->
    <footer class="pb-footer" v-if="data && !loading && !apiError">
      <div class="pb-timeline">
        <span class="pb-tl-time">{{ fmt(masterTime * 1000) }}</span>
        <input type="range" class="pb-tl-slider" :min="0" :max="maxDuration" :step="0.05" :value="masterTime" @input="seekAll" />
        <span class="pb-tl-time">{{ fmt(maxDuration * 1000) }}</span>
      </div>
      <div class="pb-ctrls">
        <button class="pb-ctrl" @click="stepBackward">⏮ -1帧</button>
        <button class="pb-ctrl" @click="skipBackward">⏪ -5s</button>
        <button class="pb-ctrl pb-ctrl-play" :disabled="!playing && !allVideosReady" @click="togglePlay">{{ playing ? '⏸ 暂停' : '▶ 播放' }}</button>
        <button class="pb-ctrl" @click="skipForward">⏩ +5s</button>
        <button class="pb-ctrl" @click="stepForward">⏭ +1帧</button>
      </div>
      <div v-if="playbackError" class="pb-playback-error">{{ playbackError }}</div>
      <div class="pb-kb-row">←→ 跳帧 · 空格 暂停 · Shift+←→ 跳5秒</div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import { fetchSessionPlayback } from "@/api/sessions";
import type { SessionPlaybackResponse } from "@/api/sessions";

type StereoMode = "pair" | "wipe" | "blink" | "anaglyph";

const stereoModes: Array<{
  id: StereoMode;
  icon: string;
  label: string;
  description: string;
  requiresStereo: boolean;
}> = [
  { id: "pair", icon: "◫", label: "双路", description: "保留左右眼原始画面，适合逐路质检。", requiresStereo: false },
  { id: "wipe", icon: "◐", label: "扫描对比", description: "拖动分割线叠加检查左右眼水平视差。", requiresStereo: true },
  { id: "blink", icon: "◉", label: "闪烁对比", description: "交替显示左右眼，让视差以画面运动的方式显现。", requiresStereo: true },
  { id: "anaglyph", icon: "◒", label: "红青 3D", description: "实时合成红青立体画面，可配合红青眼镜观察。", requiresStereo: true },
];

const route = useRoute();

// state
const loading = ref(true);
const apiError = ref("");
const data = ref<SessionPlaybackResponse | null>(null);
const playing = ref(false);
const masterTime = ref(0);
const playbackRate = ref(1);
const sidebarOpen = ref(true);
const videoCols = ref(2);
const videoRefs: Record<string, HTMLVideoElement | null> = {};
const videoTimes = ref<Record<string, number>>({});
const videoDurations = ref<Record<string, number>>({});
const videoLoaded = ref<Record<string, boolean>>({});
const videoErrors = ref<Record<string, string>>({});
const playbackError = ref("");
const imuDatasets = ref<Record<string, any[]>>({});
const stereoMode = ref<StereoMode>("pair");
const wipePosition = ref(50);
const wipeDragging = ref(false);
const blinkHz = ref(3);
const blinkEye = ref<"left" | "right">("left");
const swapAnaglyphEyes = ref(false);
const stageRef = ref<HTMLElement | null>(null);
const anaglyphCanvas = ref<HTMLCanvasElement | null>(null);
const videoResolution = ref("—");
const SKIP = 5;

let blinkTimer: ReturnType<typeof setInterval> | null = null;
let anaglyphFrameHandle: number | null = null;
let anaglyphLastRender = 0;
let redCanvas: HTMLCanvasElement | null = null;
let cyanCanvas: HTMLCanvasElement | null = null;

// computed
const videoEntries = computed(() => {
  if (!data.value) return [];
  return Object.entries(data.value.sources).filter(([, s]) => s.type === "video" && s.videoUrl);
});

const maxDuration = computed(() => {
  const loadedDurations = videoEntries.value
    .map(([key]) => videoDurations.value[key])
    .filter(duration => Number.isFinite(duration) && duration > 0);
  if (loadedDurations.length > 0) return Math.min(...loadedDurations);
  return (data.value?.durationMs ?? 0) / 1000;
});

const allVideosReady = computed(() =>
  videoEntries.value.length > 0
  && videoEntries.value.every(([key]) => videoLoaded.value[key] && videoRefs[key] != null)
);

const videoSourcesCount = computed(() => videoEntries.value.length);
const hasStereoPair = computed(() => videoEntries.value.length >= 2);
const activeMode = computed(() => stereoModes.find(mode => mode.id === stereoMode.value) ?? stereoModes[0]);
const activeModeLabel = computed(() => activeMode.value.label);
const activeModeDescription = computed(() => activeMode.value.description);
const syncDeltaMs = computed(() => {
  masterTime.value;
  const firstTwo = videoEntries.value.slice(0, 2).map(([key]) => videoTimes.value[key]);
  if (firstTwo.length < 2 || firstTwo.some(value => value == null)) return 0;
  return Math.abs(firstTwo[0] - firstTwo[1]) * 1000;
});

const imuEntries = computed(() => {
  if (!data.value) return [];
  return Object.entries(data.value.sources).filter(([, s]) => s.type === "imu" || s.type === "imu_curve");
});

const currentImu = computed(() => {
  const result: Record<string, any> = {};
  for (const [key, ds] of Object.entries(imuDatasets.value)) {
    const t = masterTime.value * 1000;
    let best = ds[0];
    for (const s of ds) {
      if (s._t <= t) best = s;
      else break;
    }
    result[key] = best ?? {};
  }
  return result;
});

// helpers
function fmt(ms: number | null | undefined): string {
  if (!ms) return "00:00";
  const sec = ms / 1000;
  const m = Math.floor(sec / 60), s = Math.floor(sec % 60);
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}
function fmtVal(v: number | undefined): string { return v != null ? v.toFixed(3) : "-"; }

function setVidRef(key: string, el: any) { videoRefs[key] = el as HTMLVideoElement | null; }
function closeWindow() { window.close(); }

function setStereoMode(mode: StereoMode) {
  const target = stereoModes.find(item => item.id === mode);
  if (!target || (target.requiresStereo && !hasStereoPair.value)) return;
  stereoMode.value = mode;
}

function eyeLayerStyle(index: number): Record<string, string | number> {
  if (stereoMode.value === "wipe") {
    if (index === 0) return { zIndex: 1 };
    if (index === 1) return {
      zIndex: 2,
      clipPath: `inset(0 0 0 ${wipePosition.value}%)`,
    };
  }
  if (stereoMode.value === "blink") {
    return {
      zIndex: index === 0 ? 1 : 2,
      opacity: blinkEye.value === (index === 0 ? "left" : "right") ? 1 : 0,
    };
  }
  if (stereoMode.value === "anaglyph") {
    return { opacity: 0, pointerEvents: "none" };
  }
  return {};
}

function updateWipePosition(event: PointerEvent) {
  const stage = stageRef.value;
  if (!stage) return;
  const rect = stage.getBoundingClientRect();
  const position = ((event.clientX - rect.left) / rect.width) * 100;
  wipePosition.value = Math.round(Math.min(95, Math.max(5, position)));
}

function onStagePointerDown(event: PointerEvent) {
  if (stereoMode.value !== "wipe") return;
  wipeDragging.value = true;
  (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId);
  updateWipePosition(event);
}

function onStagePointerMove(event: PointerEvent) {
  if (wipeDragging.value) updateWipePosition(event);
}

function onStagePointerUp(event: PointerEvent) {
  if (!wipeDragging.value) return;
  wipeDragging.value = false;
  const target = event.currentTarget as HTMLElement;
  if (target.hasPointerCapture(event.pointerId)) target.releasePointerCapture(event.pointerId);
}

function restartBlink() {
  if (blinkTimer) {
    clearInterval(blinkTimer);
    blinkTimer = null;
  }
  blinkEye.value = "left";
  if (stereoMode.value === "blink") {
    blinkTimer = setInterval(() => {
      blinkEye.value = blinkEye.value === "left" ? "right" : "left";
    }, 1000 / (blinkHz.value * 2));
  }
}

function ensureOffscreenCanvas(canvas: HTMLCanvasElement | null, width: number, height: number) {
  const target = canvas ?? document.createElement("canvas");
  if (target.width !== width) target.width = width;
  if (target.height !== height) target.height = height;
  return target;
}

function renderAnaglyphFrame() {
  if (stereoMode.value !== "anaglyph") return;
  const firstTwo = videoEntries.value.slice(0, 2);
  if (firstTwo.length < 2) return;
  const leftVideo = videoRefs[firstTwo[0][0]];
  const rightVideo = videoRefs[firstTwo[1][0]];
  const output = anaglyphCanvas.value;
  if (!leftVideo || !rightVideo || !output || leftVideo.readyState < 2 || rightVideo.readyState < 2) return;

  const width = leftVideo.videoWidth;
  const height = leftVideo.videoHeight;
  if (!width || !height) return;
  if (output.width !== width) output.width = width;
  if (output.height !== height) output.height = height;
  redCanvas = ensureOffscreenCanvas(redCanvas, width, height);
  cyanCanvas = ensureOffscreenCanvas(cyanCanvas, width, height);

  const redSource = swapAnaglyphEyes.value ? rightVideo : leftVideo;
  const cyanSource = swapAnaglyphEyes.value ? leftVideo : rightVideo;
  const redContext = redCanvas.getContext("2d");
  const cyanContext = cyanCanvas.getContext("2d");
  const outputContext = output.getContext("2d");
  if (!redContext || !cyanContext || !outputContext) return;

  redContext.globalCompositeOperation = "copy";
  redContext.drawImage(redSource, 0, 0, width, height);
  redContext.globalCompositeOperation = "multiply";
  redContext.fillStyle = "#ff0000";
  redContext.fillRect(0, 0, width, height);

  cyanContext.globalCompositeOperation = "copy";
  cyanContext.drawImage(cyanSource, 0, 0, width, height);
  cyanContext.globalCompositeOperation = "multiply";
  cyanContext.fillStyle = "#00ffff";
  cyanContext.fillRect(0, 0, width, height);

  outputContext.globalCompositeOperation = "copy";
  outputContext.drawImage(redCanvas, 0, 0);
  outputContext.globalCompositeOperation = "screen";
  outputContext.drawImage(cyanCanvas, 0, 0);
  outputContext.globalCompositeOperation = "source-over";
}

function anaglyphLoop(timestamp: number) {
  if (stereoMode.value !== "anaglyph") {
    anaglyphFrameHandle = null;
    return;
  }
  const frameInterval = 1000 / Math.max(videoFps.value, 1);
  if (timestamp - anaglyphLastRender >= frameInterval) {
    renderAnaglyphFrame();
    anaglyphLastRender = timestamp;
  }
  anaglyphFrameHandle = requestAnimationFrame(anaglyphLoop);
}

function startAnaglyphLoop() {
  if (anaglyphFrameHandle != null) cancelAnimationFrame(anaglyphFrameHandle);
  anaglyphLastRender = 0;
  anaglyphFrameHandle = requestAnimationFrame(anaglyphLoop);
}

function stopAnaglyphLoop() {
  if (anaglyphFrameHandle != null) cancelAnimationFrame(anaglyphFrameHandle);
  anaglyphFrameHandle = null;
}

watch(stereoMode, async mode => {
  restartBlink();
  stopAnaglyphLoop();
  if (mode === "anaglyph") {
    await nextTick();
    renderAnaglyphFrame();
    startAnaglyphLoop();
  }
});
watch(blinkHz, restartBlink);
watch(swapAnaglyphEyes, renderAnaglyphFrame);

// frame tracking
const videoFps = ref(20); // default, updated from video metadata
const currentFrame = computed(() => Math.floor(masterTime.value * videoFps.value) + 1);
const totalFrames = computed(() => Math.floor(maxDuration.value * videoFps.value));
const frameStep = computed(() => videoFps.value > 0 ? 1 / videoFps.value : 0.05);

// playback controls
function togglePlay() {
  if (playing.value) { pauseAll(); } else { void playAll(); }
}
async function playAll() {
  playbackError.value = "";
  if (!allVideosReady.value) {
    playbackError.value = "双目视频尚未全部就绪，请稍候";
    return;
  }
  const elements = videoEntries.value
    .map(([key]) => videoRefs[key])
    .filter((el): el is HTMLVideoElement => el != null);
  try {
    for (const el of elements) {
      el.playbackRate = playbackRate.value;
      el.currentTime = Math.min(masterTime.value, maxDuration.value);
    }
    await Promise.all(elements.map(el => el.play()));
    playing.value = true;
  } catch (error) {
    for (const el of elements) el.pause();
    playing.value = false;
    playbackError.value = `视频同步播放失败：${error instanceof Error ? error.message : String(error)}`;
  }
}
function pauseAll() {
  playing.value = false;
  for (const [key] of videoEntries.value) {
    const el = videoRefs[key];
    if (el) el.pause();
  }
}
function seekAll(e: Event) {
  setAllTimes(parseFloat((e.target as HTMLInputElement).value));
}
function skipBackward() {
  setAllTimes(masterTime.value - SKIP);
}
function skipForward() {
  setAllTimes(masterTime.value + SKIP);
}
function stepBackward() {
  setAllTimes(masterTime.value - frameStep.value);
}
function stepForward() {
  setAllTimes(masterTime.value + frameStep.value);
}
function setSpeed(s: number) {
  playbackRate.value = s;
  for (const el of Object.values(videoRefs)) if (el) el.playbackRate = s;
}

function setAllTimes(time: number) {
  const safeTime = Math.min(maxDuration.value, Math.max(0, time));
  masterTime.value = safeTime;
  for (const el of Object.values(videoRefs)) {
    if (el) el.currentTime = safeTime;
  }
  if (stereoMode.value === "anaglyph") requestAnimationFrame(renderAnaglyphFrame);
}

function onVidTime(key: string, e: Event) {
  const video = e.target as HTMLVideoElement;
  videoTimes.value[key] = video.currentTime;
  const masterKey = videoEntries.value[0]?.[0];
  if (masterKey === key) {
    masterTime.value = Math.min(video.currentTime, maxDuration.value);
    if (playing.value) {
      const driftThreshold = Math.max(frameStep.value / 2, 0.025);
      for (const [followerKey] of videoEntries.value.slice(1)) {
        const follower = videoRefs[followerKey];
        if (follower && Math.abs(follower.currentTime - video.currentTime) > driftThreshold) {
          follower.currentTime = video.currentTime;
        }
      }
    }
  }
}

function onVidReady(key: string, e: Event) {
  const v = e.target as HTMLVideoElement;
  if (v.videoWidth && v.videoHeight && videoResolution.value === "—") {
    videoResolution.value = `${v.videoWidth}×${v.videoHeight}`;
  }
  if (v.duration && isFinite(v.duration)) {
    videoDurations.value[key] = v.duration;
  }
  if (data.value && (!data.value.durationMs || data.value.durationMs === 0)) {
    if (v.duration && isFinite(v.duration)) {
      data.value.durationMs = Math.round(v.duration * 1000);
    }
  }
  // 从视频元数据估算 FPS（用于帧计数）
  if (v.duration && isFinite(v.duration) && (v as any).webkitDecodedFrameCount) {
    const fc = (v as any).webkitDecodedFrameCount as number;
    if (fc > 0) videoFps.value = Math.round(fc / v.duration);
  }
}
function onVidCanPlay(key: string) {
  videoLoaded.value[key] = true;
  delete videoErrors.value[key];
  if (stereoMode.value === "anaglyph") renderAnaglyphFrame();
}
function onVidErr(key: string, e: Event) {
  const v = e.target as HTMLVideoElement;
  const err = v.error;
  const codes = ['', 'MEDIA_ERR_ABORTED', 'MEDIA_ERR_NETWORK', 'MEDIA_ERR_DECODE', 'MEDIA_ERR_SRC_NOT_SUPPORTED'];
  videoErrors.value[key] = err
    ? `❌ ${codes[err.code] || '未知错误'}: ${err.message || v.src}`
    : `❌ 未知错误: ${v.src}`;
  console.error(`[Playback] ${key} error:`, err, v.src);
  videoLoaded.value[key] = false;
  pauseAll();
}
function onEnded() {
  pauseAll();
  setAllTimes(maxDuration.value);
}

// IMU loading
function loadImuData(url: string) {
  fetch(url).then(r => r.text()).then(text => {
    // 从内容判断格式：CSV 以列名开头，JSONL 以 { 开头
    const trimmed = text.trim();
    const isCsv = !trimmed.startsWith("{") && !trimmed.startsWith("[");
    let parsed: any[];
    if (isCsv) {
      const lines = text.trim().split("\n").filter(Boolean);
      const headers = lines[0].split(",").map(h => h.trim());
      const tsIdx = headers.indexOf("timestamp");
      const accX = headers.indexOf("acc_x"), accY = headers.indexOf("acc_y"), accZ = headers.indexOf("acc_z");
      const gyrX = headers.indexOf("gyro_x"), gyrY = headers.indexOf("gyro_y"), gyrZ = headers.indexOf("gyro_z");
      parsed = lines.slice(1).map(line => {
        const cols = line.split(",").map(c => parseFloat(c.trim()));
        return {
          _t: tsIdx >= 0 ? cols[tsIdx] : 0,
          acc: { x: accX >= 0 ? cols[accX] : 0, y: accY >= 0 ? cols[accY] : 0, z: accZ >= 0 ? cols[accZ] : 0 },
          gyro: { x: gyrX >= 0 ? cols[gyrX] : 0, y: gyrY >= 0 ? cols[gyrY] : 0, z: gyrZ >= 0 ? cols[gyrZ] : 0 },
          quat: {},
        };
      });
    } else {
      const lines = text.trim().split("\n").filter(Boolean);
      const startedAtMs = data.value ? new Date(data.value.startedAt).getTime() : 0;
      parsed = lines.map(line => {
        const obj = JSON.parse(line);
        return { _t: obj.hostReceiveTimestamp - startedAtMs, acc: obj.latest?.acc ?? {}, gyro: obj.latest?.gyro ?? {}, quat: obj.latest?.quat ?? {} };
      });
    }
    imuDatasets.value = { ...imuDatasets.value, [url]: parsed };
  }).catch((err: any) => {
    console.error(`[Playback] IMU load failed: ${url}`, err);
  });
}

// keyboard
function onKeyDown(e: KeyboardEvent) {
  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
  switch (e.code) {
    case "Space": e.preventDefault(); togglePlay(); break;
    case "ArrowLeft": e.preventDefault(); e.shiftKey ? skipBackward() : stepBackward(); break;
    case "ArrowRight": e.preventDefault(); e.shiftKey ? skipForward() : stepForward(); break;
    case "ArrowUp": e.preventDefault(); setSpeed(Math.min(4, playbackRate.value + 0.5)); break;
    case "ArrowDown": e.preventDefault(); setSpeed(Math.max(0.5, playbackRate.value - 0.5)); break;
  }
}

// mount
onMounted(async () => {
  const sid = route.params.sessionId as string;
  if (!sid) { loading.value = false; return; }
  try {
    const parsedJobId = Number(route.query.jobId);
    const jobId = Number.isInteger(parsedJobId) && parsedJobId > 0 ? parsedJobId : undefined;
    data.value = await fetchSessionPlayback(sid, jobId);
    const firstVideo = Object.values(data.value?.sources ?? {}).find(
      source => source.type === "video" && source.videoUrl
    );
    if (firstVideo?.fps && firstVideo.fps > 0) videoFps.value = firstVideo.fps;
    if (!data.value || !Object.keys(data.value.sources).length) {
      apiError.value = "此 Session 无可播放内容，请先执行处理任务生成可播放数据。";
      loading.value = false;
      return;
    }
    for (const [, src] of Object.entries(data.value.sources)) {
      if ((src.type === "imu" || src.type === "imu_curve") && src.jsonlUrl) {
        loadImuData(src.jsonlUrl);
      }
    }
  } catch (e: any) {
    apiError.value = e?.message || String(e) || "加载播放数据失败";
  }
  document.addEventListener("keydown", onKeyDown);
  loading.value = false;
});
onUnmounted(() => {
  document.removeEventListener("keydown", onKeyDown);
  if (blinkTimer) clearInterval(blinkTimer);
  stopAnaglyphLoop();
});
</script>

<style>
/* global reset for fullscreen page */
html, body, #app { height: 100%; margin: 0; }
</style>

<style scoped>
.pb-root { display:flex; flex-direction:column; height:100vh; background:#141414; color:#d4d4d4; font-family: system-ui, -apple-system, sans-serif; overflow:hidden; }

/* topbar */
.pb-topbar { display:flex; align-items:center; gap:16px; padding:8px 16px; background:#1e1e1e; border-bottom:1px solid #333; flex-shrink:0; }
.pb-back, .pb-toggle-sidebar { background:none; border:1px solid #555; color:#aaa; padding:4px 12px; border-radius:4px; cursor:pointer; font-size:13px; }
.pb-back:hover, .pb-toggle-sidebar:hover { background:#333; color:#fff; }
.pb-info { font-size:13px; }
.pb-session { color:#4ade80; font-weight:600; }
.pb-sep { color:#555; margin:0 8px; }
.pb-toggle-sidebar { margin-left:auto; }

/* body */
.pb-body { display:flex; flex:1; overflow:hidden; }

/* sidebar */
.pb-sidebar { width:220px; background:#1e1e1e; border-right:1px solid #333; overflow-y:auto; flex-shrink:0; padding:12px; display:flex; flex-direction:column; gap:16px; transition:width .2s; }
.pb-sidebar.collapsed { width:0; padding:0; overflow:hidden; border:none; }
.pb-sb-section {  }
.pb-sb-title { font-size:11px; font-weight:600; text-transform:uppercase; color:#888; letter-spacing:.06em; margin-bottom:6px; }
.pb-sb-meta { font-size:12px; }
.pb-sb-meta dt { color:#888; margin-top:4px; }
.pb-sb-meta dd { color:#ccc; margin:0 0 4px 0; }
.pb-src-list { list-style:none; padding:0; margin:0; }
.pb-src-item { display:flex; align-items:center; gap:6px; padding:4px 0; font-size:12px; }
.pb-src-icon { width:8px; height:8px; border-radius:50%; flex-shrink:0; }
.pb-src-video { background:#3b82f6; }
.pb-src-imu { background:#f59e0b; }
.pb-src-other { background:#666; }
.pb-src-label { flex:1; color:#ccc; }
.pb-src-type { color:#666; font-size:10px; }
.pb-speed-row { display:flex; align-items:center; gap:4px; }
.pb-speed-label { font-size:12px; color:#888; margin-right:4px; }
.pb-speed-btn { background:#2a2a2a; border:1px solid #444; color:#aaa; padding:2px 8px; border-radius:3px; cursor:pointer; font-size:11px; }
.pb-speed-btn.active { background:#2563eb; border-color:#2563eb; color:#fff; }
.pb-speed-btn:hover { border-color:#888; }
.pb-layout-sel { background:#2a2a2a; border:1px solid #444; color:#ccc; padding:4px 8px; border-radius:4px; font-size:12px; width:100%; }
.pb-sb-hint { font-size:11px; color:#666; }
.pb-sb-annotation { margin-top:auto; border-top:1px solid #333; padding-top:12px; }

/* main */
.pb-main { flex:1; overflow:hidden; display:flex; flex-direction:column; min-height:0; }
.pb-state { flex:1; display:flex; align-items:center; justify-content:center; color:#888; font-size:16px; }
.pb-state-err { color:#ef4444; }

/* video area */
.pb-video-area { padding:12px; flex:1; display:flex; flex-direction:column; min-height:0; }
.pb-video-grid { display:grid; gap:8px; flex:1; min-height:0; }
.pb-video-panel { display:flex; flex-direction:column; min-height:0; flex:1; border:1px solid #444; border-radius:4px; }
.pb-video-label { font-size:11px; color:#888; margin-bottom:4px; flex-shrink:0; }
.pb-video-wrap { flex:1; background:#111; border-radius:4px; overflow:hidden; display:flex; align-items:center; justify-content:center; min-height:240px; position:relative; }
.pb-video-wrap::before { content:"视频加载中..."; color:#555; font-size:13px; position:absolute; }
.pb-video-wrap:has(video)::before { display:none; }
.pb-video { width:100%; height:100%; object-fit:contain; display:block; }
.pb-video-placeholder { color:#666; font-size:13px; display:flex; align-items:center; justify-content:center; width:100%; height:100%; }
.pb-video-err { color:#ef4444; }
.pb-err-msg { color:#ef4444; font-size:11px; padding:4px 8px; margin-top:4px; background:#2a0000; border-radius:4px; }
.pb-debug { padding:12px; background:#1a1a1a; border:1px solid #444; border-radius:4px; overflow:auto; max-height:300px; font-size:11px; font-family:monospace; white-space:pre; }

/* IMU */
.pb-imu { padding:8px 16px; background:#1e1e1e; border-top:1px solid #333; flex-shrink:0; }
.pb-imu-hdr { display:flex; align-items:baseline; gap:12px; margin-bottom:8px; }
.pb-imu-title { font-size:13px; font-weight:600; }
.pb-imu-meta { font-size:11px; color:#888; }
.pb-imu-grid { display:flex; gap:24px; }
.pb-imu-card {  }
.pb-imu-card-title { font-size:11px; color:#888; margin-bottom:2px; }
.pb-imu-vals { display:flex; gap:10px; font-size:12px; font-family:monospace; }
.pb-v-x { color:#ef4444; } .pb-v-y { color:#22c55e; } .pb-v-z { color:#3b82f6; }

/* footer */
.pb-footer { background:#1e1e1e; border-top:1px solid #333; padding:10px 16px; flex-shrink:0; }
.pb-timeline { display:flex; align-items:center; gap:12px; margin-bottom:8px; }
.pb-tl-time { font-size:11px; color:#888; font-family:monospace; white-space:nowrap; }
.pb-tl-slider { flex:1; accent-color:#2563eb; height:4px; }
.pb-ctrls { display:flex; justify-content:center; gap:20px; }
.pb-ctrl { background:#2a2a2a; border:1px solid #444; color:#ccc; padding:6px 18px; border-radius:6px; cursor:pointer; font-size:13px; }
.pb-ctrl:hover { background:#333; }
.pb-ctrl-play { background:#2563eb; border-color:#2563eb; color:#fff; font-weight:600; }
.pb-ctrl-play:hover { background:#1d4ed8; }
.pb-ctrl:disabled { opacity:.4; cursor:default; }
.pb-playback-error { color:#ef4444; font-size:11px; text-align:center; margin-top:6px; }

/* new overlay elements */
.pb-kb-hint { font-size:10px; color:#555; white-space:nowrap; }
.pb-sync-dot { width:6px; height:6px; border-radius:50%; display:inline-block; flex-shrink:0; }
.pb-sync-ok { background:#22c55e; }
.pb-sync-wait { background:#f59e0b; }
.pb-frame-info { font-size:10px; color:#666; }
.pb-ts-overlay { position:absolute; bottom:6px; right:8px; background:rgba(0,0,0,0.7); color:#aaa; font-size:11px; font-family:monospace; padding:2px 6px; border-radius:3px; pointer-events:none; }
.pb-kb-row { text-align:center; font-size:10px; color:#555; margin-top:4px; }

/* 专业双目工作台：工业视觉仪器风格 */
.pb-root {
  --lab-bg:#080b0d;
  --lab-panel:#0d1215;
  --lab-panel-raised:#12191d;
  --lab-line:#253038;
  --lab-line-strong:#3b4b55;
  --lab-text:#dbe7ec;
  --lab-muted:#71818a;
  --lab-cyan:#4ee6d0;
  --lab-amber:#ffba52;
  --lab-red:#ff5f68;
  background:
    radial-gradient(circle at 72% 18%, rgba(29,89,91,.14), transparent 34%),
    linear-gradient(145deg, #080b0d 0%, #0b1013 52%, #07090b 100%);
  color:var(--lab-text);
  font-family:"IBM Plex Sans", "Noto Sans SC", "Microsoft YaHei UI", sans-serif;
}

.pb-root::before {
  content:"";
  position:fixed;
  inset:0;
  pointer-events:none;
  opacity:.16;
  background-image:linear-gradient(rgba(255,255,255,.025) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.02) 1px, transparent 1px);
  background-size:24px 24px;
}

.pb-topbar {
  min-height:44px;
  padding:0 14px;
  gap:14px;
  background:rgba(9,13,15,.94);
  border-bottom:1px solid var(--lab-line);
  box-shadow:0 10px 30px rgba(0,0,0,.2);
  backdrop-filter:blur(16px);
  z-index:20;
}

.pb-back, .pb-toggle-sidebar {
  border-color:var(--lab-line-strong);
  color:#9dafb8;
  border-radius:2px;
  font-family:"Cascadia Code", "JetBrains Mono", monospace;
  letter-spacing:.04em;
}

.pb-brand {
  display:flex;
  align-items:center;
  gap:8px;
  padding-right:14px;
  border-right:1px solid var(--lab-line);
  white-space:nowrap;
}

.pb-brand-mark {
  padding:3px 5px;
  color:#04110f;
  background:var(--lab-cyan);
  font:700 9px/1 "Cascadia Code", monospace;
  letter-spacing:.12em;
}

.pb-brand strong {
  font:600 12px/1 "Cascadia Code", "JetBrains Mono", monospace;
  letter-spacing:.16em;
  color:#ecf8f8;
}

.pb-info {
  display:flex;
  align-items:center;
  color:#83939b;
  font:11px/1 "Cascadia Code", "JetBrains Mono", monospace;
  letter-spacing:.02em;
}

.pb-session { color:var(--lab-cyan); }
.pb-sep { color:#334149; }

.pb-sidebar {
  width:242px;
  padding:18px 16px;
  background:rgba(12,17,20,.94);
  border-right:1px solid var(--lab-line);
  gap:22px;
}

.pb-sb-title {
  color:#84959d;
  font:600 10px/1 "Cascadia Code", monospace;
  letter-spacing:.14em;
  margin-bottom:10px;
}

.pb-sb-meta { margin:0; }
.pb-sb-meta dt { color:#52616a; font:9px/1.2 "Cascadia Code", monospace; text-transform:uppercase; letter-spacing:.08em; }
.pb-sb-meta dd { color:#c2d0d6; font-size:12px; margin-top:3px; margin-bottom:10px; }
.pb-src-item { padding:7px 0; border-bottom:1px solid rgba(58,73,82,.35); }
.pb-src-type { font-family:"Cascadia Code", monospace; letter-spacing:.05em; }
.pb-src-video { background:var(--lab-cyan); box-shadow:0 0 10px rgba(78,230,208,.55); }
.pb-speed-btn, .pb-layout-sel { border-radius:2px; background:#10171a; border-color:var(--lab-line); }
.pb-speed-btn.active { background:rgba(78,230,208,.14); border-color:var(--lab-cyan); color:var(--lab-cyan); }

.pb-main { position:relative; }
.pb-video-area { padding:16px 18px 10px; gap:12px; }

.pb-modebar {
  display:flex;
  align-items:center;
  gap:14px;
  min-height:54px;
  padding:8px 10px 8px 14px;
  background:linear-gradient(180deg, rgba(18,25,29,.98), rgba(12,18,21,.98));
  border:1px solid var(--lab-line);
  border-bottom:none;
  box-shadow:0 8px 28px rgba(0,0,0,.16);
}

.pb-modebar-title {
  min-width:150px;
  display:flex;
  flex-direction:column;
  gap:4px;
}

.pb-modebar-title strong { font-size:14px; font-weight:600; color:#edf8f7; }
.pb-eyebrow { color:var(--lab-cyan); font:8px/1 "Cascadia Code", monospace; letter-spacing:.18em; }
.pb-mode-tabs { display:flex; align-items:center; gap:4px; }

.pb-mode-tab {
  height:34px;
  display:inline-flex;
  align-items:center;
  gap:6px;
  padding:0 11px;
  color:#83949d;
  background:#0b1013;
  border:1px solid #263139;
  border-radius:2px;
  cursor:pointer;
  font-size:11px;
  transition:border-color .16s ease, color .16s ease, background .16s ease, transform .16s ease;
}

.pb-mode-tab:hover:not(:disabled) { color:#d9ecea; border-color:#577079; transform:translateY(-1px); }
.pb-mode-tab.active { color:#06120f; background:var(--lab-cyan); border-color:var(--lab-cyan); font-weight:700; box-shadow:0 0 18px rgba(78,230,208,.18); }
.pb-mode-icon { font:15px/1 "Cascadia Code", monospace; }
.pb-mode-disabled { opacity:.35; cursor:not-allowed; }
.pb-soon { padding-left:4px; font:8px/1 "Cascadia Code", monospace; text-transform:uppercase; }

.pb-mode-adjust {
  margin-left:auto;
  display:grid;
  grid-template-columns:auto minmax(90px, 150px) 54px;
  align-items:center;
  gap:8px;
  color:#7e9099;
  font:9px/1 "Cascadia Code", monospace;
  text-transform:uppercase;
  letter-spacing:.06em;
}

.pb-mode-adjust input { accent-color:var(--lab-cyan); }
.pb-mode-adjust output { color:var(--lab-cyan); text-align:right; }
.pb-swap-btn { margin-left:auto; height:32px; padding:0 10px; color:var(--lab-amber); background:rgba(255,186,82,.06); border:1px solid rgba(255,186,82,.38); border-radius:2px; cursor:pointer; font-size:10px; }

.pb-stereo-stage {
  position:relative;
  flex:1;
  min-height:320px;
  overflow:hidden;
  background:#020304;
  border:1px solid var(--lab-line);
  box-shadow:0 24px 70px rgba(0,0,0,.42), inset 0 0 0 1px rgba(255,255,255,.015);
  isolation:isolate;
}

.pb-stereo-stage.mode-pair { display:grid; gap:1px; background:var(--lab-line); }
.pb-eye-layer { min-width:0; min-height:0; background:#030506; }
.mode-pair .pb-eye-layer { position:relative; display:flex; flex-direction:column; }
.mode-wipe .pb-eye-layer, .mode-blink .pb-eye-layer, .mode-anaglyph .pb-eye-layer { position:absolute; inset:0; }
.mode-wipe { cursor:col-resize; touch-action:none; }

.pb-video-label {
  min-height:34px;
  margin:0;
  padding:0 10px;
  display:flex;
  align-items:center;
  gap:8px;
  color:#d5e2e7;
  background:#0c1215;
  border-bottom:1px solid #233038;
}

.pb-video-label strong { font-size:11px; font-weight:600; }
.pb-eye-index { display:grid; place-items:center; width:19px; height:19px; color:#04100e; background:var(--lab-cyan); font:800 10px/1 "Cascadia Code", monospace; }
.is-right .pb-eye-index { color:#170f02; background:var(--lab-amber); }
.pb-source-key { color:#53646d; font:9px/1 "Cascadia Code", monospace; }
.pb-video-label .pb-sync-dot { margin-left:auto; }
.pb-sync-ok { background:var(--lab-cyan); box-shadow:0 0 9px rgba(78,230,208,.6); }

.pb-video-wrap { min-height:0; border-radius:0; background:#030506; }
.mode-pair .pb-video-wrap { flex:1; }
.mode-wipe .pb-video-wrap, .mode-blink .pb-video-wrap, .mode-anaglyph .pb-video-wrap { position:absolute; inset:0; }
.pb-video { background:#030506; }

.pb-anaglyph-canvas {
  position:absolute;
  inset:0;
  z-index:4;
  width:100%;
  height:100%;
  object-fit:contain;
  background:#020304;
}

.pb-wipe-divider {
  position:absolute;
  top:0;
  bottom:0;
  z-index:8;
  width:1px;
  background:var(--lab-cyan);
  box-shadow:0 0 0 1px rgba(0,0,0,.7), 0 0 18px rgba(78,230,208,.55);
  pointer-events:none;
}

.pb-wipe-handle {
  position:absolute;
  top:50%;
  left:50%;
  width:34px;
  height:34px;
  display:grid;
  place-items:center;
  transform:translate(-50%,-50%);
  color:#03110f;
  background:var(--lab-cyan);
  border:4px solid rgba(2,5,6,.8);
  border-radius:50%;
  font:700 14px/1 "Cascadia Code", monospace;
}

.pb-corner-label {
  position:absolute;
  top:14px;
  z-index:9;
  padding:6px 8px;
  color:#d6e2e6;
  background:rgba(4,8,10,.74);
  border:1px solid rgba(93,116,127,.55);
  backdrop-filter:blur(8px);
  font:9px/1 "Cascadia Code", monospace;
  letter-spacing:.06em;
  pointer-events:none;
}

.pb-corner-left { left:14px; border-left:2px solid var(--lab-cyan); }
.pb-corner-right { right:14px; border-right:2px solid var(--lab-amber); }

.pb-blink-indicator {
  position:absolute;
  z-index:9;
  top:14px;
  left:50%;
  transform:translateX(-50%);
  min-width:78px;
  padding:6px 10px;
  text-align:center;
  background:rgba(5,9,11,.82);
  border:1px solid var(--lab-cyan);
  color:var(--lab-cyan);
  font:700 9px/1 "Cascadia Code", monospace;
  letter-spacing:.1em;
}
.pb-blink-indicator.right { border-color:var(--lab-amber); color:var(--lab-amber); }

.pb-stage-hud {
  position:absolute;
  z-index:10;
  left:12px;
  right:12px;
  bottom:10px;
  display:flex;
  align-items:center;
  gap:14px;
  padding:7px 9px;
  color:#80929b;
  background:rgba(3,7,9,.78);
  border:1px solid rgba(63,82,91,.62);
  backdrop-filter:blur(10px);
  font:9px/1 "Cascadia Code", "JetBrains Mono", monospace;
  letter-spacing:.06em;
  pointer-events:none;
}
.pb-stage-hud .is-ready { color:var(--lab-cyan); }
.pb-stage-hud .is-waiting { color:var(--lab-amber); }
.pb-stage-hud span:last-child { margin-left:auto; color:#b7c8ce; }

.pb-mode-note {
  display:flex;
  align-items:center;
  gap:8px;
  min-height:30px;
  padding:0 10px;
  color:#6f818a;
  background:rgba(10,15,18,.82);
  border:1px solid var(--lab-line);
  border-top:none;
  font-size:10px;
}
.pb-mode-note-mark { display:grid; place-items:center; width:15px; height:15px; color:var(--lab-cyan); border:1px solid rgba(78,230,208,.45); border-radius:50%; font:9px/1 serif; }

.pb-imu { background:rgba(13,19,22,.94); border-color:var(--lab-line); }
.pb-footer { padding:10px 18px 8px; background:rgba(9,14,17,.96); border-color:var(--lab-line); }
.pb-tl-slider { accent-color:var(--lab-cyan); }
.pb-ctrl { border-radius:2px; background:#10171a; border-color:#2b3941; font-family:"Cascadia Code", monospace; }
.pb-ctrl-play { color:#03110f; background:var(--lab-cyan); border-color:var(--lab-cyan); }
.pb-ctrl-play:hover { background:#7ff1e1; }

@media (max-width:1100px) {
  .pb-info .pb-sep:nth-of-type(n+3), .pb-info span:nth-of-type(n+7) { display:none; }
  .pb-modebar { align-items:flex-start; flex-wrap:wrap; }
  .pb-modebar-title { min-width:120px; }
  .pb-mode-adjust, .pb-swap-btn { margin-left:0; }
}

@media (max-width:760px) {
  .pb-sidebar { position:absolute; z-index:30; height:100%; box-shadow:20px 0 50px rgba(0,0,0,.5); }
  .pb-brand strong, .pb-kb-hint, .pb-info { display:none; }
  .pb-video-area { padding:8px; }
  .pb-mode-tabs { width:100%; overflow-x:auto; }
  .pb-mode-tab { flex:none; }
  .pb-stereo-stage.mode-pair { grid-template-columns:1fr !important; }
  .pb-stage-hud span:nth-child(2), .pb-stage-hud span:nth-child(4) { display:none; }
}
</style>
