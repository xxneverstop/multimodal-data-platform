<template>
  <div class="light2-page">
    <div class="light2-hdr">
      <div>
        <h1>数据处理</h1>
        <p>查看处理规则和执行记录</p>
      </div>
      <button v-if="activeTab === 'templates' && isAdmin" class="light2-btn light2-btn-primary" @click="openCreateDialog">+ 新建规则</button>
    </div>

    <hr class="light2-divider" />

    <div class="tab-bar" style="margin-bottom:16px">
      <button v-for="t in tabs" :key="t.value" class="tab-bar-item" :class="{ 'tab-bar-item-active': activeTab === t.value }" @click="activeTab = t.value">{{ t.label }}</button>
    </div>

    <div class="light2-filters" v-if="activeTab === 'templates'">
      <input v-model="pipelineKeyword" type="text" placeholder="搜索 pipelineId / 名称..." class="light2-input" @keyup.enter="pipelinePage = 1" />
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="pipelinePage = 1">搜索</button>
      <button class="light2-btn light2-btn-sec light2-btn-sm" @click="pipelineKeyword = ''; pipelinePage = 1">重置</button>
    </div>
    <div class="light2-filters" v-else>
      <input v-model="jobSessionId" type="number" placeholder="按 Session ID 筛选（可选）" class="light2-input" @keyup.enter="loadJobs" />
      <button class="light2-btn light2-btn-primary light2-btn-sm" @click="loadJobs">筛选</button>
      <button class="light2-btn light2-btn-sec light2-btn-sm" @click="jobSessionId = ''; loadJobs()">显示全部</button>
      <span style="font-size:12px;color:var(--color-text-tertiary);margin-left:8px">每 5 秒自动刷新</span>
    </div>

    <div class="light2-tbl overflow-x-auto" v-if="activeTab === 'templates'">
      <table class="min-w-[1000px]">
        <thead><tr>
          <th style="width:150px">Pipeline ID</th><th>名称</th><th style="width:180px">输入类型</th><th style="width:180px">输出类型</th><th style="width:90px">执行方式</th><th style="width:70px">Profile</th><th style="width:72px">状态</th><th style="width:112px">操作</th>
        </tr></thead>
        <tbody>
          <tr v-if="pipelineLoading"><td colspan="8" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">正在加载处理规则...</td></tr>
          <tr v-else-if="!filteredPipelines.length"><td colspan="8" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">{{ pipelineKeyword ? '无匹配结果' : '暂无处理规则，点击「新建规则」创建' }}</td></tr>
          <tr v-for="p in displayedPipelines" :key="p.id">
            <td><span class="light2-code">{{ p.pipelineId }}</span></td>
            <td><span class="light2-tname">{{ p.displayName }}</span><div class="light2-tsub" v-if="p.description">{{ p.description }}</div></td>
            <td><span v-if="!p.inputAssetTypes?.length" class="light2-badge light2-badge-neutral">不限</span><span v-for="t in p.inputAssetTypes" :key="t" class="light2-badge light2-badge-info" style="margin-right:3px;margin-bottom:2px">{{ t }}</span></td>
            <td><span v-if="!p.outputAssetTypes?.length" class="light2-badge light2-badge-neutral">不限</span><span v-for="t in p.outputAssetTypes" :key="t" class="light2-badge light2-badge-ok" style="margin-right:3px;margin-bottom:2px">{{ t }}</span></td>
            <td>{{ p.executorType }}</td>
            <td style="text-align:center"><span class="light2-badge light2-badge-neutral">{{ p.profileIds?.length ?? 0 }}</span></td>
            <td><span class="light2-badge" :class="p.enabled===1?'light2-badge-ok':'light2-badge-err'"><span class="light2-bdot" :style="{background:p.enabled===1?'#0d9444':'#d92d20'}"/>{{ p.enabled===1?'已启用':'已禁用' }}</span></td>
            <td><div class="light2-actions" v-if="isAdmin"><button class="light2-btn light2-btn-sec light2-btn-sm" @click="openEditDialog(p)">编辑</button><button v-if="p.enabled===1" class="light2-btn light2-btn-sec light2-btn-sm" style="color:#d92d20;border-color:#fca5a5" @click="handleDisable(p)">禁用</button></div></td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="light2-tbl overflow-x-auto" v-if="activeTab === 'jobs'">
      <table class="min-w-[960px]">
        <thead><tr>
          <th style="width:60px">#</th>
          <th style="width:140px">Pipeline</th>
          <th style="width:165px">Session</th>
          <th style="width:72px">状态</th>
          <th style="width:145px">创建时间</th>
          <th style="width:145px">更新时间</th>
          <th style="min-width:120px">错误</th>
          <th v-if="isAdmin" style="width:64px">操作</th>
        </tr></thead>
        <tbody>
          <tr v-if="jobLoading"><td :colspan="isAdmin ? 8 : 7" style="text-align:center;padding:48px 0">正在加载...</td></tr>
          <tr v-else-if="!filteredJobs.length"><td :colspan="isAdmin ? 8 : 7" style="text-align:center;padding:48px 0;color:var(--color-text-secondary)">暂无处理任务，在 Session 详情页提交处理后可在此查看</td></tr>
          <tr v-for="job in filteredJobs" :key="job.id" style="white-space:nowrap">
            <td><span class="light2-code">#{{ job.id }}</span></td>
            <td><span class="light2-code">{{ job.pipelineId }}</span></td>
            <td><span class="light2-code">{{ (job as any).sessionCode || (job.sessionId ? '#'+job.sessionId : '-') }}</span></td>
            <td><span class="light2-badge" :class="badgeCls(job.status)"><span class="light2-bdot" :style="{background:badgeClr(job.status)}"/>{{ job.status }}</span></td>
            <td class="light2-code">{{ fmt(job.createdAt) }}</td>
            <td class="light2-code">{{ fmt(job.updatedAt) }}</td>
            <td style="max-width:200px;overflow:hidden;text-overflow:ellipsis" :title="job.errorMessage??''">{{ job.errorMessage ?? '-' }}</td>
            <td v-if="isAdmin">
              <button class="light2-btn light2-btn-sec light2-btn-sm" style="color:#c5222f;border-color:#fecdd3" @click="openDeleteJobDialog(job)">清除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="activeTab==='templates'&&filteredPipelines.length>pipelinePageSize" class="light2-pg">
      <span>第 {{ pipelinePage }} 页，共 {{ Math.max(1,Math.ceil(filteredPipelines.length/pipelinePageSize)) }} 页 · 总计 {{ filteredPipelines.length }} 条</span>
      <div class="light2-pg-btns"><button :disabled="pipelinePage<=1" @click="pipelinePage--">&larr; 上一页</button><button :disabled="pipelinePage>=Math.ceil(filteredPipelines.length/pipelinePageSize)" @click="pipelinePage++">下一页 &rarr;</button></div>
    </div>

    <AppDialog :open="dialogOpen" :title="editingPipeline?'编辑处理规则':'新建处理规则'" description="选择执行方式和 Pipeline，关联 Profile" size="lg" :loading="submitting" @close="closeDialog" @confirm="submitDialog">
      <div class="dialog-form">
        <!-- PYTHON_WORKER 新建模式：精简 —— 两个下拉框 + 自动展示 -->
        <template v-if="isWorkerAutoMode">
          <fieldset class="form-section"><legend class="form-section-title">执行方式与 Pipeline</legend>
            <div class="form-grid">
              <label class="form-field"><span class="form-label">执行方式 <span class="text-rose-500">*</span></span>
                <select v-model="pForm.executorType" class="app-input app-input-compact" @change="onExecutorTypeChange">
                  <option value="">-- 请选择执行方式 --</option>
                  <option value="PYTHON_WORKER">PYTHON_WORKER</option>
                  <option value="MOCK">MOCK</option>
                  <option value="MANUAL">MANUAL</option>
                </select>
              </label>
              <label class="form-field" style="grid-column:1/-1">
                <span class="form-label">Pipeline <span class="text-rose-500">*</span></span>
                <select v-model="pForm.pipelineId" @change="onWorkerPipelineSelected" class="app-input app-input-compact">
                  <option value="">-- 请选择 Pipeline --</option>
                  <option v-for="wp in workerPipelines" :key="wp.pipelineId" :value="wp.pipelineId">{{ wp.pipelineId }} — {{ wp.displayName }}</option>
                </select>
                <span v-if="loadingWorkerPipelines" style="font-size:11px;color:#3b82f6;margin-top:2px">正在查询 Worker Pipeline 列表...</span>
                <span v-else-if="!workerPipelines.length" style="font-size:11px;color:#d92d20;margin-top:2px">未获取到 Worker Pipeline 列表，请确认 Worker 已启动并生成了 pipeline-manifest.json</span>
              </label>
            </div>
          </fieldset>

          <!-- 选中 Pipeline 后展示详情（只读） -->
          <fieldset v-if="pForm.pipelineId" class="form-section"><legend class="form-section-title">Pipeline 详情</legend>
            <div class="form-field" style="margin-bottom:12px">
              <span class="form-label">描述</span>
              <p style="font-size:13px;color:var(--color-text-secondary);margin:0;padding:8px 12px;background:#f8f9fb;border-radius:6px;min-height:32px">{{ pForm.description || '(无描述)' }}</p>
            </div>
            <div class="form-field" style="margin-bottom:8px">
              <span class="form-label">输入资产类型</span>
              <div class="chip-row"><span v-for="t in pForm.inputAssetTypes" :key="t" class="chip">{{ t }}</span><span v-if="!pForm.inputAssetTypes.length" style="font-size:12px;color:#999">不限</span></div>
            </div>
            <div class="form-field">
              <span class="form-label">输出资产类型</span>
              <div class="chip-row"><span v-for="t in pForm.outputAssetTypes" :key="t" class="chip chip-active">{{ t }}</span><span v-if="!pForm.outputAssetTypes.length" style="font-size:12px;color:#999">不限</span></div>
            </div>
          </fieldset>
        </template>

        <!-- MOCK / MANUAL / 编辑模式：传统手动填写 -->
        <template v-else>
          <fieldset class="form-section"><legend class="form-section-title">基本信息</legend>
            <div class="form-grid">
              <label class="form-field"><span class="form-label">执行方式 <span class="text-rose-500">*</span></span>
                <select v-model="pForm.executorType" class="app-input app-input-compact" @change="onExecutorTypeChange">
                  <option value="">-- 请选择执行方式 --</option>
                  <option value="PYTHON_WORKER">PYTHON_WORKER</option>
                  <option value="MOCK">MOCK</option>
                  <option value="MANUAL">MANUAL</option>
                </select>
              </label>
              <label class="form-field"><span class="form-label">Pipeline ID <span class="text-rose-500">*</span></span>
                <input v-model="pForm.pipelineId" class="app-input app-input-compact" placeholder="如 BUILD_PLAYBACK" :disabled="!!editingPipeline" />
              </label>
              <label class="form-field"><span class="form-label">显示名称 <span class="text-rose-500">*</span></span>
                <input v-model="pForm.displayName" class="app-input app-input-compact" placeholder="如 图像序列 → MP4" />
              </label>
              <label class="form-field" style="grid-column:1/-1"><span class="form-label">描述</span>
                <input v-model="pForm.description" class="app-input app-input-compact" placeholder="Pipeline 用途说明" />
              </label>
            </div>
          </fieldset>
          <fieldset class="form-section"><legend class="form-section-title">输入/输出资产类型</legend>
            <div class="form-field"><span class="form-label">输入资产类型</span>
              <div class="chip-row"><span v-for="t in pForm.inputAssetTypes" :key="t" class="chip">{{ t }} <button type="button" @click="removeInput(t)">&times;</button></span></div>
              <select class="app-input app-input-compact" @change="addInput(($event.target as HTMLSelectElement).value);($event.target as HTMLSelectElement).value=''"><option value="">-- 添加 --</option><option v-for="o in ASSET_TYPE_OPTIONS" :key="o" :value="o">{{ o }}</option></select>
            </div>
            <div class="form-field" style="margin-top:8px"><span class="form-label">输出资产类型</span>
              <div class="chip-row"><span v-for="t in pForm.outputAssetTypes" :key="t" class="chip chip-active">{{ t }} <button type="button" @click="removeOutput(t)">&times;</button></span></div>
              <select class="app-input app-input-compact" @change="addOutput(($event.target as HTMLSelectElement).value);($event.target as HTMLSelectElement).value=''"><option value="">-- 添加 --</option><option v-for="o in ASSET_TYPE_OPTIONS" :key="o" :value="o">{{ o }}</option></select>
            </div>
          </fieldset>
        </template>

        <fieldset class="form-section"><legend class="form-section-title">关联 Profile（可选多选）</legend>
          <div class="chip-row"><span v-for="pid in pForm.profileIds" :key="pid" class="chip">{{ profileLabel(pid) }} <button type="button" @click="removeProfile(pid)">&times;</button></span></div>
          <select class="app-input app-input-compact" @change="addProfile(Number(($event.target as HTMLSelectElement).value));($event.target as HTMLSelectElement).value=''"><option value="">-- 添加 --</option><option v-for="p in profiles" :key="p.id" :value="p.id">{{ p.profileName }} ({{ p.profileCode }})</option></select>
        </fieldset>
        <p v-if="dialogError" class="dialog-error">{{ dialogError }}</p>
      </div>
    </AppDialog>

    <!-- 删除 Job 产出确认对话框 -->
    <AppDialog
      :open="deleteJobDialogOpen"
      title="清除处理产出"
      :description="`将永久删除处理任务 #${deleteJobTarget?.id ?? ''}（${deleteJobTarget?.pipelineId ?? ''}）生成的所有派生文件和资产，不可恢复。`"
      confirm-text="确认清除"
      :loading="deleteJobSubmitting"
      @close="deleteJobDialogOpen = false"
      @confirm="handleDeleteJobOutputs"
    >
      <div class="rounded-[10px] border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">
        此操作不可逆！只清除该处理任务的产出（派生文件+资产），不影响原始采集数据。
      </div>
      <p v-if="deleteJobMessage" class="mt-3 text-sm text-[var(--color-text-secondary)]">{{ deleteJobMessage }}</p>
    </AppDialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import AppDialog from "@/components/AppDialog.vue";
