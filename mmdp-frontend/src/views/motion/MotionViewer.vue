<template>
  <!-- 加载状态 -->
  <div v-if="loading" class="mv-loading">
    <div class="mv-loading-spinner" />
    <p>{{ loadingStep }}</p>
    <div class="mv-progress-bar"><div class="mv-progress-fill" :style="{ width: loadProgress + '%' }" /></div>
    <p class="mv-progress-text">{{ loadDone }}/{{ loadTotal }}</p>
  </div>

  <!-- 错误 -->
  <div v-else-if="error" class="mv-error">
    <p>{{ error }}</p>
    <button class="mv-btn" @click="initAll">重试</button>
  </div>

  <!-- 主界面：PlaybackView 布局 -->
  <div v-else class="mv-root" @keydown="onKeyDown" tabindex="0" ref="rootEl">
    <!-- 顶部栏 -->
    <header class="mv-topbar">
      <button class="mv-back" @click="close">← 关闭</button>
      <div class="mv-info" v-if="currentModel">
        <span class="mv-session">{{ currentModel.shortName }}</span>
        <span class="mv-sep">|</span>
        <span>帧 {{ currentFrame }} / {{ totalFrames }}</span>
        <span class="mv-sep">|</span>
        <span>{{ currentFramerate }} fps</span>
        <span class="mv-sep">|</span>
        <span>{{ playSpeed }}x</span>
      </div>
      <span class="mv-kb-hint">空格暂停 ←→跳帧 A标注</span>
      <button class="mv-toggle-sidebar" @click="sidebarOpen = !sidebarOpen">☰</button>
    </header>

    <div class="mv-body">
      <!-- 左侧侧边栏 -->
      <aside class="mv-sidebar" :class="{ collapsed: !sidebarOpen }">
        <!-- Tab 导航 -->
        <div class="mv-tab-nav">
          <button v-for="t in tabs" :key="t.key" class="mv-tab-btn" :class="{ active: activeTab === t.key }" @click="activeTab = t.key">
            {{ t.label }}
          </button>
        </div>

        <!-- Tab 内容 -->
        <div class="mv-tab-content">
          <!-- Tab: 动作列表 -->
          <div v-show="activeTab === 'models'" class="mv-section">
            <div class="mv-sb-title">{{ modelSlots.length }} 个动作</div>
            <div class="mv-model-list">
              <div v-for="slot in modelSlots" :key="slot.id" class="mv-model-item" :class="{ 'mv-model-item-active': slot.id === selectedId }" @click="selectModel(slot.id)">
                <span class="mv-model-dot" :style="{ background: slot.color }" />
                <div class="mv-model-info">
                  <span class="mv-model-name">{{ slot.shortName }}</span>
                  <span class="mv-model-meta">{{ slot.totalFrames }} 帧 · {{ slot.framerate }} fps</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Tab: 设置 -->
          <div v-show="activeTab === 'settings'" class="mv-section">
            <div class="mv-sb-title">播放速度</div>
            <div class="mv-speed-row">
              <button v-for="s in [0.25, 0.5, 1, 1.5, 2, 3]" :key="s" class="mv-speed-btn" :class="{ active: playSpeed === s }" @click="playSpeed = s">{{ s }}x</button>
            </div>
            <div class="mv-sb-title" style="margin-top:14px">显示选项</div>
            <label class="mv-check"><input type="checkbox" v-model="showMesh" @change="onDispToggle">网格</label>
            <label class="mv-check"><input type="checkbox" v-model="showSkeleton" @change="onDispToggle">骨架</label>
            <label class="mv-check"><input type="checkbox" v-model="wireframe" @change="onDispToggle">线框</label>
            <label class="mv-check"><input type="checkbox" v-model="showGround" @change="onDispToggle">地面</label>
          </div>

          <!-- Tab: 标注 -->
          <div v-show="activeTab === 'annotation'" class="mv-section mv-annotation-tab">
            <div v-if="!currentAnnotation && !annotationLoading" class="mv-empty-hint">加载标注中...</div>

            <template v-if="currentAnnotation">
              <!-- 质量评级 -->
              <div class="mv-rating-row">
                <button v-for="opt in QUALITY_RATING_OPTIONS" :key="opt.value" class="mv-rating-btn" :class="{ active: annotLocal.qualityRating === opt.value }" :style="annotLocal.qualityRating === opt.value ? { background: opt.color, borderColor: opt.color, color: '#fff' } : {}" @click="annotLocal.qualityRating = annotLocal.qualityRating === opt.value ? null : opt.value">
                  {{ opt.label }}
                </button>
              </div>

              <!-- 帧级标注（主体） -->
              <div class="mv-sb-title" style="margin-top:10px">
                帧级标注 ({{ annotLocal.frameIssues.length }})
                <button class="mv-add-issue-btn" @click="openCreateModal(currentFrame)">+新增</button>
              </div>
              <div v-if="annotLocal.frameIssues.length" class="mv-issue-list">
                <div
                  v-for="(issue, idx) in annotLocal.frameIssues" :key="idx"
                  class="mv-issue-card"
                  :style="{ borderLeftColor: severityColor(issue.severity) }"
                >
                  <div class="mv-issue-left" @click="seekTo(issue.frame)">
                    <span class="mv-issue-frame-badge" :style="{ background: severityColor(issue.severity) }">
                      {{ issue.endFrame ? `${issue.frame}-${issue.endFrame}` : issue.frame }}
                    </span>
                    <span class="mv-issue-type">{{ issue.defectType ? defectLabel(issue.defectType) : (issue.category || '-') }}</span>
                  </div>
                  <div class="mv-issue-right">
                    <button class="mv-issue-edit-btn" @click.stop="openEditModal(idx)" title="编辑">✎</button>
                    <button class="mv-issue-edit-btn" @click.stop="removeFrameIssue(idx)" title="删除">×</button>
                  </div>
                  <div v-if="issue.description" class="mv-issue-desc">{{ issue.description }}</div>
                </div>
              </div>
              <div v-else class="mv-empty-hint">
                暂无帧级标注
                <div style="font-size:10px;color:#666;margin-top:2px">按 N 标记当前帧，或在时间轴拖拽选区</div>
              </div>

              <!-- 动作属性（可折叠） -->
              <button class="mv-collapse-btn" @click="propsExpanded = !propsExpanded" style="margin-top:10px">
                {{ propsExpanded ? '▼' : '▶' }} 动作属性
              </button>
              <div v-show="propsExpanded" class="mv-props-area">
                <!-- MotionDB 缺陷评估 -->
                <div class="mv-sb-subtitle">缺陷评估</div>
                <div class="mv-defect-list">
                  <div v-for="field in MOTIONDB_DEFECT_FIELDS" :key="field.key" class="mv-defect-row">
                    <span class="mv-defect-label">{{ field.label }}</span>
                    <span v-if="field.type === 'yn'" class="mv-defect-yn">
                      <button class="mv-yn-btn" :class="{ active: annotLocal.motiondbDefects[field.key] === 'Y' }" @click="annotLocal.motiondbDefects[field.key] = 'Y'">是</button>
                      <button class="mv-yn-btn" :class="{ active: annotLocal.motiondbDefects[field.key] === 'N' }" @click="annotLocal.motiondbDefects[field.key] = 'N'">否</button>
                    </span>
                    <span v-else-if="field.type === 'three'" class="mv-defect-three">
                      <button v-for="lv in ['0','1','2']" :key="lv" class="mv-yn-btn" :class="{ active: annotLocal.motiondbDefects[field.key] === lv }" @click="annotLocal.motiondbDefects[field.key] = lv">{{ lv === '0' ? '无' : lv === '1' ? '轻微' : '明显' }}</button>
                    </span>
                    <span v-else class="mv-defect-speed">
                      <select v-model="annotLocal.motiondbDefects[field.key]" class="mv-sel-sm">
                        <option value="slow">慢</option>
                        <option value="normal">正常</option>
                        <option value="fast">快</option>
                      </select>
                    </span>
                  </div>
                </div>

                <!-- 动作标签 -->
                <div class="mv-sb-subtitle" style="margin-top:8px">动作标签</div>
                <div class="mv-tags-grid">
                  <button v-for="tag in MOTION_TAG_OPTIONS" :key="tag" class="mv-tag-btn" :class="{ active: annotLocal.motionTags.includes(tag) }" @click="toggleTag(tag)">{{ tag }}</button>
                </div>

                <!-- 文本描述 -->
                <div class="mv-sb-subtitle" style="margin-top:8px">文本描述</div>
                <div class="mv-text-list">
                  <div v-for="(txt, idx) in annotLocal.textDescriptions" :key="idx" class="mv-text-row">
                    <input v-model="annotLocal.textDescriptions[idx]" class="mv-input-sm" placeholder="描述..." />
                    <button class="mv-issue-del" @click="annotLocal.textDescriptions.splice(idx, 1)">×</button>
                  </div>
                  <button class="mv-add-text-btn" @click="annotLocal.textDescriptions.push('')">+ 添加描述</button>
                </div>
              </div>

              <!-- 保存 -->
              <button class="mv-save-btn" @click="handleAnnotationSave" :disabled="annotationSaving">
                {{ annotationSaving ? '保存中...' : '保存标注' }}
              </button>
              <p v-if="annotationMsg" class="mv-annotation-msg">{{ annotationMsg }}</p>
            </template>
          </div>

          <!-- Tab: 信息 -->
          <div v-show="activeTab === 'info'" class="mv-section">
            <div class="mv-sb-title">Session 信息</div>
            <dl class="mv-meta-list">
              <dt>Session</dt><dd>{{ sessionId }}</dd>
              <dt>动作数</dt><dd>{{ modelSlots.length }}</dd>
              <dt>标注进度</dt><dd>{{ annotationProgressText }}</dd>
            </dl>
            <template v-if="currentAnnotation">
              <div class="mv-sb-title" style="margin-top:12px">当前标注</div>
              <dl class="mv-meta-list">
                <dt>状态</dt><dd>{{ ANNOTATION_STATUS_LABELS[currentAnnotation.status] || currentAnnotation.status }}</dd>
                <dt>评级</dt><dd>{{ currentAnnotation.qualityRating || '-' }}</dd>
                <dt>标注人</dt><dd>{{ currentAnnotation.annotatorName || '-' }}</dd>
                <dt>更新时间</dt><dd>{{ currentAnnotation.updatedAt?.slice(0, 16) || '-' }}</dd>
              </dl>
            </template>
          </div>
        </div>
      </aside>

      <!-- 3D 画布 -->
      <div ref="canvasWrap" class="mv-canvas-wrap" />
    </div>

    <!-- 底部双层时间轴 -->
    <MotionTimeline
      v-if="currentModel"
      :total-frames="totalFrames"
      :current-frame="currentFrame"
      :framerate="currentFramerate"
      :frame-issues="currentAnnotation?.frameIssues ?? []"
      :playing="playing"
      :loop="loopPlayback"
      @seek="seekTo"
      @create-annotation="onTimelineCreateAnnotation"
      @select-annotation="onTimelineSelectAnnotation"
      @toggle-play="togglePlay"
      @step-forward="stepForward"
      @step-backward="stepBackward"
      @skip-forward="skipForward"
      @skip-backward="skipBackward"
      @toggle-loop="loopPlayback = !loopPlayback"
    />

    <!-- 标注编辑 Modal -->
    <AnnotationModal
      :open="modalOpen"
      :mode="modalMode"
      :default-frame="modalDefaultFrame"
      :default-end-frame="modalDefaultEndFrame"
      :edit-issue="editingIssueIndex != null ? annotLocal.frameIssues[editingIssueIndex] : null"
      :total-frames="totalFrames"
      @close="modalOpen = false"
      @save="onModalSave"
      @delete="onModalDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, reactive, watch } from "vue";
