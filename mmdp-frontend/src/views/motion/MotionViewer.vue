<template>
  <!-- 加载状态 -->
  <div v-if="loading" class="mv-loading">
    <div class="mv-loading-spinner" />
    <p>{{ loadingStep }}</p>
    <div class="mv-progress-bar">
      <div class="mv-progress-fill" :style="{ width: loadProgress + '%' }" />
    </div>
    <p class="mv-progress-text">{{ loadDone }}/{{ loadTotal }}</p>
  </div>

  <!-- 错误 -->
  <div v-else-if="error" class="mv-error">
    <p>{{ error }}</p>
    <button class="mv-btn" @click="initAll">重试</button>
  </div>

  <!-- 主界面 -->
  <div v-else class="mv-root" @keydown="onKeyDown" tabindex="0" ref="rootEl">
    <!-- 顶部工具栏 -->
    <div class="mv-topbar">
      <div class="mv-topbar-left">
        <span class="mv-filename">{{ currentModel?.fileName || '—' }}</span>
        <span class="mv-meta">{{ currentFrame }}/{{ totalFrames }} 帧 · {{ currentFramerate }} fps · {{ playSpeed }}x</span>
      </div>
      <div class="mv-topbar-center">
        <select v-model.number="playSpeed" class="mv-speed-sel">
          <option :value="0.25">0.25x</option><option :value="0.5">0.5x</option>
          <option :value="1">1x</option><option :value="1.5">1.5x</option>
          <option :value="2">2x</option><option :value="3">3x</option>
        </select>
        <label class="mv-toggle"><input type="checkbox" v-model="showMesh" @change="onDispToggle">网格</label>
        <label class="mv-toggle"><input type="checkbox" v-model="showSkeleton" @change="onDispToggle">骨架</label>
        <label class="mv-toggle"><input type="checkbox" v-model="wireframe" @change="onDispToggle">线框</label>
        <label class="mv-toggle"><input type="checkbox" v-model="showGround" @change="onDispToggle">地面</label>
      </div>
      <div class="mv-topbar-right">
        <button class="mv-btn mv-btn-close" @click="close">✕</button>
      </div>
    </div>

    <!-- 主体：侧边栏 + 画布 -->
    <div class="mv-body">
      <!-- 侧边栏 -->
      <aside class="mv-sidebar" :class="{ 'mv-sidebar-collapsed': sidebarCollapsed }">
        <div class="mv-sidebar-hdr">
          <span>{{ modelSlots.length }} 个动作</span>
          <button class="mv-sidebar-toggle" @click="sidebarCollapsed = !sidebarCollapsed" :title="sidebarCollapsed?'展开':'折叠'">
            {{ sidebarCollapsed ? '▶' : '◀' }}
          </button>
        </div>
        <div v-if="!sidebarCollapsed" class="mv-sidebar-list">
          <div
            v-for="slot in modelSlots" :key="slot.id"
            class="mv-file-item"
            :class="{ 'mv-file-item-active': slot.id === selectedId }"
            @click="selectModel(slot.id)"
          >
            <span class="mv-file-dot" :style="{ background: slot.color }" />
            <div class="mv-file-info">
              <span class="mv-file-name">{{ slot.shortName }}</span>
              <span class="mv-file-meta">{{ slot.totalFrames }} 帧 · {{ slot.framerate }} fps</span>
            </div>
          </div>
        </div>
      </aside>

      <!-- 3D 画布 -->
      <div ref="canvasWrap" class="mv-canvas-wrap" />
    </div>

    <!-- 底部控制栏 -->
    <div class="mv-bottombar">
      <button class="mv-btn" @click="stepBackward">⏮</button>
      <button class="mv-btn" @click="skipBackward">-1s</button>
      <button class="mv-btn mv-btn-play" @click="togglePlay">{{ playing ? '⏸' : '▶' }}</button>
      <button class="mv-btn" @click="skipForward">+1s</button>
      <button class="mv-btn" @click="stepForward">⏭</button>
      <input type="range" class="mv-progress" :min="0" :max="totalFrames - 1" :value="currentFrame"
        @input="seekTo(Number(($event.target as HTMLInputElement).value))" />
      <span class="mv-frame-label">{{ currentFrame }} / {{ totalFrames }}</span>
      <!-- 循环 -->
      <label class="mv-toggle" style="margin-left:6px">
        <input type="checkbox" v-model="loopPlayback">循环
      </label>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from "vue";