import { fetchPipelines, createPipeline, updatePipeline, disablePipeline, fetchWorkerAvailablePipelines } from "@/api/pipelines";
import { fetchAllJobs, fetchSessionJobs } from "@/api/processing";
import { deleteJobOutputs } from "@/api/admin";
import { useAuthStore } from "@/stores/auth";
import { fetchCollectionProfiles, type CollectionProfileResponse } from "@/api/profiles";
import { ASSET_TYPE_OPTIONS } from "@/types/pipeline";
import type { PipelineDefinitionResponse, CreatePipelineRequest, WorkerPipelineInfo } from "@/types/pipeline";
import type { ProcessingJobResponse } from "@/types/processing";

const authStore = useAuthStore();
const isAdmin = computed(() => !!authStore.state.user?.isAdmin);

// ── 删除 Job 产出 ──
const deleteJobDialogOpen = ref(false);
const deleteJobTarget = ref<ProcessingJobResponse | null>(null);
const deleteJobSubmitting = ref(false);
const deleteJobMessage = ref("");

function openDeleteJobDialog(job: ProcessingJobResponse) {
  deleteJobTarget.value = job;
  deleteJobMessage.value = "";
  deleteJobDialogOpen.value = true;
}

async function handleDeleteJobOutputs() {
  if (!deleteJobTarget.value) return;
  deleteJobSubmitting.value = true;
  deleteJobMessage.value = "";
  try {
    const result = await deleteJobOutputs(deleteJobTarget.value.id);
    deleteJobMessage.value = result.summary;
    deleteJobDialogOpen.value = false;
    deleteJobTarget.value = null;
    // 重新加载 jobs
    if (jobSessionId.value) await loadJobs();
  } catch (e) {
    deleteJobMessage.value = e instanceof Error ? e.message : "删除失败";
  } finally {
    deleteJobSubmitting.value = false;
  }
}