import { useRoute } from "vue-router";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import MotionTimeline from "@/components/timeline/MotionTimeline.vue";
import AnnotationModal from "@/components/AnnotationModal.vue";
import { fetchAnnotationByFileId, saveAnnotation } from "@/api/annotation";
import type { MotionAnnotation, MotionAnnotationRequest, FrameIssueItem, MotiondbDefects } from "@/types/annotation";
import { QUALITY_RATING_OPTIONS, MOTION_TAG_OPTIONS, MOTIONDB_DEFECT_FIELDS, DEFAULT_MOTIONDB_DEFECTS, SEVERITY_OPTIONS, ANNOTATION_STATUS_LABELS } from "@/types/annotation";

const route = useRoute();

// ── 模型槽位 ──
interface ModelSlot {
  id: number; fileId: number; fileName: string; shortName: string;
  mesh: THREE.SkinnedMesh; bones: THREE.Bone[]; skeletonHelper: THREE.Group;
  frameDataList: any[]; totalFrames: number; framerate: number; color: string;
}

// ── UI 状态 ──
const sidebarOpen = ref(true);
const tabs = [
  { key: "models", label: "动作" },
  { key: "settings", label: "设置" },
  { key: "annotation", label: "标注" },
  { key: "info", label: "信息" },
] as const;
const activeTab = ref<string>("models");

