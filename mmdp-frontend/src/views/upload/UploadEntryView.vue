<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>数据资产接入</h1>
        <p>上传文件、导入数据包、登记外部资产路径</p>
      </div>
      <button class="light2-btn light2-btn-sec" :disabled="!canClearCurrentMode" @click="requestClearWorkspace">清空工作台</button>
    </div>

    <!-- mode tabs -->
    <div class="chip-group" style="margin-bottom:20px">
      <button
        v-for="option in ingestModeOptions"
        :key="option.key"
        type="button"
        class="chip-item"
        :class="activeIngestMode === option.key ? 'chip-item-active' : ''"
        @click="setActiveIngestMode(option.key)"
      >
        {{ option.title }}
      </button>
    </div>

    <div class="grid gap-4 xl:grid-cols-[minmax(0,1.6fr)_320px]">
      <form @submit.prevent="handleSubmit">
        <!-- upload workspace card -->
        <div class="rounded-[12px] border bg-white p-5 shadow-[var(--shadow-card)]" style="border-color:var(--color-border-soft)">
          <!-- context selects -->
          <section class="grid gap-3 lg:grid-cols-[minmax(220px,1fr)_minmax(220px,1fr)]" style="margin-bottom:16px">
            <label class="block">
              <span class="mb-1 block text-[12px] font-medium" style="color:var(--color-text-secondary)">
                归属 Task <span style="color:#c5222f">*</span>
              </span>
              <select v-model="form.taskId" required class="app-input app-input-compact" @change="onTaskChange">
                <option value="" disabled>请选择 Task</option>
                <option v-for="task in tasks" :key="task.id" :value="String(task.id)">
                  {{ task.taskName }} ({{ task.taskCode }})
                </option>
              </select>
            </label>

            <label v-if="activeIngestMode !== 'archive'" class="block">
              <span class="mb-1 block text-[12px] font-medium" style="color:var(--color-text-secondary)">关联 Session</span>
              <select v-model="form.sessionId" class="app-input app-input-compact">
                <option value="">自动创建新 Session</option>
                <option v-for="sess in sessions" :key="sess.id" :value="String(sess.id)">
                  {{ sess.sessionCode ?? sess.sessionId }} - {{ sess.actionName }}
                </option>
              </select>
            </label>
          </section>

          <!-- drop zone / archive / external -->
          <section class="rounded-[12px] border p-4" style="border-color:var(--color-border-soft);background:var(--color-surface-muted);margin-bottom:16px">
            <template v-if="activeIngestMode === 'files'">
              <div
                class="rounded-[12px] border-2 border-dashed px-5 py-10 text-center cursor-pointer transition"
                style="border-color:rgba(0,0,0,0.10)"
                @dragover.prevent
                @drop.prevent="onDropFiles"
                @click="triggerFileInput"
                @mouseover="(e: MouseEvent) => (e.target as HTMLElement).style.borderColor = 'var(--color-brand-500)'"
                @mouseleave="(e: MouseEvent) => (e.target as HTMLElement).style.borderColor = 'rgba(0,0,0,0.10)'"
              >
                <div style="font-size:44px;margin-bottom:10px;opacity:0.5">↑</div>
                <p style="font-size:15px;font-weight:700;color:var(--color-text-primary)">拖拽文件到此处或点击选择</p>
                <p style="font-size:12px;color:var(--color-text-tertiary);margin-top:6px">支持 MP4、MOV、CSV、NPZ、ZIP、PNG 序列，单文件不超过 200MB</p>
                <input ref="fileInputRef" type="file" multiple class="hidden" @change="onFilesSelected" />
              </div>
            </template>

            <template v-else-if="activeIngestMode === 'archive'">
              <div class="grid gap-3 lg:grid-cols-[minmax(0,1fr)_280px]">
                <div class="rounded-[12px] border-2 border-dashed px-5 py-6 text-center" style="border-color:#fde68a;background:rgba(254,247,224,0.6)">
                  <p style="font-size:15px;font-weight:700;color:var(--color-text-primary);margin-bottom:8px">选择标准 Session 目录</p>
                  <p style="font-size:12px;color:var(--color-text-secondary);line-height:1.6">
                    目录必须包含根级 <code>manifest.json</code>、<code>sources/</code>，以及可选的 <code>artifacts/</code>。
                  </p>
                  <label class="inline-flex cursor-pointer rounded-[10px] border bg-white px-3 py-1.5 text-xs font-medium hover:bg-slate-50" style="margin-top:12px;border-color:var(--color-border-default);color:var(--color-text-primary)">
                    选择目录
                    <input ref="directoryInputRef" type="file" multiple webkitdirectory directory class="hidden" @change="onDirectorySelected" />
                  </label>
                  <p v-if="!directoryUploadSupported" style="font-size:12px;color:#c5222f;margin-top:8px">当前浏览器不支持目录上传，请使用桌面版 Chrome / Edge。</p>
                </div>

                <div class="rounded-[12px] border px-3 py-3" style="border-color:var(--color-border-soft);background:var(--color-surface-muted)">
                  <div style="font-size:10px;font-weight:700;color:var(--color-text-tertiary);letter-spacing:0.06em;text-transform:uppercase;margin-bottom:8px">目录规范</div>
                  <div class="rounded-[10px] bg-white px-3 py-3 font-mono text-[11px] leading-5" style="color:var(--color-text-secondary)">
                    <div>session_dir/</div>
                    <div>├─ manifest.json</div>
                    <div>├─ sources/</div>
                    <div>└─ artifacts/</div>
                  </div>
                  <div style="font-size:12px;color:var(--color-text-secondary);line-height:1.6;margin-top:10px">
                    前端会把目录内每个文件直传到临时导入区，并在全部上传完成后调用 `finalize` 完成 Session 收口。
                  </div>
                </div>
              </div>
            </template>

            <template v-else>
              <div>
                <p style="font-size:15px;font-weight:700;color:var(--color-text-primary);margin-bottom:12px">外部资产登记</p>
                <div class="grid gap-3 md:grid-cols-2">
                  <label class="block md:col-span-2">
                    <span class="mb-1 block text-[12px] font-medium" style="color:var(--color-text-secondary)">外部路径 / Object Key</span>
                    <input v-model="externalDraftForm.path" class="app-input app-input-compact" placeholder="例如：oss://bucket/session_001/video/main.mp4" />
                  </label>
                  <label class="block">
                    <span class="mb-1 block text-[12px] font-medium" style="color:var(--color-text-secondary)">存储类型</span>
                    <select v-model="externalDraftForm.storageType" class="app-input app-input-compact">
                      <option v-for="item in storageTypeOptions" :key="item" :value="item">{{ item }}</option>
                    </select>
                  </label>
                  <label class="block">
                    <span class="mb-1 block text-[12px] font-medium" style="color:var(--color-text-secondary)">显示名称</span>
                    <input v-model="externalDraftForm.displayName" class="app-input app-input-compact" placeholder="例如：session_001_result" />
                  </label>
                  <label class="block md:col-span-2">
                    <span class="mb-1 block text-[12px] font-medium" style="color:var(--color-text-secondary)">备注</span>
                    <textarea v-model="externalDraftForm.remark" rows="2" class="app-input resize-y" placeholder="记录来源、结构说明或补充信息" />
                  </label>
                </div>
                <div class="flex flex-wrap items-center gap-3" style="margin-top:12px">
                  <button type="button" class="light2-btn light2-btn-primary light2-btn-sm" :disabled="!canAddExternalPreview" @click="addExternalPreviewItem">添加到预览</button>
                  <span style="font-size:12px;color:var(--color-text-secondary)">当前仅保留页面入口和预览，不接真实提交。</span>
                </div>
              </div>
            </template>
          </section>

          <!-- pending list -->
          <div class="light2-tbl" style="margin-bottom:0">
            <div style="display:flex;align-items:center;justify-content:space-between;padding:14px 16px;border-bottom:1px solid rgba(0,0,0,0.05)">
              <div>
                <div style="font-size:13px;font-weight:700;color:var(--color-text-primary)">待处理清单</div>
                <div style="font-size:12px;color:var(--color-text-secondary);margin-top:2px">当前模式下待上传或待导入的内容</div>
              </div>
              <div style="display:flex;gap:8px;font-size:12px">
                <span class="light2-badge light2-badge-neutral">{{ previewItemCount }} 项</span>
                <span class="light2-badge light2-badge-neutral">{{ previewTotalSizeLabel }}</span>
              </div>
            </div>

            <div v-if="pendingAssetItems.length" style="overflow-x:auto">
              <table>
                <thead>
                  <tr>
                    <th>文件 / 路径</th>
                    <th>模式</th>
                    <th>状态</th>
                    <th>大小</th>
                    <th style="text-align:right">操作</th>
                  </tr>
                </thead>

                <!-- 折叠模式：按目录分组 -->
                <tbody v-if="isCollapsed && groupedEntries">
                  <template v-for="group in groupedEntries" :key="group.folder">
                    <!-- 分组摘要行 -->
                    <tr class="group-summary-row" @click="toggleGroup(group.folder)">
                      <td colspan="5" style="padding:8px 14px">
                        <div style="display:flex;align-items:center;justify-content:space-between">
                          <div style="display:flex;align-items:center;gap:10px">
                            <span style="transition:transform 0.15s" :style="{ transform: expandedGroups.has(group.folder) ? 'rotate(90deg)' : '' }">▸</span>
                            <span style="font-size:18px">📁</span>
                            <span style="font-weight:600;font-size:13px;color:var(--color-text-primary)">{{ group.folder }}/</span>
                            <span class="light2-badge light2-badge-neutral">{{ group.count }} 个文件</span>
                          </div>
                          <span style="font-size:12px;color:var(--color-text-secondary)">{{ formatSize(group.totalSize) }}</span>
                        </div>
                      </td>
                    </tr>
                    <!-- 展开的文件明细行 -->
                    <template v-if="expandedGroups.has(group.folder)">
                      <tr v-for="item in pendingAssetItems.filter(p => group.files.some(f => f.key === p.key))" :key="item.key">
                        <td>
                          <div style="max-width:360px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:500" :title="item.name">{{ item.name }}</div>
                          <div v-if="item.secondaryText" style="font-size:11px;color:var(--color-text-tertiary);margin-top:2px">{{ item.secondaryText }}</div>
                        </td>
                        <td style="color:var(--color-text-secondary)">{{ item.ingestModeLabel }}</td>
                        <td>
                          <div style="min-width:180px">
                            <div style="display:flex;align-items:center;gap:8px">
                              <span class="light2-badge" :class="{ 'light2-badge-ok': item.stateLabel === '完成', 'light2-badge-warn': item.stateLabel === '上传中', 'light2-badge-info': item.stateLabel === '已上传' || item.stateLabel === '收口中', 'light2-badge-err': item.stateLabel.includes('失败'), 'light2-badge-neutral': item.stateLabel === '等待上传' || item.stateLabel === '仅预览' }">
                                <span class="light2-bdot" :style="{ background: item.stateLabel === '完成' ? '#0d7d3e' : item.stateLabel === '上传中' ? '#b87a0a' : item.stateLabel.includes('失败') ? '#c5222f' : item.stateLabel === '等待上传' || item.stateLabel === '仅预览' ? '#9298a3' : 'var(--color-brand-500)' }" />
                                {{ item.stateLabel }}
                              </span>
                              <span v-if="item.showPercent" style="font-size:12px;font-weight:500;color:var(--color-text-secondary)">{{ item.progress }}%</span>
                            </div>
                            <div v-if="item.showProgressBar" style="margin-top:8px">
                              <div class="progress-track">
                                <div class="progress-fill" :class="item.progress >= 100 ? 'progress-fill-done' : 'progress-fill-active'" :style="{ width: `${item.progress}%` }" />
                              </div>
                              <div style="font-size:11px;color:var(--color-text-tertiary);margin-top:4px">{{ item.progressHint }}</div>
                            </div>
                          </div>
                        </td>
                        <td style="color:var(--color-text-secondary)">{{ item.sizeLabel }}</td>
                        <td style="text-align:right">
                          <button v-if="item.canRemove" type="button" style="font-size:12px;font-weight:500;color:var(--color-text-tertiary);background:none;border:none;cursor:pointer" @click="item.remove()">移除</button>
                          <span v-else style="font-size:11px;color:var(--color-text-tertiary)">{{ item.removeHint }}</span>
                        </td>
                      </tr>
                    </template>
                  </template>
                </tbody>

                <!-- 普通模式：逐行展示 -->
                <tbody v-else>
                  <tr v-for="item in pendingAssetItems" :key="item.key">
                    <td>
                      <div style="max-width:360px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-weight:500" :title="item.name">{{ item.name }}</div>
                      <div v-if="item.secondaryText" style="font-size:11px;color:var(--color-text-tertiary);margin-top:2px">{{ item.secondaryText }}</div>
                    </td>
                    <td style="color:var(--color-text-secondary)">{{ item.ingestModeLabel }}</td>
                    <td>
                      <div style="min-width:180px">
                        <div style="display:flex;align-items:center;gap:8px">
                          <span class="light2-badge" :class="{ 'light2-badge-ok': item.stateLabel === '完成', 'light2-badge-warn': item.stateLabel === '上传中', 'light2-badge-info': item.stateLabel === '已上传' || item.stateLabel === '收口中', 'light2-badge-err': item.stateLabel.includes('失败'), 'light2-badge-neutral': item.stateLabel === '等待上传' || item.stateLabel === '仅预览' }">
                            <span class="light2-bdot" :style="{ background: item.stateLabel === '完成' ? '#0d7d3e' : item.stateLabel === '上传中' ? '#b87a0a' : item.stateLabel.includes('失败') ? '#c5222f' : item.stateLabel === '等待上传' || item.stateLabel === '仅预览' ? '#9298a3' : 'var(--color-brand-500)' }" />
                            {{ item.stateLabel }}
                          </span>
                          <span v-if="item.showPercent" style="font-size:12px;font-weight:500;color:var(--color-text-secondary)">{{ item.progress }}%</span>
                        </div>
                        <div v-if="item.showProgressBar" style="margin-top:8px">
                          <div class="progress-track">
                            <div class="progress-fill" :class="item.progress >= 100 ? 'progress-fill-done' : 'progress-fill-active'" :style="{ width: `${item.progress}%` }" />
                          </div>
                          <div style="font-size:11px;color:var(--color-text-tertiary);margin-top:4px">{{ item.progressHint }}</div>
                        </div>
                      </div>
                    </td>
                    <td style="color:var(--color-text-secondary)">{{ item.sizeLabel }}</td>
                    <td style="text-align:right">
                      <button v-if="item.canRemove" type="button" style="font-size:12px;font-weight:500;color:var(--color-text-tertiary);background:none;border:none;cursor:pointer" @click="item.remove()">移除</button>
                      <span v-else style="font-size:11px;color:var(--color-text-tertiary)">{{ item.removeHint }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <div v-else style="padding:32px 0;text-align:center;font-size:13px;color:var(--color-text-secondary)">暂无待处理内容。</div>

            <!-- remark -->
            <div style="border-top:1px solid rgba(0,0,0,0.05);padding:12px 16px">
              <label class="block">
                <span class="mb-1 block text-[12px] font-medium" style="color:var(--color-text-secondary)">补充说明</span>
                <input v-model="form.remark" class="app-input app-input-compact" placeholder="可填写本次上传或导入的说明" />
              </label>
            </div>

            <!-- footer actions -->
            <div style="display:flex;flex-direction:column;gap:12px;border-top:1px solid rgba(0,0,0,0.05);padding:16px;background:rgba(255,255,255,0.97)" class="md:flex-row md:items-center md:justify-between">
              <div>
                <div v-if="message" :class="messageBannerClass" class="rounded-[10px] px-3 py-2 text-xs font-medium">{{ message }}</div>
                <div v-else style="font-size:12px;color:var(--color-text-secondary)">{{ footerSummaryText }}</div>
              </div>
              <div style="display:flex;align-items:center;justify-content:flex-end;gap:8px;flex-wrap:wrap">
                <button v-if="canStopCurrentMode" type="button" class="light2-btn light2-btn-sec light2-btn-sm" @click="stopCurrentUpload()">
                  停止导入
                </button>
                <button type="button" class="light2-btn light2-btn-sec light2-btn-sm" :disabled="!canClearCurrentMode" @click="requestClearWorkspace">清空当前工作台</button>
                <button type="submit" class="light2-btn light2-btn-primary" :disabled="!canSubmitCurrentMode">{{ submitButtonText }}</button>
              </div>
            </div>
          </div>
        </div>
      </form>

      <!-- side panels -->
      <div class="space-y-4">
        <div class="rounded-[12px] border bg-white p-4 shadow-[var(--shadow-card)]" style="border-color:var(--color-border-soft)">
          <div style="font-size:10px;font-weight:700;color:var(--color-text-tertiary);letter-spacing:0.06em;text-transform:uppercase;margin-bottom:12px">归属信息</div>
          <div v-if="selectedTask" class="space-y-2 text-sm">
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">Task</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ selectedTask.taskName }}</span></div>
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">Task Code</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ selectedTask.taskCode }}</span></div>
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">被试编号</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ selectedTask.subjectCode || "-" }}</span></div>
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">Profile</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ selectedTask.profileName || "-" }}</span></div>
            <div v-if="batchSessionCode" class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">最近 Session</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ batchSessionCode }}</span></div>
          </div>
          <div v-else style="font-size:12px;color:var(--color-text-secondary)">请先选择 Task。</div>
        </div>

        <div class="rounded-[12px] border bg-white p-4 shadow-[var(--shadow-card)]" style="border-color:var(--color-border-soft)">
          <div style="font-size:10px;font-weight:700;color:var(--color-text-tertiary);letter-spacing:0.06em;text-transform:uppercase;margin-bottom:12px">导入结果</div>
          <div class="space-y-2 text-sm">
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">模式</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ activeIngestModeOption.title }}</span></div>
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">localSessionId</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ importResult?.localSessionId ?? "-" }}</span></div>
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">platformSessionCode</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ importResult?.platformSessionCode ?? (batchSessionCode || "-") }}</span></div>
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">状态</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ importResult?.status ?? "-" }}</span></div>
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">幂等命中</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ importResult?.existing ? "是" : "否" }}</span></div>
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">创建文件数</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ importResult?.createdFileCount ?? "-" }}</span></div>
            <div class="flex items-start justify-between gap-3"><span style="color:var(--color-text-tertiary)">创建资产数</span><span style="text-align:right;font-weight:600;color:var(--color-text-primary)">{{ importResult?.createdAssetCount ?? "-" }}</span></div>
          </div>
        </div>
        <div v-if="importedSessionDetailLink || importedSessionListLink" class="mt-3 flex flex-wrap gap-2">
          <RouterLink v-if="importedSessionDetailLink" :to="importedSessionDetailLink" class="light2-btn light2-btn-sec light2-btn-sm">
            查看 Session 详情
          </RouterLink>
          <RouterLink v-if="importedSessionListLink" :to="importedSessionListLink" class="light2-btn light2-btn-sec light2-btn-sm">
            在采集总页定位
          </RouterLink>
        </div>

        <!-- upload progress sidebar -->
        <div v-if="showOverallProgress" class="rounded-[12px] border bg-white p-4 shadow-[var(--shadow-card)]" style="border-color:var(--color-border-soft)">
          <div style="font-size:10px;font-weight:700;color:var(--color-text-tertiary);letter-spacing:0.06em;text-transform:uppercase;margin-bottom:8px">上传进度</div>
          <div class="metric-value-lg" style="margin-bottom:4px">{{ totalProgressPercent }}%</div>
          <div class="progress-track" style="height:8px;margin-bottom:4px">
            <div class="progress-fill progress-fill-active" :style="{ width: `${totalProgressPercent}%` }" />
          </div>
          <div style="font-size:12px;color:var(--color-text-tertiary)">{{ Math.floor(uploadedBytes / 1024 / 1024) }} / {{ Math.floor(totalProgressBytes / 1024 / 1024) }} MB</div>
        </div>
      </div>
    </div>

    <AppDialog
      :open="clearDialogOpen"
      size="sm"
      title="清空当前工作台"
      :description="effectiveClearDialogDescription"
        confirm-text="确认清空"
      @close="clearDialogOpen = false"
      @confirm="confirmClearWorkspace"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { RouterLink } from "vue-router";
import { fetchAcquisitionList } from "@/api/platform";
import {
  fetchTaskSessions,
  finalizeSessionImport,
  type FinalizeSessionImportResponse,
  type FinalizeSessionImportUploadedFile,
  type SessionResponse,
} from "@/api/sessions";
import AppDialog from "@/components/AppDialog.vue";
import type { TaskResponse } from "@/types/task";
import {
  DirectUploadCompletionError,
  directUploadSessionImportFileToOss,
  directUploadToOss,
  inferAssetTypeForFile,
  retryDirectUploadCompletion,
  type DirectUploadStage,
} from "@/utils/ossDirectUpload";
import {
  clearWorkspace,
  hasActiveItems,
  hasWorkspaceEntries,
  type IngestModeKey,
  type UploadItemState,
  type UploadPhase,
  type UploadStateRecord,
  uploadWorkspaceState,
} from "@/views/upload/uploadWorkspaceState";

interface PendingAssetItem {
  key: string;
  name: string;
  secondaryText?: string;
  ingestModeLabel: string;
  sizeLabel: string;
  stateLabel: string;
  stateBadgeClass: string;
  progress: number;
  showPercent: boolean;
  showProgressBar: boolean;
  progressBarClass: string;
  progressHint: string;
  canRemove: boolean;
  removeHint?: string;
  remove: () => void;
}

interface UploadEntryProgressItem {
  key: string;
  name: string;
  size: number;
  state?: UploadStateRecord;
}

interface DirectoryManifestSummary {
  localSessionId: string | null;
  profileCode: string | null;
  subjectCode: string | null;
  actionName: string | null;
  sourceCount: number;
}

const tasks = ref<TaskResponse[]>([]);
const sessions = ref<SessionResponse[]>([]);
const fileInputRef = ref<HTMLInputElement | null>(null);
const directoryInputRef = ref<HTMLInputElement | null>(null);
const abortControllers = new Map<string, AbortController>();
const clearDialogOpen = ref(false);
const clearDialogVariant = ref<"active" | "normal">("normal");
const importResult = ref<FinalizeSessionImportResponse | null>(null);
const manifestSummary = ref<DirectoryManifestSummary | null>(null);
const activeRunId = ref<string | null>(null);
const activeRunMode = ref<IngestModeKey | null>(null);
const stopping = ref(false);
const finalizeInFlight = ref(false);

const {
  submitting,
  message,
  uploadError,
  batchSessionId,
  batchSessionCode,
  activeIngestMode,
  externalPreviewItems,
  form,
  externalDraftForm,
  uploadStates,
} = uploadWorkspaceState;

const ingestModeOptions = [
  {
    key: "files" as IngestModeKey,
    title: "普通文件上传",
    activeClass: "border-[var(--color-brand-500)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]",
    inactiveClass: "border-[var(--color-border-default)] bg-white text-[var(--color-text-secondary)] hover:border-[var(--color-brand-200)]",
  },
  {
    key: "archive" as IngestModeKey,
    title: "标准 Session 目录导入",
    activeClass: "border-amber-400 bg-amber-50 text-amber-800",
    inactiveClass: "border-[var(--color-border-default)] bg-white text-[var(--color-text-secondary)] hover:border-amber-200",
  },
  {
    key: "external" as IngestModeKey,
    title: "外部资产登记",
    activeClass: "border-emerald-400 bg-emerald-50 text-emerald-800",
    inactiveClass: "border-[var(--color-border-default)] bg-white text-[var(--color-text-secondary)] hover:border-emerald-200",
  },
];

const storageTypeOptions = ["OSS", "MinIO", "NAS", "Server Path", "Other"];

const activeIngestModeOption = computed(
  () => ingestModeOptions.find((item) => item.key === activeIngestMode.value) ?? ingestModeOptions[0],
);
const selectedTask = computed(() => tasks.value.find((task) => String(task.id) === form.taskId) ?? null);
const selectedSession = computed(() => sessions.value.find((session) => String(session.id) === form.sessionId) ?? null);
const importedSessionDetailLink = computed(() =>
  importResult.value?.localSessionId ? `/sessions/${encodeURIComponent(importResult.value.localSessionId)}` : "",
);
const importedSessionListLink = computed(() => {
  const sessionCode = importResult.value?.platformSessionCode ?? batchSessionCode.value;
  if (!sessionCode) {
    return "";
  }
  return `/sessions?sessionCode=${encodeURIComponent(sessionCode)}`;
});
const directoryUploadSupported = computed(() => typeof HTMLInputElement !== "undefined" && "webkitdirectory" in document.createElement("input"));

function createRunId() {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function isCurrentRun(runId: string | null | undefined) {
  return Boolean(runId) && activeRunId.value === runId;
}

function startRun(mode: IngestModeKey) {
  const runId = createRunId();
  activeRunId.value = runId;
  activeRunMode.value = mode;
  stopping.value = false;
  finalizeInFlight.value = false;
  submitting.value = true;
  return runId;
}

function completeRun(runId: string) {
  if (!isCurrentRun(runId)) {
    return;
  }
  activeRunId.value = null;
  activeRunMode.value = null;
  stopping.value = false;
  finalizeInFlight.value = false;
  submitting.value = false;
}

function patchUploadStateIfCurrent(runId: string, key: string, patch: Partial<UploadStateRecord>) {
  if (!isCurrentRun(runId)) {
    return;
  }
  const current = uploadStates[key] ?? { progress: 0, state: "idle" as UploadItemState, phase: "upload" as UploadPhase };
  uploadStates[key] = { ...current, ...patch };
}

function setRunMessageIfCurrent(runId: string, nextMessage: string, isError = false) {
  if (!isCurrentRun(runId)) {
    return;
  }
  message.value = nextMessage;
  uploadError.value = isError;
}

function isAbortLike(error: unknown) {
  return error instanceof DOMException && error.name === "AbortError";
}

function stopCurrentUpload(silent = false) {
  if (!hasActiveRun.value || !activeRunId.value) {
    return;
  }

  const finalizeWasInFlight = finalizeInFlight.value;
  stopping.value = true;

  abortControllers.forEach((controller) => controller.abort());
  abortControllers.clear();

  Object.keys(uploadStates).forEach((key) => {
    const state = uploadStates[key];
    if (!state || !["initiating", "uploading", "uploaded", "completing"].includes(state.state)) {
      return;
    }
    uploadStates[key] = {
      ...state,
      state: "error",
      phase: state.state === "uploaded" || state.state === "completing" ? "complete" : "upload",
      message: finalizeWasInFlight ? "已停止等待结果" : "已停止导入",
    };
  });

  activeRunId.value = null;
  activeRunMode.value = null;
  finalizeInFlight.value = false;
  stopping.value = false;
  submitting.value = false;

  if (!silent) {
    message.value = finalizeWasInFlight
      ? "已停止前端等待；后台可能仍在完成本次导入，请以后端结果为准。"
      : "当前导入已停止，可重新选择目录并再次导入。";
    uploadError.value = false;
  }
}

const archiveEntries = computed<UploadEntryProgressItem[]>(() =>
  form.archiveFiles.map((file, index) => {
    const relativePath = getDirectoryRelativePath(file);
    const key = buildArchiveKey(file, index);
    return { key, name: relativePath, size: file.size, state: uploadStates[key] };
  }),
);

const uploadEntryItems = computed<UploadEntryProgressItem[]>(() => {
  if (activeIngestMode.value === "archive") return archiveEntries.value;
  if (activeIngestMode.value !== "files") return [];
  return form.selectedFiles.map((file, index) => {
    const key = buildFileKey(file, index);
    return { key, name: file.name, size: file.size, state: uploadStates[key] };
  });
});

const previewItemCount = computed(() => {
  if (activeIngestMode.value === "external") return externalPreviewItems.value.length;
  return uploadEntryItems.value.length;
});

const previewTotalBytes = computed(() => uploadEntryItems.value.reduce((total, item) => total + item.size, 0));
const hasActiveRun = computed(() => activeRunId.value !== null);
const previewTotalSizeLabel = computed(() => activeIngestMode.value === "external" ? "外部路径预览" : formatSize(previewTotalBytes.value));

// ── 待处理清单折叠分组 ──
const COLLAPSE_THRESHOLD = 15;
const expandedGroups = ref<Set<string>>(new Set());
const isCollapsed = computed(() => uploadEntryItems.value.length > COLLAPSE_THRESHOLD);

interface GroupedEntry {
  folder: string;
  files: UploadEntryProgressItem[];
  totalSize: number;
  count: number;
}

const groupedEntries = computed<GroupedEntry[] | null>(() => {
  if (!isCollapsed.value) return null;
  const groups = new Map<string, UploadEntryProgressItem[]>();
  for (const item of uploadEntryItems.value) {
    const folder = item.name.includes("/") ? item.name.split("/")[0] : "根目录";
    if (!groups.has(folder)) groups.set(folder, []);
    groups.get(folder)!.push(item);
  }
  // 有上传中文件的分组默认展开
  for (const [folder, files] of groups) {
    const hasActive = files.some((f) => f.state && ["initiating", "uploading", "uploaded", "completing"].includes(f.state.state));
    if (hasActive) expandedGroups.value.add(folder);
  }
  return Array.from(groups.entries()).map(([folder, files]) => ({
    folder,
    files,
    totalSize: files.reduce((sum, f) => sum + f.size, 0),
    count: files.length,
  }));
});

function toggleGroup(folder: string) {
  const next = new Set(expandedGroups.value);
  if (next.has(folder)) next.delete(folder);
  else next.add(folder);
  expandedGroups.value = next;
}

const submittableEntryItems = computed(() =>
  uploadEntryItems.value.filter((item) => {
    const stateName = item.state?.state;
    return stateName == null || stateName === "idle" || stateName === "error";
  }),
);

const canSubmit = computed(() => {
  if (!form.taskId || activeIngestMode.value === "external") return false;
  if (activeIngestMode.value === "archive" && !directoryUploadSupported.value) return false;
  return submittableEntryItems.value.length > 0;
});

const modeUploadingCount = computed(() =>
  uploadEntryItems.value.filter((item) => item.state && ["initiating", "uploading"].includes(item.state.state)).length,
);
const modeCompletingCount = computed(() =>
  uploadEntryItems.value.filter((item) => item.state && ["uploaded", "completing"].includes(item.state.state)).length,
);

const totalProgressBytes = computed(() => uploadEntryItems.value.reduce((total, item) => total + item.size, 0));
const uploadedBytes = computed(() =>
  uploadEntryItems.value.reduce((total, item) => total + (item.size * (item.state?.progress ?? 0)) / 100, 0),
);
const totalProgressPercent = computed(() => {
  if (!totalProgressBytes.value) return 0;
  return Math.max(0, Math.min(100, Math.floor((uploadedBytes.value / totalProgressBytes.value) * 100)));
});

const headerMeta = computed(() => [
  { label: "模式", value: activeIngestModeOption.value.title },
  { label: "Task", value: selectedTask.value?.taskCode ?? "未选择" },
  { label: "待处理", value: `${previewItemCount.value} 项` },
]);

const showOverallProgress = computed(() =>
  activeIngestMode.value !== "external" && uploadEntryItems.value.some((item) => item.state && item.state.state !== "idle"),
);

const footerSummaryText = computed(() => {
  if (activeIngestMode.value === "archive") {
    if (manifestSummary.value) {
      return `目录导入已识别：localSessionId=${manifestSummary.value.localSessionId ?? "-"}，sources=${manifestSummary.value.sourceCount}。`;
    }
    return "目录导入会先直传到临时导入区，再调用 finalize 完成 Session 收口。";
  }
  if (activeIngestMode.value === "files") {
    return "普通文件上传会沿用现有 OSS 直传与文件登记能力。";
  }
  return "外部资产登记当前仅保留预览入口。";
});

const submitButtonText = computed(() => {
  if (activeIngestMode.value === "external") return "暂不提交";
  if (stopping.value) return "停止中...";
  if (modeUploadingCount.value > 0) return "上传中...";
  if (modeCompletingCount.value > 0 || submitting.value) return "处理中...";
  return activeIngestMode.value === "archive" ? "开始目录导入" : "提交上传";
});

const canSubmitCurrentMode = computed(() => !hasActiveRun.value && !stopping.value && !submitting.value && canSubmit.value);
const canStopCurrentMode = computed(() =>
  !stopping.value &&
  hasActiveRun.value &&
  activeRunMode.value != null &&
  activeRunMode.value !== "external",
);
const canAddExternalPreview = computed(() => Boolean(externalDraftForm.path.trim() && externalDraftForm.displayName.trim()));
const canClearCurrentMode = computed(() => hasWorkspaceEntries() || hasActiveRun.value);
const messageBannerClass = computed(() =>
  uploadError.value
    ? "border border-[var(--color-danger-100)] bg-[var(--color-danger-100)] text-[var(--color-danger-700)]"
    : "border border-[var(--color-success-100)] bg-[var(--color-success-100)] text-[var(--color-success-700)]",
);

const clearDialogDescription = computed(() =>
  clearDialogVariant.value === "active"
    ? "当前仍有文件正在上传或收口，清空后将无法继续跟踪本轮进度。确定要清空吗？"
    : "确定清空当前上传工作台吗？",
);

const effectiveClearDialogDescription = computed(() =>
  clearDialogVariant.value === "active"
    ? "当前仍有文件正在上传或收口，清空前会先停止当前导入，然后再重置工作台。确定要继续吗？"
    : "确定清空当前上传工作台吗？",
);

const pendingAssetItems = computed<PendingAssetItem[]>(() => {
  if (activeIngestMode.value === "external") {
    return externalPreviewItems.value.map((item) => ({
      key: item.key,
      name: item.displayName,
      secondaryText: `${item.storageType} / ${item.path}`,
      ingestModeLabel: "外部资产登记",
      sizeLabel: "外部路径",
      stateLabel: "仅预览",
      stateBadgeClass: "border-amber-200 bg-amber-50 text-amber-700",
      progress: 0,
      showPercent: false,
      showProgressBar: false,
      progressBarClass: "bg-slate-300",
      progressHint: "",
      canRemove: true,
      remove: () => removeExternalPreviewItem(item.key),
    }));
  }

  return uploadEntryItems.value.map((item) => buildPendingAssetItem({
    key: item.key,
    name: item.name,
    secondaryText: activeIngestMode.value === "archive" ? "标准 Session 目录文件" : "普通上传文件",
    ingestModeLabel: activeIngestMode.value === "archive" ? "目录导入" : "普通上传",
    sizeLabel: formatSize(item.size),
    state: item.state,
    remove: () => {
      if (activeIngestMode.value === "archive") {
        removeArchiveFileByKey(item.key);
        return;
      }
      removeFileByKey(item.key);
    },
  }));
});

function buildPendingAssetItem(input: {
  key: string;
  name: string;
  secondaryText?: string;
  ingestModeLabel: string;
  sizeLabel: string;
  state?: UploadStateRecord;
  remove: () => void;
}): PendingAssetItem {
  const progress = input.state?.progress ?? 0;
  const isBusy = input.state ? ["initiating", "uploading", "uploaded", "completing"].includes(input.state.state) : false;
  return {
    key: input.key,
    name: input.name,
    secondaryText: input.secondaryText,
    ingestModeLabel: input.ingestModeLabel,
    sizeLabel: input.sizeLabel,
    stateLabel: resolveUploadStateLabel(input.state),
    stateBadgeClass: resolveUploadStateBadgeClass(input.state),
    progress,
    showPercent: Boolean(input.state && input.state.state !== "idle"),
    showProgressBar: Boolean(input.state && input.state.state !== "idle"),
    progressBarClass: resolveProgressBarClass(input.state),
    progressHint: resolveProgressHint(input.state),
    canRemove: !isBusy,
    removeHint: isBusy ? "上传中不可移除" : undefined,
    remove: input.remove,
  };
}

function setActiveIngestMode(mode: IngestModeKey) {
  if (hasActiveRun.value) return;
  activeIngestMode.value = mode;
  form.uploadMode = mode === "archive" ? "archive" : "files";
  importResult.value = null;
  message.value = "";
  uploadError.value = false;
  if (mode === "archive") {
    form.sessionId = "";
  }
}

function triggerFileInput() {
  if (hasActiveRun.value) return;
  fileInputRef.value?.click();
}

function requestClearWorkspace() {
  if (!canClearCurrentMode.value) return;
  clearDialogVariant.value = hasActiveRun.value || hasActiveItems() ? "active" : "normal";
  clearDialogOpen.value = true;
}

function confirmClearWorkspace() {
  clearDialogOpen.value = false;
  if (hasActiveRun.value) {
    stopCurrentUpload(true);
  }
  clearWorkspace({ preserveTask: true, preserveSession: activeIngestMode.value !== "archive", showMessage: true });
  importResult.value = null;
  manifestSummary.value = null;
}

function addExternalPreviewItem() {
  if (!canAddExternalPreview.value) return;
  externalPreviewItems.value.unshift({
    key: `${externalDraftForm.path}-${Date.now()}`,
    path: externalDraftForm.path.trim(),
    storageType: externalDraftForm.storageType,
    displayName: externalDraftForm.displayName.trim(),
    remark: externalDraftForm.remark.trim(),
    assetTypeKey: "external-path",
  });
  externalDraftForm.path = "";
  externalDraftForm.displayName = "";
  externalDraftForm.remark = "";
}

function removeExternalPreviewItem(key: string) {
  externalPreviewItems.value = externalPreviewItems.value.filter((item) => item.key !== key);
}

function buildFileKey(file: File, index: number) {
  return `${file.name}-${file.size}-${index}`;
}

function buildArchiveKey(file: File, index: number) {
  return `${getDirectoryRelativePath(file)}-${file.size}-${index}`;
}

function removeFileByKey(key: string) {
  const index = form.selectedFiles.findIndex((file, fileIndex) => buildFileKey(file, fileIndex) === key);
  if (index >= 0) {
    delete uploadStates[key];
    form.selectedFiles.splice(index, 1);
  }
}

function removeArchiveFileByKey(key: string) {
  const index = form.archiveFiles.findIndex((file, fileIndex) => buildArchiveKey(file, fileIndex) === key);
  if (index >= 0) {
    delete uploadStates[key];
    form.archiveFiles.splice(index, 1);
  }
  if (form.archiveFiles.length === 0) {
    manifestSummary.value = null;
  }
}

function onDropFiles(event: DragEvent) {
  if (hasActiveRun.value) return;
  const files = event.dataTransfer?.files;
  if (files) {
    addFilesWithValidation(Array.from(files), form.selectedFiles, buildFileKey);
  }
}

function onFilesSelected(event: Event) {
  if (hasActiveRun.value) return;
  const input = event.target as HTMLInputElement;
  if (input.files) {
    addFilesWithValidation(Array.from(input.files), form.selectedFiles, buildFileKey);
  }
  input.value = "";
}

async function onDirectorySelected(event: Event) {
  if (hasActiveRun.value) return;
  const input = event.target as HTMLInputElement;
  const files = input.files ? Array.from(input.files) : [];
  if (!files.length) return;

  form.archiveFiles = [];
  Object.keys(uploadStates).forEach((key) => {
    if (!form.selectedFiles.some((file, index) => buildFileKey(file, index) === key)) {
      delete uploadStates[key];
    }
  });

  const validFiles = addFilesWithValidation(files, form.archiveFiles, buildArchiveKey);
  if (validFiles.length) {
    try {
      manifestSummary.value = summarizeManifest(await parseManifestFromFiles(validFiles));
      message.value = `目录预检通过：识别到 ${validFiles.length} 个文件。`;
      uploadError.value = false;
    } catch (error) {
      manifestSummary.value = null;
      message.value = error instanceof Error ? error.message : "目录预检失败";
      uploadError.value = true;
    }
  }
  input.value = "";
}

function addFilesWithValidation(
  files: File[],
  target: File[],
  buildKey: (file: File, index: number) => string,
) {
  message.value = "";
  uploadError.value = false;
  const accepted: File[] = [];
  for (const file of files) {
    if (!validateFileSize(file)) {
      break;
    }
    target.push(file);
    accepted.push(file);
    const key = buildKey(file, target.length - 1);
    uploadStates[key] = { progress: 0, state: "idle", phase: "upload" };
  }
  return accepted;
}

const MAX_FILE_SIZE = 1024 * 1024 * 1024;

function validateFileSize(file: File): boolean {
  if (file.size > MAX_FILE_SIZE) {
      message.value = `文件 "${file.name}" 大小为 ${formatSize(file.size)}，超过单文件上限 ${formatSize(MAX_FILE_SIZE)}。`;
    uploadError.value = true;
    return false;
  }
  return true;
}

function legacyApplyUploadStage(key: string, stage: DirectUploadStage) {
  const current = uploadStates[key] ?? { progress: 0, state: "idle" as UploadItemState, phase: "upload" as UploadPhase };
  if (stage === "uploaded") {
    uploadStates[key] = { ...current, progress: 100, state: "uploaded", phase: "upload" };
  } else if (stage === "completing") {
    uploadStates[key] = { ...current, progress: 100, state: "completing", phase: "complete" };
  }
}

async function legacyUploadSingleFile(taskId: number, file: File, sessionId?: number) {
  const key = buildFileKey(file, form.selectedFiles.indexOf(file));
  const controller = new AbortController();
  abortControllers.set(key, controller);
  uploadStates[key] = { progress: 0, state: "initiating", phase: "upload" };
  try {
    const result = await directUploadToOss({
      taskId,
      file,
      sessionId,
      assetType: inferAssetTypeForFile(file.name),
      signal: controller.signal,
      onProgress: (progress) => {
        uploadStates[key] = { ...uploadStates[key], progress, state: progress >= 100 ? "uploaded" : "uploading", phase: "upload" };
      },
      onStageChange: (stage) => legacyApplyUploadStage(key, stage),
    });
    batchSessionId.value = result.sessionId;
    batchSessionCode.value = result.sessionCode;
    uploadStates[key] = { ...uploadStates[key], progress: 100, state: "success", phase: "complete" };
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      uploadStates[key] = { ...uploadStates[key], state: "error", phase: "upload", message: "上传已中止" };
      return;
    }
    if (error instanceof DirectUploadCompletionError) {
      if (error.sessionId && batchSessionId.value == null) batchSessionId.value = error.sessionId;
      uploadStates[key] = { ...uploadStates[key], progress: 100, state: "complete_failed", phase: "complete", fileId: error.fileId, message: error.message };
    } else {
      uploadStates[key] = { ...uploadStates[key], state: "error", phase: "upload", message: error instanceof Error ? error.message : "上传失败" };
    }
    throw error;
  } finally {
    abortControllers.delete(key);
  }
}

async function legacySubmitDirectoryImport(taskId: number) {
  const files = [...form.archiveFiles];
  const manifest = await parseManifestFromFiles(files);
  manifestSummary.value = summarizeManifest(manifest);

  const importKey = `dirimp-${taskId}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const uploadedFiles: FinalizeSessionImportUploadedFile[] = [];

  for (let index = 0; index < files.length; index += 1) {
    const file = files[index];
    const relativePath = getDirectoryRelativePath(file);
    const key = buildArchiveKey(file, index);
    const controller = new AbortController();
    abortControllers.set(key, controller);
    uploadStates[key] = { progress: 0, state: "initiating", phase: "upload" };
    try {
      const uploaded = await directUploadSessionImportFileToOss({
        taskId,
        importKey,
        file,
        relativePath,
        signal: controller.signal,
        onProgress: (progress) => {
          uploadStates[key] = { ...uploadStates[key], progress, state: progress >= 100 ? "uploaded" : "uploading", phase: "upload" };
        },
      });
      uploadStates[key] = { ...uploadStates[key], progress: 100, state: "uploaded", phase: "upload" };
      uploadedFiles.push(uploaded);
    } catch (error) {
      uploadStates[key] = {
        ...uploadStates[key],
        state: "error",
        phase: "upload",
        message: error instanceof Error ? error.message : "目录文件上传失败",
      };
      throw error;
    } finally {
      abortControllers.delete(key);
    }
  }

  for (let index = 0; index < files.length; index += 1) {
    const key = buildArchiveKey(files[index], index);
    uploadStates[key] = { ...uploadStates[key], progress: 100, state: "completing", phase: "complete" };
  }

  const result = await finalizeSessionImport({
    taskId,
    importKey,
    requestId: importKey,
    manifest,
    uploadedFiles,
  });

  importResult.value = result;
  batchSessionId.value = result.platformSessionId;
  batchSessionCode.value = result.platformSessionCode ?? "";

  for (let index = 0; index < files.length; index += 1) {
    const key = buildArchiveKey(files[index], index);
    uploadStates[key] = { ...uploadStates[key], progress: 100, state: "success", phase: "complete" };
  }

  message.value = result.existing
    ? `目录导入已幂等命中，Session ${result.platformSessionCode ?? result.platformSessionId ?? "-"} 已存在。`
    : `目录导入完成，平台 Session 为 ${result.platformSessionCode ?? result.platformSessionId ?? "-"}。`;
  uploadError.value = false;
}

async function legacySubmitUpload() {
  submitting.value = true;
  message.value = "";
  uploadError.value = false;
  importResult.value = null;
  batchSessionId.value = form.sessionId ? Number(form.sessionId) : null;
  batchSessionCode.value = selectedSession.value?.sessionCode ?? selectedSession.value?.sessionId ?? "";
  const taskId = Number(form.taskId);

  try {
    if (activeIngestMode.value === "archive") {
      await legacySubmitDirectoryImport(taskId);
    } else {
      let successCount = 0;
      let failCount = 0;
      for (const file of form.selectedFiles.filter((candidate, index) => {
        const stateName = uploadStates[buildFileKey(candidate, index)]?.state;
        return stateName == null || stateName === "idle" || stateName === "error";
      })) {
        try {
          await legacyUploadSingleFile(taskId, file, batchSessionId.value ?? undefined);
          successCount += 1;
        } catch {
          failCount += 1;
        }
      }
      if (failCount === 0) {
        message.value = `普通文件上传完成，共 ${successCount} 个文件。`;
        uploadError.value = false;
      } else {
        message.value = `${successCount} 个成功，${failCount} 个失败。`;
        uploadError.value = true;
      }
    }

    if (form.taskId) {
      sessions.value = await fetchTaskSessions(Number(form.taskId));
    }
  } catch (error) {
    uploadError.value = true;
    message.value = error instanceof Error ? error.message : "上传失败";
  } finally {
    submitting.value = false;
  }
}

function applyUploadStage(runId: string, key: string, stage: DirectUploadStage) {
  const current = uploadStates[key] ?? { progress: 0, state: "idle" as UploadItemState, phase: "upload" as UploadPhase };
  if (!isCurrentRun(runId)) {
    return;
  }
  if (stage === "uploaded") {
    uploadStates[key] = { ...current, progress: 100, state: "uploaded", phase: "upload" };
  } else if (stage === "completing") {
    uploadStates[key] = { ...current, progress: 100, state: "completing", phase: "complete" };
  }
}

async function uploadSingleFile(runId: string, taskId: number, file: File, sessionId?: number) {
  const key = buildFileKey(file, form.selectedFiles.indexOf(file));
  const controller = new AbortController();
  abortControllers.set(key, controller);
  patchUploadStateIfCurrent(runId, key, { progress: 0, state: "initiating", phase: "upload" });
  try {
    const result = await directUploadToOss({
      taskId,
      file,
      sessionId,
      assetType: inferAssetTypeForFile(file.name),
      signal: controller.signal,
      onProgress: (progress) => {
        patchUploadStateIfCurrent(runId, key, { progress, state: progress >= 100 ? "uploaded" : "uploading", phase: "upload" });
      },
      onStageChange: (stage) => applyUploadStage(runId, key, stage),
    });
    if (!isCurrentRun(runId)) {
      return false;
    }
    batchSessionId.value = result.sessionId;
    batchSessionCode.value = result.sessionCode;
    patchUploadStateIfCurrent(runId, key, { progress: 100, state: "success", phase: "complete" });
    return true;
  } catch (error) {
    if (isAbortLike(error)) {
      patchUploadStateIfCurrent(runId, key, { state: "error", phase: "upload", message: "上传已中止" });
      return false;
    }
    if (!isCurrentRun(runId)) {
      return false;
    }
    if (error instanceof DirectUploadCompletionError) {
      if (error.sessionId && batchSessionId.value == null) batchSessionId.value = error.sessionId;
      patchUploadStateIfCurrent(runId, key, { progress: 100, state: "complete_failed", phase: "complete", fileId: error.fileId, message: error.message });
    } else {
      patchUploadStateIfCurrent(runId, key, { state: "error", phase: "upload", message: error instanceof Error ? error.message : "上传失败" });
    }
    throw error;
  } finally {
    abortControllers.delete(key);
  }
}

async function submitDirectoryImport(runId: string, taskId: number) {
  const files = [...form.archiveFiles];
  const manifest = await parseManifestFromFiles(files);
  if (isCurrentRun(runId)) {
    manifestSummary.value = summarizeManifest(manifest);
  }

  const importKey = `dirimp-${taskId}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const uploadedFiles: FinalizeSessionImportUploadedFile[] = [];

  for (let index = 0; index < files.length; index += 1) {
    const file = files[index];
    const relativePath = getDirectoryRelativePath(file);
    const key = buildArchiveKey(file, index);
    const controller = new AbortController();
    abortControllers.set(key, controller);
    patchUploadStateIfCurrent(runId, key, { progress: 0, state: "initiating", phase: "upload" });
    try {
      const uploaded = await directUploadSessionImportFileToOss({
        taskId,
        importKey,
        file,
        relativePath,
        signal: controller.signal,
        onProgress: (progress) => {
          patchUploadStateIfCurrent(runId, key, { progress, state: progress >= 100 ? "uploaded" : "uploading", phase: "upload" });
        },
      });
      if (!isCurrentRun(runId)) {
        return false;
      }
      patchUploadStateIfCurrent(runId, key, { progress: 100, state: "uploaded", phase: "upload" });
      uploadedFiles.push(uploaded);
    } catch (error) {
      if (isAbortLike(error) || !isCurrentRun(runId)) {
        patchUploadStateIfCurrent(runId, key, { state: "error", phase: "upload", message: "目录文件上传已中止" });
        return false;
      }
      patchUploadStateIfCurrent(runId, key, {
        state: "error",
        phase: "upload",
        message: error instanceof Error ? error.message : "目录文件上传失败",
      });
      throw error;
    } finally {
      abortControllers.delete(key);
    }
  }

  if (!isCurrentRun(runId)) {
    return false;
  }

  for (let index = 0; index < files.length; index += 1) {
    const key = buildArchiveKey(files[index], index);
    patchUploadStateIfCurrent(runId, key, { progress: 100, state: "completing", phase: "complete" });
  }

  const finalizeKey = `__finalize__:${runId}`;
  const finalizeController = new AbortController();
  abortControllers.set(finalizeKey, finalizeController);
  finalizeInFlight.value = true;

  let result: FinalizeSessionImportResponse;
  try {
    result = await finalizeSessionImport({
      taskId,
      importKey,
      requestId: importKey,
      manifest,
      uploadedFiles,
    }, finalizeController.signal);
  } catch (error) {
    if (isAbortLike(error) || !isCurrentRun(runId)) {
      return false;
    }
    throw error;
  } finally {
    finalizeInFlight.value = false;
    abortControllers.delete(finalizeKey);
  }

  if (!isCurrentRun(runId)) {
    return false;
  }

  importResult.value = result;
  batchSessionId.value = result.platformSessionId;
  batchSessionCode.value = result.platformSessionCode ?? "";

  for (let index = 0; index < files.length; index += 1) {
    const key = buildArchiveKey(files[index], index);
    patchUploadStateIfCurrent(runId, key, { progress: 100, state: "success", phase: "complete" });
  }

  setRunMessageIfCurrent(
    runId,
    result.existing
      ? `目录导入已幂等命中，Session ${result.platformSessionCode ?? result.platformSessionId ?? "-"} 已存在。`
      : `目录导入完成，平台 Session 为 ${result.platformSessionCode ?? result.platformSessionId ?? "-"}。`,
    false,
  );
  return true;
}

async function submitUpload() {
  const runId = startRun(activeIngestMode.value);
  message.value = "";
  uploadError.value = false;
  importResult.value = null;
  batchSessionId.value = form.sessionId ? Number(form.sessionId) : null;
  batchSessionCode.value = selectedSession.value?.sessionCode ?? selectedSession.value?.sessionId ?? "";
  const taskId = Number(form.taskId);

  try {
    if (activeIngestMode.value === "archive") {
      const imported = await submitDirectoryImport(runId, taskId);
      if (!imported) {
        return;
      }
    } else {
      let successCount = 0;
      let failCount = 0;
      for (const file of form.selectedFiles.filter((candidate, index) => {
        const stateName = uploadStates[buildFileKey(candidate, index)]?.state;
        return stateName == null || stateName === "idle" || stateName === "error";
      })) {
        try {
          const uploaded = await uploadSingleFile(runId, taskId, file, batchSessionId.value ?? undefined);
          if (!isCurrentRun(runId)) {
            return;
          }
          if (uploaded) {
            successCount += 1;
          } else {
            failCount += 1;
          }
        } catch {
          if (!isCurrentRun(runId)) {
            return;
          }
          failCount += 1;
        }
      }
      if (!isCurrentRun(runId)) {
        return;
      }
      if (failCount === 0) {
        setRunMessageIfCurrent(runId, `普通文件上传完成，共 ${successCount} 个文件。`, false);
      } else {
        setRunMessageIfCurrent(runId, `${successCount} 个成功，${failCount} 个失败。`, true);
      }
    }

    if (form.taskId && isCurrentRun(runId)) {
      sessions.value = await fetchTaskSessions(Number(form.taskId));
    }
  } catch (error) {
    if (!isCurrentRun(runId) || isAbortLike(error)) {
      return;
    }
    uploadError.value = true;
    message.value = error instanceof Error ? error.message : "上传失败";
  } finally {
    completeRun(runId);
  }
}

function handleSubmit() {
  if (activeIngestMode.value === "external") return;
  void submitUpload();
}

async function onTaskChange() {
  sessions.value = [];
  form.sessionId = "";
  importResult.value = null;
  if (!form.taskId) return;
  try {
    sessions.value = await fetchTaskSessions(Number(form.taskId));
  } catch {
    // 忽略 Session 加载失败，不阻断上传
  }
}

function resolveUploadStateLabel(state?: UploadStateRecord) {
  if (!state) return "等待上传";
  switch (state.state) {
    case "initiating":
    case "uploading":
      return "上传中";
    case "uploaded":
      return "已上传";
    case "completing":
      return "收口中";
    case "success":
      return "完成";
    case "complete_failed":
      return "登记失败";
    case "error":
      return state.phase === "complete" ? "收口失败" : "上传失败";
    default:
      return "等待上传";
  }
}

function resolveUploadStateBadgeClass(state?: UploadStateRecord) {
  if (!state) return "border-slate-200 bg-slate-50 text-slate-600";
  switch (state.state) {
    case "success":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
    case "complete_failed":
    case "error":
      return "border-rose-200 bg-rose-50 text-rose-700";
    case "uploaded":
      return "border-sky-200 bg-sky-50 text-sky-700";
    case "completing":
      return "border-[var(--color-brand-200)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]";
    default:
      return "border-sky-200 bg-sky-50 text-sky-700";
  }
}

function resolveProgressBarClass(state?: UploadStateRecord) {
  if (!state) return "bg-slate-300";
  switch (state.state) {
    case "success":
      return "bg-emerald-500";
    case "complete_failed":
    case "error":
      return "bg-rose-500";
    case "completing":
      return "bg-[var(--color-brand-500)]";
    default:
      return "bg-sky-500";
  }
}

function resolveProgressHint(state?: UploadStateRecord) {
  if (!state) return "等待开始";
  switch (state.state) {
    case "initiating":
    case "uploading":
      return "文件正在上传到 OSS 临时导入区";
    case "uploaded":
      return "文件上传完成，等待 finalize";
    case "completing":
      return "平台正在创建 Session、data_file、data_asset";
    case "success":
      return "上传与登记已完成";
    case "complete_failed":
      return "文件已上传，但登记失败";
    case "error":
      return state.phase === "complete" ? "收口失败" : "上传失败";
    default:
      return "等待开始";
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function getDirectoryRelativePath(file: File) {
  const webkitRelativePath = (file as File & { webkitRelativePath?: string }).webkitRelativePath ?? file.name;
  const normalized = webkitRelativePath.replace(/\\/g, "/");
  const firstSlash = normalized.indexOf("/");
  return firstSlash >= 0 ? normalized.slice(firstSlash + 1) : normalized;
}

async function parseManifestFromFiles(files: File[]) {
  validateDirectoryEntries(files);
  const manifestFile = files.find((file) => getDirectoryRelativePath(file) === "manifest.json");
  if (!manifestFile) {
    throw new Error("目录根路径缺少 manifest.json");
  }
  const manifestText = await manifestFile.text();
  let manifest: Record<string, unknown>;
  try {
    manifest = JSON.parse(manifestText) as Record<string, unknown>;
  } catch {
    throw new Error("manifest.json 不是合法 JSON");
  }
  validateManifestPaths(manifest, files);
  return manifest;
}

function validateDirectoryEntries(files: File[]) {
  const seenPaths = new Set<string>();
  for (const file of files) {
    const relativePath = getDirectoryRelativePath(file);
    if (seenPaths.has(relativePath)) {
      throw new Error(`目录中存在重复文件路径: ${relativePath}`);
    }
    seenPaths.add(relativePath);
  }
}

function validateManifestPaths(manifest: Record<string, unknown>, files: File[]) {
  const filePathSet = new Set(files.map((file) => getDirectoryRelativePath(file)));
  const sourceEntries = collectSourceEntries(asObject(manifest.sources));
  if (!sourceEntries.length) {
    throw new Error("manifest.json 缺少有效的 sources 定义");
  }

  const referencedSourcePaths = new Set<string>();
  for (const entry of sourceEntries) {
    if (referencedSourcePaths.has(entry.path)) {
      throw new Error(`manifest.sources 中存在重复 path: ${entry.path}`);
    }
    referencedSourcePaths.add(entry.path);
    if (!filePathSet.has(entry.path) && !isDirectoryPathInSet(entry.path, filePathSet)) {
      throw new Error(`manifest.sources.${entry.sourceKey}.path 指向的文件不存在: ${entry.path}`);
    }
    const expectedPrefix = `sources/${entry.sourceKey}/`;
    if (!entry.path.startsWith(expectedPrefix)) {
      throw new Error(`manifest.sources.${entry.sourceKey}.path 必须位于 ${expectedPrefix} 中`);
    }
  }

  for (const artifactPath of collectArtifactPaths(manifest.artifacts)) {
    if (!filePathSet.has(artifactPath) && !isDirectoryPathInSet(artifactPath, filePathSet)) {
      throw new Error(`manifest.artifacts 中声明的文件不存在: ${artifactPath}`);
    }
    if (!artifactPath.startsWith("artifacts/")) {
      throw new Error(`manifest.artifacts.path 必须位于 artifacts/ 中: ${artifactPath}`);
    }
  }
}

function collectSourceEntries(sources: Record<string, unknown>) {
  return Object.entries(sources).flatMap(([sourceKey, value]) => {
    const source = asObject(value);
    const path = normalizeManifestPath(source.path);
    if (!path) {
      throw new Error(`manifest.sources.${sourceKey}.path 不能为空`);
    }
    return [{ sourceKey, path }];
  });
}

function collectArtifactPaths(value: unknown): string[] {
  if (typeof value === "string") {
    const path = normalizeManifestPath(value);
    return path ? [path] : [];
  }
  if (Array.isArray(value)) {
    return value.flatMap((item) => collectArtifactPaths(item));
  }
  if (value && typeof value === "object") {
    const objectValue = asObject(value);
    const directPath = normalizeManifestPath(objectValue.path);
    if (directPath) return [directPath];
    return Object.values(objectValue).flatMap((item) => collectArtifactPaths(item));
  }
  return [];
}

function normalizeManifestPath(value: unknown) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim().replace(/\\/g, "/").replace(/^\/+/, "");
  return trimmed || null;
}

/** 检查 path 是否指向一个目录——即有文件以 path/ 为前缀 */
function isDirectoryPathInSet(path: string, filePathSet: Set<string>) {
  const prefix = path.endsWith("/") ? path : path + "/";
  for (const fp of filePathSet) {
    if (fp.startsWith(prefix)) return true;
  }
  return false;
}

function summarizeManifest(manifest: Record<string, unknown>): DirectoryManifestSummary {
  const localRefs = asObject(manifest.localRefs);
  const task = asObject(manifest.task);
  const subject = asObject(manifest.subject);
  const action = asObject(manifest.action);
  const sources = asObject(manifest.sources);
  return {
    localSessionId: asString(localRefs.localSessionId) ?? asString(manifest.sessionId),
    profileCode: asString(task.profileCode) ?? asString(manifest.profileCode),
    subjectCode: asString(subject.code) ?? asString(manifest.subjectCode),
    actionName: asString(action.name) ?? asString(manifest.actionName),
    sourceCount: Object.keys(sources).length,
  };
}

function asObject(value: unknown): Record<string, unknown> {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : {};
}

function asString(value: unknown) {
  return typeof value === "string" && value.trim() ? value : null;
}

onMounted(async () => {
  try {
    const page = await fetchAcquisitionList();
    tasks.value = page.records;
  } catch {
    // 忽略列表初始化失败
  }
  if (form.taskId) {
    try {
      sessions.value = await fetchTaskSessions(Number(form.taskId));
    } catch {
      // 蹇界暐
    }
  }
});
</script>