const activeTab = ref("jobs");
const tabs = [{ label: "处理任务", value: "jobs" }, { label: "处理规则", value: "templates" }];

const pipelines = ref<PipelineDefinitionResponse[]>([]);
const pipelineLoading = ref(true);
const pipelineKeyword = ref("");
const pipelinePage = ref(1);
const pipelinePageSize = 10;
const filteredPipelines = computed(() => {
  if (!pipelineKeyword.value.trim()) return pipelines.value;
  const kw = pipelineKeyword.value.trim().toLowerCase();
  return pipelines.value.filter(p => p.pipelineId.toLowerCase().includes(kw) || p.displayName.toLowerCase().includes(kw));
});
const displayedPipelines = computed(() => filteredPipelines.value.slice((pipelinePage.value-1)*pipelinePageSize, pipelinePage.value*pipelinePageSize));

const jobs = ref<ProcessingJobResponse[]>([]);
const jobLoading = ref(true);
const jobSessionId = ref("");
const filteredJobs = computed(() => {
  if (!jobSessionId.value) return jobs.value;
  return jobs.value.filter(j => String(j.sessionId ?? "") === jobSessionId.value);
});
let jobRefreshTimer: any = null;
const refreshJobs = async () => { try{ jobs.value = jobSessionId.value ? await fetchSessionJobs(Number(jobSessionId.value)) : await fetchAllJobs(); }catch{ jobs.value=[]; } };
async function loadJobs() { jobLoading.value=true; await refreshJobs(); jobLoading.value=false; }
function startJobAutoRefresh() { if (jobRefreshTimer) clearInterval(jobRefreshTimer); jobRefreshTimer = setInterval(refreshJobs, 5000); }