// ── 播放状态 ──
const loading = ref(true); const error = ref(""); const loadingStep = ref("初始化...");
const loadTotal = ref(0); const loadDone = ref(0);
const loadProgress = computed(() => loadTotal.value ? Math.round(loadDone.value / loadTotal.value * 100) : 0);
const modelSlots = ref<ModelSlot[]>([]); const selectedId = ref(-1);
const playing = ref(false); const currentFrame = ref(0);
const playSpeed = ref(1); const loopPlayback = ref(true);
const showMesh = ref(true); const showSkeleton = ref(true); const wireframe = ref(false); const showGround = ref(true);
const rootEl = ref<HTMLElement>(); const canvasWrap = ref<HTMLElement>();
const sessionId = computed(() => route.params.sessionId as string);
const currentModel = computed(() => modelSlots.value.find(s => s.id === selectedId.value));
const totalFrames = computed(() => { const m = currentModel.value; return m ? m.frameDataList.length : 0; });
const currentFramerate = computed(() => currentModel.value?.framerate ?? 120);

// ── 标注状态 ──
const currentAnnotation = ref<MotionAnnotation | null>(null);
const annotationLoading = ref(false);
const annotationSaving = ref(false);
const annotationMsg = ref("");
const currentAssetFileId = ref<number | null>(null);

// ── Modal 状态 ──
const modalOpen = ref(false);
const modalMode = ref<"create" | "edit">("create");
const editingIssueIndex = ref<number | null>(null);
const modalDefaultFrame = ref(0);
const modalDefaultEndFrame = ref<number | undefined>(undefined);
const markStartFrame = ref<number | null>(null);
const propsExpanded = ref(false);

const defectLabels: Record<string, string> = {
  jointJump: "关节跳变", sliding: "脚底滑动", jointDeformity: "关节畸形",
  floatPenetrate: "漂浮/穿透", displacementMissing: "位移缺失",
  flatScene: "平地场景", temporalConsistency: "时间异常", other: "其他",
};
function defectLabel(type: string) { return defectLabels[type] || type; }

const annotLocal = reactive<{
  qualityRating: string | null;
  motiondbDefects: MotiondbDefects;
  motionTags: string[];
  frameIssues: FrameIssueItem[];
  textDescriptions: string[];
}>({
  qualityRating: null,
  motiondbDefects: { ...DEFAULT_MOTIONDB_DEFECTS },
  motionTags: [],
  frameIssues: [],
  textDescriptions: [],
});

watch(() => currentAnnotation.value, (ann) => {
  if (ann) {
    annotLocal.qualityRating = ann.qualityRating;
    annotLocal.motiondbDefects = ann.motiondbDefects ? { ...DEFAULT_MOTIONDB_DEFECTS, ...ann.motiondbDefects } : { ...DEFAULT_MOTIONDB_DEFECTS };
    annotLocal.motionTags = [...ann.motionTags];
    annotLocal.frameIssues = ann.frameIssues.map(f => ({ ...f }));
    annotLocal.textDescriptions = [...(ann.textDescriptions || [])];
    annotationMsg.value = "";
  }
}, { immediate: true });

const annotationProgressText = computed(() => {
  if (!currentAnnotation.value) return "-";
  return ANNOTATION_STATUS_LABELS[currentAnnotation.value.status] || currentAnnotation.value.status;
});

function severityColor(s: string) { return SEVERITY_OPTIONS.find(o => o.value === s)?.color ?? "#f59e0b"; }
function toggleTag(tag: string) {
  const idx = annotLocal.motionTags.indexOf(tag);
  idx >= 0 ? annotLocal.motionTags.splice(idx, 1) : annotLocal.motionTags.push(tag);
}
// ── 标注 Modal 操作 ──
function openCreateModal(defaultFrame: number, defaultEndFrame?: number) {
  modalMode.value = "create";
  editingIssueIndex.value = null;
  modalDefaultFrame.value = defaultFrame;
  modalDefaultEndFrame.value = defaultEndFrame;
  modalOpen.value = true;
}

function openEditModal(idx: number) {
  modalMode.value = "edit";
  editingIssueIndex.value = idx;
  modalOpen.value = true;
}

function onModalSave(issue: FrameIssueItem) {
  if (modalMode.value === "edit" && editingIssueIndex.value != null) {
    annotLocal.frameIssues.splice(editingIssueIndex.value, 1, issue);
  } else {
    annotLocal.frameIssues.push(issue);
  }
  modalOpen.value = false;
}

function onModalDelete() {
  if (editingIssueIndex.value != null) {
    annotLocal.frameIssues.splice(editingIssueIndex.value, 1);
  }
  modalOpen.value = false;
}