import { useRoute } from "vue-router";
import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";

const route = useRoute();

// ── 模型槽位 ──
interface ModelSlot {
  id: number;
  fileId: number;
  fileName: string;
  shortName: string;
  mesh: THREE.SkinnedMesh;
  bones: THREE.Bone[];
  skeletonHelper: THREE.Group;
  frameDataList: any[];
  totalFrames: number;
  framerate: number;
  color: string;
}

// ── 状态 ──
const loading = ref(true);
const error = ref("");
const loadingStep = ref("初始化...");
const loadTotal = ref(0);
const loadDone = ref(0);
const loadProgress = computed(() => loadTotal.value ? Math.round(loadDone.value / loadTotal.value * 100) : 0);

const modelSlots = ref<ModelSlot[]>([]);
const selectedId = ref(-1);  // 初始-1，避免 selectModel(0) 被 early-return 跳过
const playing = ref(false);
const currentFrame = ref(0);
const playSpeed = ref(1);
const loopPlayback = ref(true);
const showMesh = ref(true);
const showSkeleton = ref(true);
const wireframe = ref(false);
const showGround = ref(true);
const sidebarCollapsed = ref(false);

const rootEl = ref<HTMLElement>();
const canvasWrap = ref<HTMLElement>();

const currentModel = computed(() => modelSlots.value.find(s => s.id === selectedId.value));
const totalFrames = computed(() => {
  const m = currentModel.value;
  return m ? m.frameDataList.length : 0;
});
const currentFramerate = computed(() => currentModel.value?.framerate ?? 120);

// ── Three.js ──
let renderer: THREE.WebGLRenderer | null = null;
let scene: THREE.Scene | null = null;
let camera: THREE.PerspectiveCamera | null = null;
let controls: OrbitControls | null = null;
let groundGrid: THREE.GridHelper | null = null;
let animFrameId = 0;
let startTime = 0;

// ── 颜色 ──
const COLORS = ["#6495ED", "#FF6B81", "#4CAF50", "#FF9800", "#9C27B0", "#00BCD4", "#FFEB3B", "#9E9E9E"];

// ── 模型缓存 ──
const modelCache = new Map<string, {
  vTemplate: Float32Array; faces: Uint16Array;
  skinWeights: Float32Array; skinIndices: Uint16Array; keypoints: Float32Array;
  offsets: Float32Array[]; offsetsJ: (Float32Array | null)[];
}>();

// ── 骨骼表 ──
const SMPL_EDGES: Record<string, number[]> = {
  smpl:  [-1,0,0,0,1,2,3,4,5,6,7,8,9,9,9,12,13,14,16,17,18,19,20,21],
  smplh: [-1,0,0,0,1,2,3,4,5,6,7,8,9,9,9,12,13,14,16,17,18,19,20,22,23,20,25,26,20,28,29,20,31,32,20,34,35,21,37,38,21,40,41,21,43,44,21,46,47,21,49,50],
  smplx: [-1,0,0,0,1,2,3,4,5,6,7,8,9,9,9,12,13,14,16,17,18,19,15,15,15,20,25,26,20,28,29,20,31,32,20,34,35,20,37,38,21,40,41,21,43,44,21,46,47,21,49,50,21,52,53],
};

function getAssetDir(st: string, g: string) {
  let d = `dump_${st}`;
  if (g === "male") d += "_male"; else if (g === "female") d += "_female";
  return `/assets/${d}`;
}

