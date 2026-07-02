<template>
  <div class="pb-root">
    <!-- top bar -->
    <header class="pb-topbar">
      <button class="pb-back" @click="window.close()">← 关闭</button>
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
        <!-- DEBUG BAR (开发调试用，上线去掉) -->
        <div v-if="data" style="background:#1a1a0a;color:#aaa;padding:3px 12px;font-size:10px;font-family:monospace;flex-shrink:0;display:flex;gap:16px;flex-wrap:wrap;border-bottom:1px solid #333">
          <span>data=✅</span><span>sources={{ Object.keys(data.sources).length }}</span><span>videos={{ videoEntries.length }}</span><span>imus={{ imuEntries.length }}</span>
          <span v-for="(s,k) in data.sources" :key="k">{{ k }}:{{ s.videoUrl?'🎬':s.jsonlUrl?'📊':'⚪' }}</span>
        </div>
        <!-- loading / error -->
        <div v-if="loading" class="pb-state">加载播放数据中...</div>
        <div v-else-if="apiError" class="pb-state pb-state-err">{{ apiError }}</div>

        <template v-if="data && !loading && !apiError">
          <!-- DEBUG: 显示所有 source 的 URL -->
          <div class="pb-debug" v-if="false">
            <pre>{{ JSON.stringify(data.sources, null, 2) }}</pre>
          </div>
          <!-- videos -->
          <div class="pb-video-area">
            <div v-if="!videoEntries.length" class="pb-state">
              此 Session 没有可播放的视频
              <div style="font-size:12px;color:#666;margin-top:8px">
                共 {{ Object.keys(data.sources).length }} 个 source，
                其中 video 类型 {{ Object.values(data.sources).filter(s=>s.type==='video').length }} 个
              </div>
            </div>
            <div v-else class="pb-video-grid" :style="{ gridTemplateColumns: `repeat(${videoCols}, 1fr)` }">
              <div v-for="[key, src] in videoEntries" :key="key" class="pb-video-panel">
                <div class="pb-video-label">
                  <span>{{ src.label || key }}</span>
                  <span class="pb-sync-dot" :class="videoLoaded[key] ? 'pb-sync-ok' : 'pb-sync-wait'" :title="videoLoaded[key] ? '已同步' : '加载中'"></span>
                  <span class="pb-frame-info">帧 {{ currentFrame }} / {{ totalFrames }}</span>
                </div>
                <div class="pb-video-wrap">
                  <video
                    :ref="el => setVidRef(key, el)"
                    :src="src.videoUrl"
                    class="pb-video"
                    preload="auto"
                    controls
                    @timeupdate="e => onVidTime(key, e)"
                    @loadedmetadata="e => onVidReady(key, e)"
                    @canplay="() => onVidCanPlay(key)"
                    @error="e => onVidErr(key, e)"
                    @ended="onEnded"
                    playsinline
                  ></video>
                  <div class="pb-ts-overlay" v-if="videoLoaded[key]">{{ masterTime.toFixed(3) }}s</div>
                </div>
                <div v-if="videoErrors[key]" class="pb-err-msg">{{ videoErrors[key] }}</div>
              </div>
            </div>
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
        <span class="pb-tl-time">{{ fmt(data.durationMs) }}</span>
      </div>
      <div class="pb-ctrls">
        <button class="pb-ctrl" @click="stepBackward">⏮ -1帧</button>
        <button class="pb-ctrl" @click="skipBackward">⏪ -5s</button>
        <button class="pb-ctrl pb-ctrl-play" @click="togglePlay">{{ playing ? '⏸ 暂停' : '▶ 播放' }}</button>
        <button class="pb-ctrl" @click="skipForward">⏩ +5s</button>
        <button class="pb-ctrl" @click="stepForward">⏭ +1帧</button>
      </div>
      <div class="pb-kb-row">←→ 跳帧 · 空格 暂停 · Shift+←→ 跳5秒</div>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { useRoute } from "vue-router";
import { fetchSessionPlayback } from "@/api/sessions";
import type { SessionPlaybackResponse, PlaybackSource } from "@/api/sessions";

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
const videoTimes: Record<string, number> = {};
const videoErrors = ref<Record<string, string>>({});
const imuDatasets = ref<Record<string, any[]>>({});
const SKIP = 5;

// computed
const maxDuration = computed(() => (data.value?.durationMs ?? 0) / 1000);