function onTimelineCreateAnnotation(startFrame: number, endFrame: number) {
  openCreateModal(startFrame, endFrame);
}

// ── Three.js ──
let renderer: THREE.WebGLRenderer | null = null; let scene: THREE.Scene | null = null;
let camera: THREE.PerspectiveCamera | null = null; let controls: OrbitControls | null = null;
let groundGrid: THREE.GridHelper | null = null; let animFrameId = 0; let startTime = 0;
const COLORS = ["#6495ED", "#FF6B81", "#4CAF50", "#FF9800", "#9C27B0", "#00BCD4", "#FFEB3B", "#9E9E9E"];
const modelCache = new Map<string, { vTemplate: Float32Array; faces: Uint16Array; skinWeights: Float32Array; skinIndices: Uint16Array; keypoints: Float32Array; offsets: Float32Array[]; offsetsJ: (Float32Array | null)[] }>();
const SMPL_EDGES: Record<string, number[]> = {
  smpl:  [-1,0,0,0,1,2,3,4,5,6,7,8,9,9,9,12,13,14,16,17,18,19,20,21],
  smplh: [-1,0,0,0,1,2,3,4,5,6,7,8,9,9,9,12,13,14,16,17,18,19,20,22,23,20,25,26,20,28,29,20,31,32,20,34,35,21,37,38,21,40,41,21,43,44,21,46,47,21,49,50],
  smplx: [-1,0,0,0,1,2,3,4,5,6,7,8,9,9,9,12,13,14,16,17,18,19,15,15,15,20,25,26,20,28,29,20,31,32,20,34,35,20,37,38,21,40,41,21,43,44,21,46,47,21,49,50,21,52,53],
};

function getAssetDir(st: string, g: string) { let d = `dump_${st}`; if (g === "male") d += "_male"; else if (g === "female") d += "_female"; return `/assets/${d}`; }

async function loadCachedGroup(st: string, g: string) {
  const key = `${st}_${g}`; if (modelCache.has(key)) return modelCache.get(key)!;
  const base = getAssetDir(st, g);
  const fb = async (n: string) => { const url=`${base}/${n}`; const mc=await caches.open("motion-viewer-v1"); let r=await mc.match(url); if(!r){ r=await fetch(url); if(r.ok)mc.put(url,r.clone()); } if (!r.ok) { if (n === "j_template.bin") { const r2=await fetch(`${base}/keypoints.bin`); return r2.arrayBuffer(); } throw new Error(`${n}: ${r.status}`); } return r.arrayBuffer(); };
  const [v,f,sw,si,kp] = await Promise.all([fb("v_template.bin"),fb("faces.bin"),fb("skinWeights.bin"),fb("skinIndice.bin"),fb("j_template.bin")]);
  const offs: Float32Array[] = [], offsJ: (Float32Array | null)[] = [];
  for (let i=0;i<16;i++) { try { offs.push(new Float32Array(await fb(`shapeoffset_${i}.bin`))); } catch { offs.push(new Float32Array()); } }
  for (let i=0;i<16;i++) { try { offsJ.push(new Float32Array(await fb(`shapeoffset_j_${i}.bin`))); } catch { offsJ.push(null); } }
  const data = { vTemplate:new Float32Array(v), faces:new Uint16Array(f), skinWeights:new Float32Array(sw), skinIndices:new Uint16Array(si), keypoints:new Float32Array(kp), offsets:offs, offsetsJ:offsJ };
  modelCache.set(key, data); return data;
}

type ModelCacheData = Awaited<ReturnType<typeof loadCachedGroup>>;

function createSkinnedMesh(cache: ModelCacheData, shapes: number[], st: string, color: string) {
  const s = shapes.slice(0,16); while(s.length<16)s.push(0);
  const vt = new Float32Array(cache.vTemplate);
  for (let i=0;i<16;i++) { const o = cache.offsets[i]; if (!o?.length) continue; for (let j=0;j<vt.length/3;j++) { vt[3*j]+=o[3*j]*s[i]; vt[3*j+1]+=o[3*j+1]*s[i]; vt[3*j+2]+=o[3*j+2]*s[i]; } }
  const kp = new Float32Array(cache.keypoints);
  for (let i=0;i<16;i++) { const o = cache.offsetsJ[i]; if (!o?.length) continue; for (let j=0;j<kp.length/3;j++) { kp[3*j]+=o[3*j]*s[i]; kp[3*j+1]+=o[3*j+1]*s[i]; kp[3*j+2]+=o[3*j+2]*s[i]; } }
  const edges = SMPL_EDGES[st]||SMPL_EDGES["smpl"];
  const rb = new THREE.Bone(); rb.position.set(kp[0],kp[1],kp[2]);
  const bones:THREE.Bone[]=[rb]; const nj=Math.min(kp.length/3,edges.length);
  for (let i=1;i<nj;i++){ const b=new THREE.Bone(); const p=edges[i]; b.position.set(kp[3*i]-kp[3*p],kp[3*i+1]-kp[3*p+1],kp[3*i+2]-kp[3*p+2]); bones.push(b); bones[p].add(b); }
  const sk=new THREE.Skeleton(bones);
  const geo=new THREE.BufferGeometry(); geo.setIndex(new THREE.BufferAttribute(cache.faces,1)); geo.setAttribute("position",new THREE.BufferAttribute(vt,3));
  geo.setAttribute("skinIndex",new THREE.BufferAttribute(cache.skinIndices,4)); geo.setAttribute("skinWeight",new THREE.BufferAttribute(cache.skinWeights,4)); geo.computeVertexNormals();
  const mat=new THREE.MeshStandardMaterial({color:new THREE.Color(color),side:THREE.DoubleSide,skinning:true,emissive:0x222222,roughness:.7,metalness:.3} as any);
  const m=new THREE.SkinnedMesh(geo,mat); m.castShadow=m.receiveShadow=true; m.frustumCulled=false; m.add(bones[0]); m.bind(sk);
  (m as any).userData.bones=bones; return {mesh:m,bones,skeleton:sk};
}