const BM:Record<string,{cls:string;color:string}>={CREATED:{cls:"light2-badge-neutral",color:"#9298a3"},CLAIMED:{cls:"light2-badge-info",color:"#3b82f6"},RUNNING:{cls:"light2-badge-warn",color:"#b87a0a"},SUCCESS:{cls:"light2-badge-ok",color:"#0d7d3e"},FAILED:{cls:"light2-badge-err",color:"#c5222f"}};
function badgeCls(s:string){return BM[s]?.cls??"light2-badge-neutral"}
function badgeClr(s:string){return BM[s]?.color??"#9298a3"}
function fmt(d:string){return d?d.replace("T"," ").substring(0,19):"-"}

const dialogOpen=ref(false); const submitting=ref(false); const dialogError=ref(""); const editingPipeline=ref<PipelineDefinitionResponse|null>(null);
const pForm=reactive({pipelineId:"",displayName:"",description:"",executorType:"",inputAssetTypes:[] as string[],outputAssetTypes:[] as string[],profileIds:[] as number[]});
function addInput(t:string){if(t&&!pForm.inputAssetTypes.includes(t))pForm.inputAssetTypes.push(t)}
function removeInput(t:string){pForm.inputAssetTypes=pForm.inputAssetTypes.filter(v=>v!==t)}
function addOutput(t:string){if(t&&!pForm.outputAssetTypes.includes(t))pForm.outputAssetTypes.push(t)}
function removeOutput(t:string){pForm.outputAssetTypes=pForm.outputAssetTypes.filter(v=>v!==t)}