// ── 缓存加载 SMPL 模型数据 ──
async function loadCachedGroup(st: string, g: string) {
  const key = `${st}_${g}`;
  if (modelCache.has(key)) return modelCache.get(key)!;
  const base = getAssetDir(st, g);
  const fb = async (n: string) => {
    const url=`${base}/${n}`;
    const mc=await caches.open("motion-viewer-v1");
    let r=await mc.match(url);
    if(!r){ r=await fetch(url); if(r.ok)mc.put(url,r.clone()); }
    if (!r.ok) {
      if (n === "j_template.bin") { const r2=await fetch(`${base}/keypoints.bin`); return r2.arrayBuffer(); }
      throw new Error(`${n}: ${r.status}`);
    }
    return r.arrayBuffer();
  };
  const [v,f,sw,si,kp] = await Promise.all([fb("v_template.bin"),fb("faces.bin"),fb("skinWeights.bin"),fb("skinIndice.bin"),fb("j_template.bin")]);
  const offs: Float32Array[] = [], offsJ: (Float32Array | null)[] = [];
  for (let i=0;i<16;i++) { try { offs.push(new Float32Array(await fb(`shapeoffset_${i}.bin`))); } catch { offs.push(new Float32Array()); } }
  for (let i=0;i<16;i++) { try { offsJ.push(new Float32Array(await fb(`shapeoffset_j_${i}.bin`))); } catch { offsJ.push(null); } }
  const data = { vTemplate:new Float32Array(v), faces:new Uint16Array(f), skinWeights:new Float32Array(sw), skinIndices:new Uint16Array(si), keypoints:new Float32Array(kp), offsets:offs, offsetsJ:offsJ };
  modelCache.set(key, data);
  return data;
}

// ── 创建骨骼+SkinnedMesh ──
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
  const mat=new THREE.MeshStandardMaterial({color:new THREE.Color(color),side:THREE.DoubleSide,skinning:true,emissive:0x222222,roughness:.7,metalness:.3});
  const m=new THREE.SkinnedMesh(geo,mat); m.castShadow=m.receiveShadow=true; m.frustumCulled=false; m.add(bones[0]); m.bind(sk);
  (m as any).userData.bones=bones; return {mesh:m,bones,skeleton:sk};
}

// ── 骨架可视化 ──
function createSkeletonHelper(bones:THREE.Bone[],st:string,color:string){
  const g=new THREE.Group(); const sg=new THREE.SphereGeometry(.008,8,8); const jm=new THREE.MeshBasicMaterial({color:new THREE.Color(color)});
  for(const b of bones){ const d=new THREE.Mesh(sg,jm); d.userData.isSkelHelper=true; b.add(d); }
  const edges=SMPL_EDGES[st]||SMPL_EDGES["smpl"]; const cm=new THREE.MeshBasicMaterial({color:new THREE.Color(color).multiplyScalar(.6)});
  for(let i=1;i<Math.min(bones.length,edges.length);i++){ const p=edges[i]; if(p<0)continue; const cb=bones[i]; const dist=cb.position.length(); if(dist<.001)continue;
    const cg=new THREE.CylinderGeometry(.003,.005,dist,6); const c=new THREE.Mesh(cg,cm); c.userData.isSkelHelper=true; c.position.copy(cb.position.clone().multiplyScalar(.5));
    c.setRotationFromQuaternion(new THREE.Quaternion().setFromUnitVectors(new THREE.Vector3(0,1,0),cb.position.clone().normalize())); bones[p]?.add(c); }
  return g;
}

type ModelCacheData = Awaited<ReturnType<typeof loadCachedGroup>>;

// ── 轴角转四元数 ──
function aa2q(rx:number,ry:number,rz:number){ const a=new THREE.Vector3(rx,ry,rz); const ang=a.length(); if(ang<1e-10)return new THREE.Quaternion(0,0,0,1); a.normalize(); return new THREE.Quaternion().setFromAxisAngle(a,ang); }