function createSkeletonHelper(bones:THREE.Bone[],st:string,color:string){
  const g=new THREE.Group(); const sg=new THREE.SphereGeometry(.008,8,8); const jm=new THREE.MeshBasicMaterial({color:new THREE.Color(color)});
  for(const b of bones){ const d=new THREE.Mesh(sg,jm); d.userData.isSkelHelper=true; b.add(d); }
  const edges=SMPL_EDGES[st]||SMPL_EDGES["smpl"]; const cm=new THREE.MeshBasicMaterial({color:new THREE.Color(color).multiplyScalar(.6)});
  for(let i=1;i<Math.min(bones.length,edges.length);i++){ const p=edges[i]; if(p<0)continue; const cb=bones[i]; const dist=cb.position.length(); if(dist<.001)continue;
    const cg=new THREE.CylinderGeometry(.003,.005,dist,6); const c=new THREE.Mesh(cg,cm); c.userData.isSkelHelper=true; c.position.copy(cb.position.clone().multiplyScalar(.5));
    c.setRotationFromQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),cb.position.clone().normalize())); bones[p]?.add(c); }
  return g;
}

function aa2q(rx:number,ry:number,rz:number){ const a=new THREE.Vector3(rx,ry,rz); const ang=a.length(); if(ang<1e-10)return new THREE.Quaternion(0,0,0,1); a.normalize(); return new THREE.Quaternion().setFromAxisAngle(a,ang); }

function updateFrame(slot:ModelSlot, frameObj:any){
  const fd=frameObj; const pa:number[]=fd.poses[0]; const rh=fd.Rh[0]; const th=fd.Th[0];
  slot.bones[0].quaternion.copy(aa2q(rh[0],rh[1],rh[2])); slot.mesh.position.set(th[0],th[1],th[2]);
  const off=pa.length===69?-3:0;
  for(let i=1;i<slot.bones.length;i++){ const idx=off+3*i; if(idx+2>=pa.length)break; slot.bones[i].quaternion.copy(aa2q(pa[idx],pa[idx+1],pa[idx+2])); }
}

function selectModel(id:number){
  if(selectedId.value===id)return;
  const prev=modelSlots.value.find(s=>s.id===selectedId.value);
  if(prev){ prev.mesh.visible=false; prev.skeletonHelper.visible=false; }
  selectedId.value=id;
  const next=modelSlots.value.find(s=>s.id===id);
  if(next){ next.mesh.visible=showMesh.value; next.skeletonHelper.visible=showSkeleton.value; if(next.frameDataList[0]) updateFrame(next, next.frameDataList[0]); }
  currentFrame.value=0; if(playing.value)startTime=performance.now();
  applyDispFlags(); loadCurrentAnnotation();
}

function applyDispFlags(){
  const m=currentModel.value; if(!m)return;
  m.mesh.visible=showMesh.value;
  const mats=Array.isArray(m.mesh.material)?m.mesh.material:[m.mesh.material];
  mats.forEach(mt=>{ if(mt instanceof THREE.MeshStandardMaterial)mt.wireframe=wireframe.value; });
  m.skeletonHelper.visible=showSkeleton.value;
  if(groundGrid)groundGrid.visible=showGround.value;
}
function onDispToggle(){ applyDispFlags(); }

function animate(){
  animFrameId=requestAnimationFrame(animate);
  if(!renderer||!scene||!camera)return;
  if(playing.value&&totalFrames.value>1){
    const fps=currentModel.value?.framerate??120; const spd=playSpeed.value;
    const elapsed=(performance.now()-startTime)/1000;
    let frame=Math.floor(elapsed*(fps*spd));
    if(loopPlayback.value&&frame>=totalFrames.value){ frame=0;startTime=performance.now(); }
    else if(!loopPlayback.value&&frame>=totalFrames.value-1){ frame=totalFrames.value-1; playing.value=false; }
    currentFrame.value=Math.max(0,Math.min(frame,totalFrames.value-1));
    const m=currentModel.value;
    if(m&&m.frameDataList[currentFrame.value]) updateFrame(m,m.frameDataList[currentFrame.value]);
  }
  controls?.update(); renderer.render(scene,camera);
}

function togglePlay(){ playing.value=!playing.value; if(playing.value)startTime=performance.now()-(currentFrame.value/((currentModel.value?.framerate??120)*(playSpeed.value||1)))*1000; }
function seekTo(f:number){ currentFrame.value=Math.max(0,Math.min(f,totalFrames.value-1)); const m=currentModel.value; if(m?.frameDataList[currentFrame.value])updateFrame(m,m.frameDataList[currentFrame.value]); if(playing.value)startTime=performance.now()-(currentFrame.value/((currentModel.value?.framerate??120)*(playSpeed.value||1)))*1000; }
function stepForward(){ seekTo(currentFrame.value+1); }
function stepBackward(){ seekTo(currentFrame.value-1); }
function skipForward(){ seekTo(currentFrame.value+Math.round(currentModel.value?.framerate??120)); }
function skipBackward(){ seekTo(currentFrame.value-Math.round(currentModel.value?.framerate??120)); }

function onKeyDown(e:KeyboardEvent){
  if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;
  switch(e.key){ case" ":e.preventDefault();togglePlay();break;
    case"ArrowLeft":e.preventDefault();stepBackward();break;
    case"ArrowRight":e.preventDefault();stepForward();break;
    case"ArrowUp":playSpeed.value=Math.min(3,playSpeed.value+.25);break;
    case"ArrowDown":playSpeed.value=Math.max(.25,playSpeed.value-.25);break;
    case"Escape":if(modalOpen.value){modalOpen.value=false;}else{close();}break;
    case"a":case"A":e.preventDefault();activeTab.value="annotation";break;
    case"n":case"N":e.preventDefault();openCreateModal(currentFrame.value);break;
    case"m":case"M":e.preventDefault();if(markStartFrame.value==null){markStartFrame.value=currentFrame.value;}else{openCreateModal(markStartFrame.value,currentFrame.value);markStartFrame.value=null;}break; }
}
function close(){ window.close(); }