const videoEntries = computed(() => {
  if (!data.value) return [];
  return Object.entries(data.value.sources).filter(([, s]) => s.type === "video" && s.videoUrl);
});

const videoSourcesCount = computed(() => videoEntries.value.length);

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

// frame tracking
const videoFps = ref(20); // default, updated from video metadata
const currentFrame = computed(() => Math.floor(masterTime.value * videoFps.value) + 1);
const totalFrames = computed(() => Math.floor(maxDuration.value * videoFps.value));
const frameStep = computed(() => videoFps.value > 0 ? 1 / videoFps.value : 0.05);

// playback controls
function togglePlay() {
  if (playing.value) { pauseAll(); } else { playAll(); }
}
function playAll() {
  playing.value = true;
  for (const [key] of videoEntries.value) {
    const el = videoRefs[key];
    if (el) { el.playbackRate = playbackRate.value; el.play().catch(() => {}); }
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
  const t = parseFloat((e.target as HTMLInputElement).value);
  masterTime.value = t;
  for (const el of Object.values(videoRefs)) {
    if (el) el.currentTime = t;
  }
}
function skipBackward() {
  masterTime.value = Math.max(0, masterTime.value - SKIP);
  for (const el of Object.values(videoRefs)) if (el) el.currentTime = masterTime.value;
}
function skipForward() {
  masterTime.value = Math.min(maxDuration.value, masterTime.value + SKIP);
  for (const el of Object.values(videoRefs)) if (el) el.currentTime = masterTime.value;
}
function stepBackward() {
  masterTime.value = Math.max(0, masterTime.value - frameStep.value);
  for (const el of Object.values(videoRefs)) if (el) el.currentTime = masterTime.value;
}
function stepForward() {
  masterTime.value = Math.min(maxDuration.value, masterTime.value + frameStep.value);
  for (const el of Object.values(videoRefs)) if (el) el.currentTime = masterTime.value;
}
function setSpeed(s: number) {
  playbackRate.value = s;
  for (const el of Object.values(videoRefs)) if (el) el.playbackRate = s;
}

function onVidTime(key: string, e: Event) {
  videoTimes[key] = (e.target as HTMLVideoElement).currentTime;
  // use first video as master
  if (Object.keys(videoRefs)[0] === key) {
    masterTime.value = (e.target as HTMLVideoElement).currentTime;
  }
}
const videoLoaded = ref<Record<string, boolean>>({});

function onVidReady(key: string, e: Event) {
  const v = e.target as HTMLVideoElement;
  videoLoaded.value[key] = true;
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
function onVidCanPlay(key: string) { videoLoaded.value[key] = true; console.log("[Playback] video canplay:", key); }
function onVidErr(key: string, e: Event) {
  const v = e.target as HTMLVideoElement;
  const err = v.error;
  const codes = ['', 'MEDIA_ERR_ABORTED', 'MEDIA_ERR_NETWORK', 'MEDIA_ERR_DECODE', 'MEDIA_ERR_SRC_NOT_SUPPORTED'];
  videoErrors.value[key] = err
    ? `❌ ${codes[err.code] || '未知错误'}: ${err.message || v.src}`
    : `❌ 未知错误: ${v.src}`;
  console.error(`[Playback] ${key} error:`, err, v.src);
}
function onEnded() {
  if (Object.values(videoRefs).every(r => !r || r.ended || r.paused)) playing.value = false;
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
    data.value = await fetchSessionPlayback(sid);
    console.log("[Playback] API response:", JSON.stringify(data.value?.sources, null, 2));
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
onUnmounted(() => { document.removeEventListener("keydown", onKeyDown); });
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

/* new overlay elements */
.pb-kb-hint { font-size:10px; color:#555; white-space:nowrap; }
.pb-sync-dot { width:6px; height:6px; border-radius:50%; display:inline-block; flex-shrink:0; }
.pb-sync-ok { background:#22c55e; }
.pb-sync-wait { background:#f59e0b; }
.pb-frame-info { font-size:10px; color:#666; }
.pb-ts-overlay { position:absolute; bottom:6px; right:8px; background:rgba(0,0,0,0.7); color:#aaa; font-size:11px; font-family:monospace; padding:2px 6px; border-radius:3px; pointer-events:none; }
.pb-kb-row { text-align:center; font-size:10px; color:#555; margin-top:4px; }
</style>