// ── 更新模型骨骼 ──
function updateFrame(slot:ModelSlot, frameObj:any){
  const fd=frameObj; const pa:number[]=fd.poses[0]; const rh=fd.Rh[0]; const th=fd.Th[0];
  slot.bones[0].quaternion.copy(aa2q(rh[0],rh[1],rh[2])); slot.mesh.position.set(th[0],th[1],th[2]);
  const off=pa.length===69?-3:0;
  for(let i=1;i<slot.bones.length;i++){ const idx=off+3*i; if(idx+2>=pa.length)break; slot.bones[i].quaternion.copy(aa2q(pa[idx],pa[idx+1],pa[idx+2])); }
}

// ── 选择模型 ──
function selectModel(id:number){
  if(selectedId.value===id)return;
  // 隐藏旧
  const prev=modelSlots.value.find(s=>s.id===selectedId.value);
  if(prev){ prev.mesh.visible=false; prev.skeletonHelper.visible=false; }
  // 显示新
  selectedId.value=id;
  const next=modelSlots.value.find(s=>s.id===id);
  if(next){
    next.mesh.visible=showMesh.value; next.skeletonHelper.visible=showSkeleton.value;
    // 立即应用第一帧姿态
    if(next.frameDataList[0]) updateFrame(next, next.frameDataList[0]);
  }
  currentFrame.value=0; if(playing.value)startTime=performance.now();
  applyDispFlags();
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

// ── 动画 ──
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

// ── 播放控制 ──
function togglePlay(){ playing.value=!playing.value; if(playing.value)startTime=performance.now()-(currentFrame.value/((currentModel.value?.framerate??120)*(playSpeed.value||1)))*1000; }
function seekTo(f:number){ currentFrame.value=Math.max(0,Math.min(f,totalFrames.value-1)); const m=currentModel.value; if(m?.frameDataList[currentFrame.value])updateFrame(m,m.frameDataList[currentFrame.value]); if(playing.value)startTime=performance.now()-(currentFrame.value/((currentModel.value?.framerate??120)*(playSpeed.value||1)))*1000; }
function stepForward(){ seekTo(currentFrame.value+1); }
function stepBackward(){ seekTo(currentFrame.value-1); }
function skipForward(){ seekTo(currentFrame.value+Math.round(currentModel.value?.framerate??120)); }
function skipBackward(){ seekTo(currentFrame.value-Math.round(currentModel.value?.framerate??120)); }

function onKeyDown(e:KeyboardEvent){
  switch(e.key){ case" ":e.preventDefault();togglePlay();break; case"ArrowLeft":e.preventDefault();stepBackward();break; case"ArrowRight":e.preventDefault();stepForward();break; case"ArrowUp":playSpeed.value=Math.min(3,playSpeed.value+.25);break; case"ArrowDown":playSpeed.value=Math.max(.25,playSpeed.value-.25);break; case"Escape":close();break; }
}
function close(){ window.close(); }

// ── 场景初始化 ──
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

// ── 主初始化 ──
async function initAll(){
  loading.value=true; error.value=""; loadingStep.value="正在获取会话数据..."; loadTotal.value=0; loadDone.value=0;
  try{
    const sid=Number(route.params.sessionId); if(!sid||isNaN(sid))throw new Error("无效的sessionId");
    loadingStep.value="正在获取文件列表...";
    const fr=await fetch(`/api/sessions/${sid}/files`); if(!fr.ok)throw new Error(`文件列表失败:${fr.status}`);
    const fw=await fr.json(); const allFiles:any[]=(fw as any)?.data||fw||[];
    const viewerFiles=allFiles.filter((f:any)=>f.assetType==="MOTION_VIEWER_JSON"&&f.id);
    if(!viewerFiles.length)throw new Error("该会话没有 3D 查看数据，请先运行「生成3D查看数据」");

    // 并行下载所有 viewer JSON（Cache API 缓存，刷新不重下）
    loadTotal.value=viewerFiles.length; loadDone.value=0;
    const cache=await caches.open("motion-viewer-v1");
    const fileDataArr=await Promise.all(viewerFiles.map(async (vf:any)=>{
      const url=`/api/files/${vf.id}/download`;
      let r=await cache.match(url);
      if(!r){ r=await fetch(url); if(r.ok)cache.put(url,r.clone()); }
      if(!r.ok)throw new Error(`下载 fileId=${vf.id} 失败:${r.status}`);
      const d=await r.json(); loadDone.value++; return {fileId:vf.id,data:d};
    }));

    // 按 (smplType,gender) 分组
    const groups=new Map<string,{fileId:number;data:any}[]>();
    for(const fd of fileDataArr){ const k=`${fd.data.smpl_type||"smplh"}_${fd.data.gender||"neutral"}`; if(!groups.has(k))groups.set(k,[]); groups.get(k)!.push(fd); }

    const slots:ModelSlot[]=[]; let ci=0;
    loadingStep.value="正在加载 SMPL 模型..."; loadDone.value=0; loadTotal.value=groups.size;
    for(const [key,group] of groups){
      const [st,g]=key.split("_");
      const cache=await loadCachedGroup(st,g);
      loadDone.value++;

      for(const fd of group){
        const fl=fd.data.frames.map((f:any)=>f[0]); const ff=fl[0]; const shapes:number[]=ff.shapes||[]; const color=COLORS[ci%COLORS.length];
        const {mesh,bones}=createSkinnedMesh(cache,shapes,st,color);
        const skel=createSkeletonHelper(bones,st,color); mesh.add(skel);
        const name=fd.data.filename||`Motion #${fd.fileId}`;
        const short=name.replace(/_poses(_viewer)?/,"").replace(/_/g," ").replace(/__+/g," ").replace(/\.json$/,"").slice(0,40);
        const slot:ModelSlot={id:slots.length,fileId:fd.fileId,fileName:name,shortName:short,mesh,bones,skeletonHelper:skel,frameDataList:fl,totalFrames:fd.data.frame_count,framerate:fd.data.framerate||120,color};
        mesh.visible=false; skel.visible=false; slots.push(slot); ci++;
      }
    }
    modelSlots.value=slots; if(!slots.length)throw new Error("无可用模型");

    // 初始化场景
    loading.value=false; loadingStep.value=""; await nextTick(); initScene();
    for(const s of slots) scene!.add(s.mesh);

    // 选第一个
    selectModel(0);
    // 调整相机：SMPL 人物面朝 -Z，相机放在 -Z 方向（人物正面）
    const m=currentModel.value!; const bbox=new THREE.Box3().setFromObject(m.mesh);
    const center=new THREE.Vector3(); bbox.getCenter(center);
    const targetY=Math.max(center.y,.6);
    const distZ=Math.max(bbox.max.z-bbox.min.z+2,3.5);
    controls!.target.set(center.x,targetY,center.z);
    camera!.position.set(center.x,targetY+.6,center.z+distZ);  // +Z 侧观察
    camera!.lookAt(controls!.target); controls!.update();

    await nextTick(); rootEl.value?.focus(); animate();
  }catch(e:any){ console.error("[MotionViewer]",e); error.value=`${loadingStep.value}\n${e.message||String(e)}`; loading.value=false; loadingStep.value=""; }
}

onMounted(()=>{ initAll(); });
onBeforeUnmount(()=>{ cancelAnimationFrame(animFrameId); renderer?.dispose(); renderer?.domElement.remove(); scene?.traverse(o=>{ if(o instanceof THREE.Mesh){o.geometry?.dispose(); if(Array.isArray(o.material))o.material.forEach(m=>m.dispose()); else o.material?.dispose();}}); });
</script>

<style scoped>
.mv-root{position:fixed;inset:0;display:flex;flex-direction:column;background:#141414;color:#e0e0e0;font-family:"JetBrains Mono","Cascadia Code",monospace;outline:none;z-index:9999;}
.mv-loading,.mv-error{position:fixed;inset:0;display:flex;flex-direction:column;align-items:center;justify-content:center;background:#141414;color:#e0e0e0;z-index:9999;gap:16px;}
.mv-loading-spinner{width:40px;height:40px;border:3px solid rgba(255,255,255,.1);border-top-color:#0053e6;border-radius:50%;animation:spin .8s linear infinite;}
@keyframes spin{to{transform:rotate(360deg)}}
.mv-progress-bar{width:240px;height:4px;background:rgba(255,255,255,.1);border-radius:2px;overflow:hidden;}
.mv-progress-fill{height:100%;background:#0053e6;transition:width .3s;}
.mv-progress-text{font-size:12px;color:#999;}

.mv-topbar{display:flex;align-items:center;justify-content:space-between;padding:8px 16px;background:rgba(0,0,0,.5);border-bottom:1px solid rgba(255,255,255,.06);gap:12px;flex-shrink:0;}
.mv-topbar-left{display:flex;flex-direction:column;gap:2px;min-width:0;}
.mv-filename{font-size:14px;font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.mv-meta{font-size:11px;color:#999;}
.mv-topbar-center{display:flex;align-items:center;gap:10px;flex-wrap:wrap;}
.mv-speed-sel{background:#2a2a3a;color:#e0e0e0;border:1px solid rgba(255,255,255,.1);border-radius:4px;padding:4px 8px;font-size:12px;font-family:inherit;}
.mv-toggle{display:flex;align-items:center;gap:4px;font-size:12px;cursor:pointer;color:#aaa;}
.mv-toggle input{accent-color:#0053e6;}

.mv-btn{background:#2a2a3a;color:#e0e0e0;border:1px solid rgba(255,255,255,.08);border-radius:5px;padding:6px 12px;font-size:12px;cursor:pointer;font-family:inherit;transition:background .12s;}
.mv-btn:hover{background:#3a3a4a;}
.mv-btn-play{font-size:16px;width:40px;}
.mv-btn-close{background:transparent;border-color:transparent;font-size:16px;padding:4px 8px;}

/* 主体 */
.mv-body{display:flex;flex:1;min-height:0;overflow:hidden;}

/* 侧边栏 */
.mv-sidebar{width:220px;flex-shrink:0;background:rgba(0,0,0,.3);border-right:1px solid rgba(255,255,255,.06);display:flex;flex-direction:column;transition:width .2s;}
.mv-sidebar-collapsed{width:32px;}
.mv-sidebar-hdr{display:flex;align-items:center;justify-content:space-between;padding:10px 12px;font-size:12px;color:#999;border-bottom:1px solid rgba(255,255,255,.06);}
.mv-sidebar-toggle{background:none;border:none;color:#999;cursor:pointer;font-size:10px;padding:2px 4px;}
.mv-sidebar-list{flex:1;overflow-y:auto;padding:4px 0;}
.mv-file-item{display:flex;align-items:center;gap:10px;padding:8px 12px;cursor:pointer;transition:background .12s;border-left:3px solid transparent;}
.mv-file-item:hover{background:rgba(255,255,255,.04);}
.mv-file-item-active{background:rgba(0,83,230,.1);border-left-color:#0053e6;}
.mv-file-dot{width:8px;height:8px;border-radius:50%;flex-shrink:0;}
.mv-file-info{display:flex;flex-direction:column;gap:2px;min-width:0;}
.mv-file-name{font-size:12px;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}
.mv-file-meta{font-size:10px;color:#777;}

/* 画布 */
.mv-canvas-wrap{flex:1;min-width:0;overflow:hidden;}

/* 底部 */
.mv-bottombar{display:flex;align-items:center;gap:10px;padding:8px 16px;background:rgba(0,0,0,.5);border-top:1px solid rgba(255,255,255,.06);flex-shrink:0;}
.mv-progress{flex:1;height:4px;accent-color:#0053e6;cursor:pointer;}
.mv-frame-label{font-size:12px;color:#999;white-space:nowrap;min-width:80px;text-align:right;}
</style>