// ── 标注逻辑 ──
async function loadCurrentAnnotation() {
  const slot = currentModel.value; if (!slot) return;
  const fileId = slot.fileId;
  if (currentAssetFileId.value === fileId && currentAnnotation.value) return;
  currentAssetFileId.value = fileId; annotationLoading.value = true;
  try { currentAnnotation.value = await fetchAnnotationByFileId(fileId); }
  catch (e: any) { console.warn("[MotionViewer] 加载标注失败:", e.message); currentAnnotation.value = null; }
  finally { annotationLoading.value = false; }
}

async function handleAnnotationSave() {
  if (!currentAnnotation.value) return;
  annotationSaving.value = true; annotationMsg.value = "";
  const data: MotionAnnotationRequest = {
    status: annotLocal.qualityRating || annotLocal.motionTags.length > 0 || annotLocal.frameIssues.length > 0 ? "ANNOTATED" : "IN_PROGRESS",
    qualityRating: annotLocal.qualityRating as any,
    motionTags: annotLocal.motionTags,
    motiondbDefects: annotLocal.motiondbDefects,
    frameIssues: annotLocal.frameIssues,
    textDescriptions: annotLocal.textDescriptions.filter(t => t.trim()),
    overallComment: currentAnnotation.value.overallComment,
    version: currentAnnotation.value.version,
  };
  try {
    const updated = await saveAnnotation(currentAnnotation.value.assetId, data);
    currentAnnotation.value = updated;
    annotationMsg.value = "已保存";
    setTimeout(() => annotationMsg.value = "", 2000);
  } catch (e: any) { annotationMsg.value = "保存失败: " + (e?.message || String(e)); }
  finally { annotationSaving.value = false; }
}

function onTimelineSelectAnnotation(issue: FrameIssueItem) {
  activeTab.value = "annotation";
  const idx = annotLocal.frameIssues.findIndex(
    (f) => f.frame === issue.frame && f.endFrame === issue.endFrame
  );
  if (idx >= 0) openEditModal(idx);
}

function removeFrameIssue(idx: number) { annotLocal.frameIssues.splice(idx, 1); }

function initScene(){
  const wrap=canvasWrap.value!; const w=wrap.clientWidth,h=wrap.clientHeight;
  renderer=new THREE.WebGLRenderer({antialias:true,alpha:false,preserveDrawingBuffer:false});
  renderer.setPixelRatio(Math.min(devicePixelRatio,2)); renderer.setSize(w,h); renderer.shadowMap.enabled=true; renderer.shadowMap.type=THREE.PCFSoftShadowMap;
  renderer.setClearColor(0x141414); renderer.domElement.style.display="block"; wrap.appendChild(renderer.domElement);
  scene=new THREE.Scene(); scene.background=new THREE.Color(0x141414);
  camera=new THREE.PerspectiveCamera(45,w/h,.01,100); camera.position.set(0,1.1,4); camera.lookAt(0,.7,0);
  controls=new OrbitControls(camera,renderer.domElement); controls.target.set(0,.7,0); controls.enableDamping=true; controls.dampingFactor=.1; controls.update();
  scene.add(new THREE.AmbientLight(0x606080,1));
  const kl=new THREE.DirectionalLight(0xffffff,1.2); kl.position.set(5,10,5); kl.castShadow=true; scene.add(kl);
  const fl=new THREE.DirectionalLight(0x8888cc,.4); fl.position.set(-3,3,-3); scene.add(fl);
  groundGrid=new THREE.GridHelper(10,20,0x444466,0x222244); scene.add(groundGrid);
  addEventListener("resize",()=>{ const nw=wrap.clientWidth,nh=wrap.clientHeight; if(camera){camera.aspect=nw/nh;camera.updateProjectionMatrix();} renderer?.setSize(nw,nh); });
}