const profiles=ref<CollectionProfileResponse[]>([]);
async function loadProfiles(){try{profiles.value=await fetchCollectionProfiles()}catch{profiles.value=[]}}
function profileLabel(pid:number){return profiles.value.find(p=>p.id===pid)?.profileName??`#${pid}`}
function addProfile(pid:number){if(pid&&!pForm.profileIds.includes(pid))pForm.profileIds.push(pid)}
function removeProfile(pid:number){pForm.profileIds=pForm.profileIds.filter(v=>v!==pid)}

// ── Worker Pipeline 自动发现 ──
const workerPipelines = ref<WorkerPipelineInfo[]>([]);
const loadingWorkerPipelines = ref(false);

/** 当前是否为 Worker 自动填充模式（新建 + PYTHON_WORKER） */
const isWorkerAutoMode = computed(() =>
  pForm.executorType === 'PYTHON_WORKER' && !editingPipeline.value
);

async function loadWorkerPipelines() {
  if (loadingWorkerPipelines.value) return;
  loadingWorkerPipelines.value = true;
  try {
    workerPipelines.value = await fetchWorkerAvailablePipelines();
    if (!workerPipelines.value.length) {
      console.warn('未获取到 Worker Pipeline 列表，请确认 Worker 已启动并注册');
    }
  } catch {
    workerPipelines.value = [];
  } finally {
    loadingWorkerPipelines.value = false;
  }
}

function onExecutorTypeChange() {
  if (pForm.executorType === 'PYTHON_WORKER') {
    // 切换到 PYTHON_WORKER：清空之前的手动输入，加载 Worker 清单
    pForm.pipelineId = '';
    pForm.displayName = '';
    pForm.description = '';
    pForm.inputAssetTypes = [];
    pForm.outputAssetTypes = [];
    loadWorkerPipelines();
  } else {
    // 切换到 MOCK/MANUAL：清空自动填充的字段，恢复手动输入
    pForm.pipelineId = '';
    pForm.displayName = '';
    pForm.description = '';
    pForm.inputAssetTypes = [];
    pForm.outputAssetTypes = [];
    workerPipelines.value = [];
  }
}