async function initAll(){
  loading.value=true; error.value=""; loadingStep.value="正在获取会话数据..."; loadTotal.value=0; loadDone.value=0;
  try{
    const sid=Number(route.params.sessionId); if(!sid||isNaN(sid))throw new Error("无效的sessionId");
    loadingStep.value="正在获取文件列表...";
    const fr=await fetch(`/api/sessions/${sid}/files`); if(!fr.ok)throw new Error(`文件列表失败:${fr.status}`);
    const fw=await fr.json(); const allFiles:any[]=(fw as any)?.data||fw||[];
    const viewerFiles=allFiles.filter((f:any)=>f.assetType==="MOTION_VIEWER_JSON"&&f.id);
    if(!viewerFiles.length)throw new Error("该会话没有 3D 查看数据，请先运行「生成3D查看数据」");
    loadTotal.value=viewerFiles.length; loadDone.value=0;
    const cache=await caches.open("motion-viewer-v1");
    const fileDataArr=await Promise.all(viewerFiles.map(async (vf:any)=>{
      const url=`/api/files/${vf.id}/download`;
      let r=await cache.match(url); if(!r){ r=await fetch(url); if(r.ok)cache.put(url,r.clone()); }
      if(!r.ok)throw new Error(`下载 fileId=${vf.id} 失败:${r.status}`);
      const d=await r.json(); loadDone.value++; return {fileId:vf.id,data:d};
    }));
    const groups=new Map<string,{fileId:number;data:any}[]>();
    for(const fd of fileDataArr){ const k=`${fd.data.smpl_type||"smplh"}_${fd.data.gender||"neutral"}`; if(!groups.has(k))groups.set(k,[]); groups.get(k)!.push(fd); }
    const slots:ModelSlot[]=[]; let ci=0;
    loadingStep.value="正在加载 SMPL 模型..."; loadDone.value=0; loadTotal.value=groups.size;
    for(const [key,group] of groups){
      const [st,g]=key.split("_"); const mc=await loadCachedGroup(st,g); loadDone.value++;
      for(const fd of group){
        const fl=fd.data.frames.map((f:any)=>f[0]); const ff=fl[0]; const shapes:number[]=ff.shapes||[]; const color=COLORS[ci%COLORS.length];
        const {mesh,bones}=createSkinnedMesh(mc,shapes,st,color);
        const skel=createSkeletonHelper(bones,st,color); mesh.add(skel);
        const name=fd.data.filename||`Motion #${fd.fileId}`;
        const short=name.replace(/_poses(_viewer)?/,"").replace(/_/g," ").replace(/__+/g," ").replace(/\.json$/,"").slice(0,40);
        const slot:ModelSlot={id:slots.length,fileId:fd.fileId,fileName:name,shortName:short,mesh,bones,skeletonHelper:skel,frameDataList:fl,totalFrames:fd.data.frame_count,framerate:fd.data.framerate||120,color};
        mesh.visible=false; skel.visible=false; slots.push(slot); ci++;
      }
    }
    modelSlots.value=slots; if(!slots.length)throw new Error("无可用模型");
    loading.value=false; loadingStep.value=""; await nextTick(); initScene();
    for(const s of slots) scene!.add(s.mesh);
    selectModel(0);
    const m=currentModel.value!; const bbox=new THREE.Box3().setFromObject(m.mesh); const center=new THREE.Vector3(); bbox.getCenter(center);
    const targetY=Math.max(center.y,.6); const distZ=Math.max(bbox.max.z-bbox.min.z+2,3.5);
    controls!.target.set(center.x,targetY,center.z); camera!.position.set(center.x,targetY+.6,center.z+distZ);
    camera!.lookAt(controls!.target); controls!.update();
    await nextTick(); rootEl.value?.focus(); animate();
  }catch(e:any){ console.error("[MotionViewer]",e); error.value=`${loadingStep.value}\n${e.message||String(e)}`; loading.value=false; loadingStep.value=""; }
}

onMounted(()=>{ initAll(); });
onBeforeUnmount(()=>{ cancelAnimationFrame(animFrameId); renderer?.dispose(); renderer?.domElement.remove(); scene?.traverse(o=>{ if(o instanceof THREE.Mesh){o.geometry?.dispose(); if(Array.isArray(o.material))o.material.forEach(m=>m.dispose()); else o.material?.dispose();}}); });
</script>

<style>
html, body, #app { height: 100%; margin: 0; }
</style>