function onWorkerPipelineSelected() {
  const selected = workerPipelines.value.find(wp => wp.pipelineId === pForm.pipelineId);
  if (selected) {
    pForm.displayName = selected.displayName;
    pForm.description = selected.description || '';
    pForm.inputAssetTypes = [...selected.inputAssetTypes];
    pForm.outputAssetTypes = [...selected.outputAssetTypes];
  } else {
    pForm.displayName = '';
    pForm.description = '';
    pForm.inputAssetTypes = [];
    pForm.outputAssetTypes = [];
  }
}

function openCreateDialog(){editingPipeline.value=null;dialogError.value="";workerPipelines.value=[];Object.assign(pForm,{pipelineId:"",displayName:"",description:"",executorType:"",inputAssetTypes:[],outputAssetTypes:[],profileIds:[]});dialogOpen.value=true}
function openEditDialog(p:PipelineDefinitionResponse){editingPipeline.value=p;dialogError.value="";pForm.pipelineId=p.pipelineId;pForm.displayName=p.displayName;pForm.description=p.description??"";pForm.executorType=p.executorType;pForm.inputAssetTypes=[...(p.inputAssetTypes??[])];pForm.outputAssetTypes=[...(p.outputAssetTypes??[])];pForm.profileIds=[...(p.profileIds??[])];dialogOpen.value=true}
function closeDialog(){dialogOpen.value=false;dialogError.value=""}
async function submitDialog(){
  if(!pForm.pipelineId.trim()){dialogError.value="Pipeline ID 不能为空";return}
  if(!pForm.displayName.trim()){dialogError.value="显示名称不能为空";return}
  submitting.value=true;dialogError.value="";
  const payload:CreatePipelineRequest={pipelineId:pForm.pipelineId.trim(),displayName:pForm.displayName.trim(),description:pForm.description.trim()||undefined,inputAssetTypes:pForm.inputAssetTypes.length?[...pForm.inputAssetTypes]:undefined,outputAssetTypes:pForm.outputAssetTypes.length?[...pForm.outputAssetTypes]:undefined,executorType:pForm.executorType.trim(),profileIds:pForm.profileIds.length?[...pForm.profileIds]:undefined};
  try{if(editingPipeline.value){await updatePipeline(editingPipeline.value.id,payload)}else{await createPipeline(payload)}closeDialog();await loadPipelines()}catch(e:any){dialogError.value=e?.message||"操作失败"}finally{submitting.value=false}
}
async function handleDisable(p:PipelineDefinitionResponse){if(!confirm(`确认禁用 "${p.displayName}"？`))return;try{await disablePipeline(p.id);await loadPipelines()}catch(e:any){alert(e?.message||"操作失败")}}
async function loadPipelines(){pipelineLoading.value=true;try{pipelines.value=await fetchPipelines()}catch{pipelines.value=[]}pipelineLoading.value=false}
onMounted(()=>{loadPipelines();loadProfiles();if(activeTab.value==='jobs'){loadJobs();startJobAutoRefresh();}});
watch(activeTab, (v) => { if(v==='jobs'){loadJobs();startJobAutoRefresh();}else{if(jobRefreshTimer)clearInterval(jobRefreshTimer);} });
onUnmounted(()=>{if(jobRefreshTimer)clearInterval(jobRefreshTimer);});
</script>

<style scoped>
.dialog-form{display:flex;flex-direction:column;gap:16px}
.form-section{border:1px solid var(--color-border-soft);border-radius:var(--radius-lg);padding:16px}
.form-section-title{font-size:13px;font-weight:600;color:var(--color-text-primary);margin-bottom:12px}
.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}
.form-field{display:flex;flex-direction:column;gap:4px}
.form-label{font-size:12px;color:var(--color-text-secondary);font-weight:500}
.dialog-error{color:#d92d20;font-size:13px;padding:8px 12px;background:#fef2f2;border-radius:6px;border:1px solid #fecaca}
.chip-row{display:flex;flex-wrap:wrap;gap:4px;margin-bottom:4px}
.chip{font-size:11px;padding:2px 8px;border-radius:12px;border:1px solid var(--color-border-default);background:#f8f9fb;display:inline-flex;align-items:center;gap:4px}
.chip button{background:none;border:none;color:#999;cursor:pointer;font-size:14px;line-height:1;padding:0}
.chip-active{background:var(--color-brand-50);border-color:var(--color-brand-200);color:var(--color-brand-700)}
</style>