<style scoped>
.mv-root { display:flex; flex-direction:column; height:100vh; background:#141414; color:#d4d4d4; font-family:system-ui,-apple-system,sans-serif; overflow:hidden; }
.mv-loading,.mv-error { position:fixed; inset:0; display:flex; flex-direction:column; align-items:center; justify-content:center; background:#141414; color:#e0e0e0; z-index:9999; gap:16px; }
.mv-loading-spinner { width:40px; height:40px; border:3px solid rgba(255,255,255,.1); border-top-color:#2563eb; border-radius:50%; animation:spin .8s linear infinite; }
@keyframes spin { to{transform:rotate(360deg)} }
.mv-progress-bar { width:240px; height:4px; background:rgba(255,255,255,.1); border-radius:2px; overflow:hidden; }
.mv-progress-fill { height:100%; background:#2563eb; transition:width .3s; }
.mv-progress-text { font-size:12px; color:#999; }

/* 顶部栏 */
.mv-topbar { display:flex; align-items:center; gap:14px; padding:8px 16px; background:#1e1e1e; border-bottom:1px solid #333; flex-shrink:0; }
.mv-back { background:none; border:1px solid #555; color:#aaa; padding:4px 12px; border-radius:4px; cursor:pointer; font-size:13px; }
.mv-back:hover { background:#333; color:#fff; }
.mv-info { font-size:13px; display:flex; align-items:center; gap:6px; flex:1; min-width:0; }
.mv-session { color:#4ade80; font-weight:600; }
.mv-sep { color:#555; }
.mv-kb-hint { font-size:10px; color:#555; }
.mv-toggle-sidebar { background:none; border:1px solid #555; color:#aaa; padding:4px 12px; border-radius:4px; cursor:pointer; font-size:13px; margin-left:auto; }
.mv-toggle-sidebar:hover { background:#333; color:#fff; }

/* 主体 */
.mv-body { display:flex; flex:1; min-height:0; overflow:hidden; }

/* 侧边栏 */
.mv-sidebar { width:240px; flex-shrink:0; background:#1e1e1e; border-right:1px solid #333; display:flex; flex-direction:column; transition:width .2s; overflow:hidden; }
.mv-sidebar.collapsed { width:0; border:none; }
.mv-tab-nav { display:flex; border-bottom:1px solid #333; flex-shrink:0; }
.mv-tab-btn { flex:1; padding:8px 0; background:none; border:none; color:#888; font-size:12px; cursor:pointer; font-family:inherit; border-bottom:2px solid transparent; transition:all .12s; }
.mv-tab-btn:hover { color:#ccc; }
.mv-tab-btn.active { color:#e0e0e0; border-bottom-color:#2563eb; }
.mv-tab-content { flex:1; overflow-y:auto; padding:10px 12px; }
.mv-section { display:flex; flex-direction:column; gap:8px; }
.mv-sb-title { font-size:11px; font-weight:600; text-transform:uppercase; color:#888; letter-spacing:.05em; display:flex; align-items:center; gap:6px; }

/* 动作列表 */
.mv-model-list { display:flex; flex-direction:column; gap:2px; }
.mv-model-item { display:flex; align-items:center; gap:8px; padding:6px 8px; cursor:pointer; border-radius:4px; border-left:3px solid transparent; transition:background .1s; }
.mv-model-item:hover { background:rgba(255,255,255,.04); }
.mv-model-item-active { background:rgba(37,99,235,.1); border-left-color:#2563eb; }
.mv-model-dot { width:8px; height:8px; border-radius:50%; flex-shrink:0; }
.mv-model-info { display:flex; flex-direction:column; gap:1px; min-width:0; }
.mv-model-name { font-size:12px; font-weight:500; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.mv-model-meta { font-size:10px; color:#777; }

/* 设置 */
.mv-speed-row { display:flex; gap:4px; flex-wrap:wrap; }
.mv-speed-btn { background:#2a2a2a; border:1px solid #444; color:#aaa; padding:3px 8px; border-radius:3px; cursor:pointer; font-size:11px; font-family:inherit; }
.mv-speed-btn.active { background:#2563eb; border-color:#2563eb; color:#fff; }
.mv-check { display:flex; align-items:center; gap:6px; font-size:12px; color:#bbb; cursor:pointer; }
.mv-check input { accent-color:#2563eb; }

/* 标注 Tab */
.mv-annotation-tab { gap:6px; }
.mv-empty-hint { font-size:11px; color:#555; text-align:center; padding:12px 0; }

.mv-rating-row { display:flex; gap:4px; }
.mv-rating-btn { flex:1; padding:5px 0; border:1px solid rgba(255,255,255,.1); border-radius:5px; background:rgba(255,255,255,.04); color:#ccc; font-size:12px; font-weight:600; cursor:pointer; transition:all .1s; font-family:inherit; }
.mv-rating-btn.active { border-color:transparent; }

.mv-defect-list { display:flex; flex-direction:column; gap:4px; }
.mv-defect-row { display:flex; align-items:center; justify-content:space-between; gap:4px; }
.mv-defect-label { font-size:11px; color:#bbb; flex:1; min-width:0; }
.mv-defect-yn, .mv-defect-three { display:flex; gap:2px; }
.mv-yn-btn { padding:2px 8px; border:1px solid rgba(255,255,255,.1); border-radius:4px; background:rgba(255,255,255,.03); color:#999; font-size:10px; cursor:pointer; font-family:inherit; }
.mv-yn-btn.active { background:rgba(37,99,235,.2); border-color:#2563eb; color:#fff; }
.mv-sel-sm { background:#2a2a2a; border:1px solid #444; color:#ccc; border-radius:4px; padding:2px 6px; font-size:10px; font-family:inherit; }

.mv-tags-grid { display:flex; flex-wrap:wrap; gap:3px; }
.mv-tag-btn { padding:3px 8px; border:1px solid rgba(255,255,255,.06); border-radius:10px; background:rgba(255,255,255,.03); color:#aaa; font-size:10px; cursor:pointer; font-family:inherit; transition:all .1s; }
.mv-tag-btn.active { background:rgba(37,99,235,.25); border-color:#2563eb; color:#fff; }

.mv-add-issue-btn { background:rgba(37,99,235,.15); border:1px solid rgba(37,99,235,.3); color:#5b9cf5; border-radius:4px; padding:0 6px; font-size:13px; cursor:pointer; font-family:inherit; margin-left:auto; }

.mv-collapse-btn { background:none; border:none; color:#888; font-size:11px; cursor:pointer; font-family:inherit; text-align:left; padding:4px 0; }
.mv-collapse-btn:hover { color:#ccc; }
.mv-props-area { display:flex; flex-direction:column; gap:6px; padding-left:4px; }
.mv-sb-subtitle { font-size:10px; font-weight:600; color:#777; text-transform:uppercase; letter-spacing:.04em; }

.mv-issue-list { display:flex; flex-direction:column; gap:4px; }
.mv-issue-card { background:rgba(255,255,255,.02); border:1px solid rgba(255,255,255,.04); border-left:3px solid #f59e0b; border-radius:4px; padding:5px 8px; transition:border-color .1s; }
.mv-issue-card:hover { border-color:rgba(37,99,235,.2); }
.mv-issue-left { display:flex; align-items:center; gap:4px; cursor:pointer; flex:1; min-width:0; }
.mv-issue-frame-badge { padding:1px 5px; border-radius:3px; font-size:10px; color:#fff; font-weight:500; white-space:nowrap; }
.mv-issue-type { font-size:10px; color:#bbb; }
.mv-issue-right { display:flex; gap:2px; }
.mv-issue-edit-btn { background:none; border:none; color:#666; cursor:pointer; font-size:11px; padding:0 3px; }
.mv-issue-edit-btn:hover { color:#ccc; }
.mv-issue-desc { font-size:10px; color:#999; margin-top:3px; padding-left:2px; line-height:1.3; }
.mv-issue-del { background:none; border:none; color:#666; cursor:pointer; font-size:12px; padding:0; }

.mv-text-list { display:flex; flex-direction:column; gap:4px; }
.mv-text-row { display:flex; gap:4px; align-items:center; }
.mv-input-sm { flex:1; background:rgba(255,255,255,.06); border:1px solid rgba(255,255,255,.1); border-radius:4px; color:#e0e0e0; padding:3px 6px; font-size:10px; font-family:inherit; min-width:0; }
.mv-input-sm:focus { outline:none; border-color:#2563eb; }
.mv-add-text-btn { background:none; border:1px dashed rgba(255,255,255,.1); border-radius:4px; color:#888; font-size:10px; padding:4px; cursor:pointer; font-family:inherit; text-align:left; }

.mv-save-btn { margin-top:8px; padding:8px 0; border:none; border-radius:6px; background:#2563eb; color:#fff; font-size:12px; font-weight:600; cursor:pointer; font-family:inherit; }
.mv-save-btn:disabled { opacity:.5; cursor:not-allowed; }
.mv-annotation-msg { font-size:10px; color:#4ade80; text-align:center; }

/* 信息 Tab */
.mv-meta-list { margin:0; font-size:11px; }
.mv-meta-list dt { color:#888; margin-top:4px; }
.mv-meta-list dd { color:#ccc; margin:0 0 2px 0; }

/* 画布 */
.mv-canvas-wrap { flex:1; min-width:0; overflow:hidden; }
</style>
