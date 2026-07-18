<template>
  <div class="light2-page">
    <!-- header -->
    <div class="light2-hdr">
      <div>
        <h1>Session 整理工具</h1>
        <p>将原始采集文件整理为标准 Session 导入目录，自动分类并生成 manifest.json</p>
      </div>
    </div>

    <!-- Step indicator -->
    <div class="mb-5 flex items-center gap-3">
      <div
        v-for="(step, idx) in steps"
        :key="step.key"
        class="flex items-center gap-2"
      >
        <span
          class="inline-flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold transition"
          :class="
            currentStep === step.key
              ? 'bg-[var(--color-brand-500)] text-white'
              : step.done
                ? 'bg-[var(--color-success-500)] text-white'
                : 'bg-[var(--color-surface-muted)] text-[var(--color-text-tertiary)]'
          "
        >
          {{ step.done ? '✓' : idx + 1 }}
        </span>
        <span
          class="text-sm font-medium transition"
          :class="
            currentStep === step.key
              ? 'text-[var(--color-text-primary)]'
              : 'text-[var(--color-text-tertiary)]'
          "
        >
          {{ step.label }}
        </span>
        <span v-if="idx < steps.length - 1" class="mx-1 h-px w-8 bg-[var(--color-border-default)]" />
      </div>
    </div>

    <!-- ================================================================ -->
    <!-- Step 1: 选择源目录 + Profile -->
    <!-- ================================================================ -->
    <div v-if="currentStep === 'select'" class="grid gap-5 lg:grid-cols-2">
      <!-- 源目录卡片 -->
      <div class="rounded-[12px] border bg-white p-5 shadow-[var(--shadow-card)]" style="border-color:var(--color-border-soft)">
        <h3 class="mb-1 text-[15px] font-bold text-[var(--color-text-primary)]">源数据目录</h3>
        <p class="mb-4 text-[12px] text-[var(--color-text-tertiary)]">选择包含原始采集文件的本地目录</p>

        <button
          type="button"
          class="light2-btn light2-btn-primary mb-3"
          @click="selectSourceDir"
        >
          <span style="font-size:16px;margin-right:4px">📁</span> 选择目录
        </button>

        <div v-if="sourceFiles.length > 0" class="rounded-[8px] border p-3" style="border-color:var(--color-border-soft);background:var(--color-surface-muted)">
          <p class="mb-2 text-[13px] font-semibold text-[var(--color-text-primary)]">
            已扫描 {{ sourceFiles.length }} 个文件
          </p>
          <div class="max-h-[200px] overflow-y-auto">
            <div
              v-for="f in sourceFiles"
              :key="f.relativePath"
              class="flex items-center justify-between border-b py-1.5 text-[12px]"
              style="border-color:var(--color-border-soft)"
            >
              <span class="truncate font-mono text-[var(--color-text-secondary)]">{{ f.relativePath }}</span>
              <span class="ml-2 shrink-0 text-[var(--color-text-tertiary)]">{{ humanSize(f.size) }}</span>
            </div>
          </div>
        </div>

        <p v-else-if="sourceDirName" class="mt-2 text-[13px] text-[var(--color-text-tertiary)]">
          目录为空或无法读取
        </p>
      </div>

      <!-- Profile 选择卡片 -->
      <div class="rounded-[12px] border bg-white p-5 shadow-[var(--shadow-card)]" style="border-color:var(--color-border-soft)">
        <h3 class="mb-1 text-[15px] font-bold text-[var(--color-text-primary)]">采集 Profile</h3>
        <p class="mb-4 text-[12px] text-[var(--color-text-tertiary)]">选择匹配的采集配置，决定 sourceKey 分类规则</p>

        <label class="mb-2 block">
          <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">
            Profile <span style="color:#c5222f">*</span>
          </span>
          <select
            v-model="selectedProfileCode"
            class="app-input app-input-compact"
            @change="onProfileChange"
          >
            <option value="" disabled>请选择 Profile</option>
            <option v-for="p in profiles" :key="p.id" :value="p.profileCode">
              {{ p.profileName }} ({{ p.profileCode }})
            </option>
          </select>
        </label>

        <!-- Profile sourceKey 信息 -->
        <div v-if="selectedProfile" class="mt-3 rounded-[8px] border p-3" style="border-color:var(--color-border-soft);background:var(--color-surface-muted)">
          <p class="mb-2 text-[13px] font-semibold text-[var(--color-text-primary)]">
            包含 {{ selectedProfile.sources.length }} 个 Source
          </p>
          <div class="space-y-1">
            <div
              v-for="s in selectedProfile.sources"
              :key="s.sourceKey"
              class="flex items-center gap-2 text-[12px]"
            >
              <span
                class="inline-block h-1.5 w-1.5 rounded-full"
                :class="s.required ? 'bg-[var(--color-danger-500)]' : 'bg-[var(--color-text-tertiary)]'"
              />
              <span class="font-semibold text-[var(--color-text-primary)]">{{ s.sourceKey }}</span>
              <span class="text-[var(--color-text-tertiary)]">({{ s.sourceType }})</span>
              <span v-if="s.required" class="text-[11px] text-[var(--color-danger-500)]">必填</span>
            </div>
          </div>
        </div>

        <div v-if="!selectedProfile" class="mt-3 rounded-[8px] border border-amber-200 bg-amber-50 p-2 text-[12px] text-amber-800">
          ⚠️ 未找到此 Profile 的 source 定义，请确认 Profile 已在平台中正确配置。
        </div>
      </div>

      <!-- 下一步按钮 -->
      <div class="lg:col-span-2 flex justify-end">
        <button
          type="button"
          class="light2-btn light2-btn-primary"
          :disabled="sourceFiles.length === 0 || !selectedProfileCode"
          @click="goToStep('configure')"
        >
          下一步：配置与预览 →
        </button>
      </div>
    </div>

    <!-- ================================================================ -->
    <!-- Step 2: 配置信息 + 预览分类 -->
    <!-- ================================================================ -->
    <div v-if="currentStep === 'configure'" class="space-y-5">
      <!-- 配置表单 -->
      <div class="rounded-[12px] border bg-white p-5 shadow-[var(--shadow-card)]" style="border-color:var(--color-border-soft)">
        <h3 class="mb-3 text-[15px] font-bold text-[var(--color-text-primary)]">Session 信息</h3>
        <div class="grid gap-4 lg:grid-cols-2">
          <label class="block">
            <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">
              受试者标识 (subjectCode) <span style="color:#c5222f">*</span>
            </span>
            <input
              v-model="form.subjectCode"
              type="text"
              class="app-input app-input-compact"
              placeholder="如 S-001"
              required
            />
          </label>
          <label class="block">
            <span class="mb-1 block text-[12px] font-medium text-[var(--color-text-secondary)]">
              动作名称 (actionName) <span style="color:#c5222f">*</span>
            </span>
            <input
              v-model="form.actionName"
              type="text"
              class="app-input app-input-compact"
              placeholder="如 Walking"
              required
            />
          </label>
        </div>
      </div>

      <!-- 缺失 required source 警告 -->
      <div
        v-if="missingRequired.length > 0"
        class="rounded-[10px] border border-red-200 bg-red-50 p-3 text-[13px] text-red-700"
      >
        ⚠️ 以下必填 source 缺少文件：
        <span class="font-semibold">{{ missingRequired.join(', ') }}</span>
      </div>

      <!-- 分类预览表格 -->
      <div class="light2-tbl">
        <table>
          <thead>
            <tr>
              <th style="width:40%">文件</th>
              <th style="width:15%">大小</th>
              <th style="width:30%">归类</th>
              <th style="width:15%">类型</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in classificationRows" :key="row.file.relativePath">
              <td>
                <span class="text-[13px] font-mono text-[var(--color-text-secondary)]">{{ row.file.relativePath || row.file.name }}</span>
              </td>
              <td>
                <span class="text-[12px] text-[var(--color-text-tertiary)]">{{ humanSize(row.file.size) }}</span>
              </td>
              <td>
                <select
                  v-model="row.targetKey"
                  class="app-input app-input-compact"
                  style="min-width:150px"
                  @change="onReclassify"
                >
                  <optgroup v-if="selectedProfile" label="Sources">
                    <option v-for="s in selectedProfile.sources" :key="s.sourceKey" :value="s.sourceKey">
                      sources/{{ s.sourceKey }}/
                    </option>
                  </optgroup>
                  <optgroup label="Artifacts">
                    <option
                      v-for="g in artifactGroups"
                      :key="'art-' + g"
                      :value="'__art__' + g"
                    >
                      artifacts/{{ g }}/
                    </option>
                  </optgroup>
                  <optgroup label="Other">
                    <option value="__unmatched__">❓ 未归类</option>
                  </optgroup>
                </select>
              </td>
              <td>
                <span class="light2-badge light2-badge-neutral" v-if="row.isArtifact">
                  <span class="light2-bdot" /> artifact
                </span>
                <span class="light2-badge light2-badge-info" v-else>
                  <span class="light2-bdot" /> source
                </span>
              </td>
            </tr>
            <tr v-if="classificationRows.length === 0">
              <td colspan="4" class="py-6 text-center text-[13px] text-[var(--color-text-tertiary)]">
                暂无分类数据
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 汇总 -->
      <div class="flex items-center gap-4 text-[13px] text-[var(--color-text-secondary)]">
        <span>Sources: <strong>{{ sourceKeyCount }}</strong> 个 key, <strong>{{ sourceFileCount }}</strong> 个文件</span>
        <span>Artifacts: <strong>{{ artifactList.length }}</strong> 个</span>
        <span v-if="unmatched.length > 0" class="text-amber-600">
          ⚠️ 未归类: <strong>{{ unmatched.length }}</strong> 个
        </span>
      </div>

      <!-- 按钮 -->
      <div class="flex justify-between">
        <button type="button" class="light2-btn light2-btn-sec" @click="goToStep('select')">
          ← 上一步
        </button>
        <button
          type="button"
          class="light2-btn light2-btn-primary"
          :disabled="!form.subjectCode || !form.actionName"
          @click="goToStep('generate')"
        >
          下一步：选择输出目录 →
        </button>
      </div>
    </div>

    <!-- ================================================================ -->
    <!-- Step 3: 选择输出目录 → 生成 -->
    <!-- ================================================================ -->
    <div v-if="currentStep === 'generate'" class="space-y-5">
      <!-- 生成摘要 -->
      <div class="rounded-[12px] border bg-white p-5 shadow-[var(--shadow-card)]" style="border-color:var(--color-border-soft)">
        <h3 class="mb-3 text-[15px] font-bold text-[var(--color-text-primary)]">确认生成</h3>

        <div class="mb-4 grid gap-2 text-[13px]">
          <div class="flex gap-2">
            <span class="text-[var(--color-text-tertiary)]">Profile:</span>
            <span class="font-semibold text-[var(--color-text-primary)]">{{ selectedProfileCode }}</span>
          </div>
          <div class="flex gap-2">
            <span class="text-[var(--color-text-tertiary)]">受试者:</span>
            <span class="font-semibold text-[var(--color-text-primary)]">{{ form.subjectCode }}</span>
          </div>
          <div class="flex gap-2">
            <span class="text-[var(--color-text-tertiary)]">动作:</span>
            <span class="font-semibold text-[var(--color-text-primary)]">{{ form.actionName }}</span>
          </div>
          <div class="flex gap-2">
            <span class="text-[var(--color-text-tertiary)]">来源文件:</span>
            <span class="font-semibold text-[var(--color-text-primary)]">{{ sourceFiles.length }} 个</span>
          </div>
          <div class="flex gap-2">
            <span class="text-[var(--color-text-tertiary)]">输出:</span>
            <span class="font-semibold text-[var(--color-text-primary)]">
              {{ sourceKeyCount }} sources + {{ artifactList.length }} artifacts + manifest.json
            </span>
          </div>
        </div>

        <button
          type="button"
          class="light2-btn light2-btn-primary mb-3"
          @click="selectOutputDir"
        >
          <span style="font-size:16px;margin-right:4px">📂</span>
          {{ outputDirName ? '重新选择输出目录' : '选择输出目录' }}
        </button>

        <div v-if="outputDirName" class="mb-4 rounded-[8px] border p-3" style="border-color:var(--color-brand-200);background:var(--color-brand-50)">
          <p class="text-[13px] font-semibold text-[var(--color-brand-700)]">输出目录：{{ outputDirName }}</p>
        </div>
      </div>

      <!-- 生成按钮 -->
      <div class="flex justify-between">
        <button type="button" class="light2-btn light2-btn-sec" @click="goToStep('configure')">
          ← 上一步
        </button>
        <button
          type="button"
          class="light2-btn light2-btn-primary"
          :disabled="!outputDirHandle || generating"
          @click="doGenerate"
        >
          <span v-if="generating" class="mr-2 inline-block animate-spin">⏳</span>
          {{ generating ? `正在写入 (${writeProgress})...` : '🚀 生成 Session 目录' }}
        </button>
      </div>

      <!-- 生成结果 -->
      <div
        v-if="generateResult"
        class="rounded-[12px] border p-5 shadow-[var(--shadow-card)]"
        :class="generateResult.success ? 'border-green-200 bg-green-50' : 'border-red-200 bg-red-50'"
      >
        <h3 class="mb-2 text-[15px] font-bold" :class="generateResult.success ? 'text-green-700' : 'text-red-700'">
          {{ generateResult.success ? '✅ 生成完成！' : '❌ 生成失败' }}
        </h3>
        <p class="text-[13px]" :class="generateResult.success ? 'text-green-600' : 'text-red-600'">
          {{ generateResult.message }}
        </p>
        <p v-if="generateResult.success" class="mt-2 text-[12px] text-[var(--color-text-tertiary)]">
          现在可以在<a href="/upload" class="text-[var(--color-brand-500)] underline">数据资产接入</a>页面选择此目录进行导入
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { fetchCollectionProfiles } from "@/api/profiles";
import type { CollectionProfileResponse, CollectionProfileSourceResponse } from "@/types/profile";
import {
  classifyFilesWithSources,
  buildManifest,
  toManifestEntries,
  humanSize,
  type FileEntry,
  type ArtifactEntry,
  type ClassificationResult,
} from "@/utils/sessionOrganizer";

// ============================================================================
// 状态
// ============================================================================

type StepKey = "select" | "configure" | "generate";

const steps = computed(() => [
  { key: "select" as StepKey, label: "选择源目录与 Profile", done: sourceFiles.value.length > 0 && !!selectedProfileCode.value },
  { key: "configure" as StepKey, label: "配置信息与预览", done: !!form.value.subjectCode && !!form.value.actionName },
  { key: "generate" as StepKey, label: "选择输出目录并生成", done: !!generateResult.value?.success },
]);

const currentStep = ref<StepKey>("select");

// Step 1
const sourceDirName = ref("");
const sourceFiles = ref<FileEntry[]>([]);
const sourceFileHandles = ref<{ handle: FileSystemFileHandle; relativePath: string }[]>([]);
const profiles = ref<CollectionProfileResponse[]>([]);
const selectedProfileCode = ref("");
const selectedProfile = computed(() =>
  profiles.value.find((p) => p.profileCode === selectedProfileCode.value) || null,
);

// Step 2
const form = ref({ subjectCode: "", actionName: "" });
const classification = ref<ClassificationResult>({
  sourceMap: {},
  artifactList: [],
  unmatched: [],
});
const classificationRows = ref<ClassificationRow[]>([]);

// Step 3
const outputDirHandle = ref<FileSystemDirectoryHandle | null>(null);
const outputDirName = ref("");
const generating = ref(false);
const writeProgress = ref("");
const generateResult = ref<{ success: boolean; message: string } | null>(null);

// ============================================================================
// 类型
// ============================================================================

interface ClassificationRow {
  file: FileEntry;
  targetKey: string; // sourceKey, "__art__group", or "__unmatched__"
  isArtifact: boolean;
  handle: FileSystemFileHandle;
}

// ============================================================================
// 计算属性
// ============================================================================

const artifactGroups = computed(() => {
  const groups = new Set<string>();
  for (const a of classification.value.artifactList) {
    groups.add(a.group);
  }
  for (const a of customArtifacts.value) {
    groups.add(a.group);
  }
  if (groups.size === 0) groups.add("other");
  return [...groups].sort();
});

const customArtifacts = ref<ArtifactEntry[]>([]);

const sourceKeyCount = computed(() => {
  let count = 0;
  for (const files of Object.values(classification.value.sourceMap)) {
    if (files.length > 0) count++;
  }
  return count;
});

const sourceFileCount = computed(() => {
  let count = 0;
  for (const files of Object.values(classification.value.sourceMap)) {
    count += files.length;
  }
  return count;
});

const artifactList = computed(() => classification.value.artifactList);
const unmatched = computed(() => classification.value.unmatched);

const missingRequired = computed(() => {
  if (!selectedProfile.value) return [];
  const required: string[] = [];
  for (const s of selectedProfile.value.sources) {
    if (s.required && (!classification.value.sourceMap[s.sourceKey] || classification.value.sourceMap[s.sourceKey].length === 0)) {
      required.push(s.sourceKey);
    }
  }
  return required;
});

// ============================================================================
// 方法
// ============================================================================

function goToStep(step: StepKey) {
  currentStep.value = step;
  generateResult.value = null;
}

async function selectSourceDir() {
  try {
    const handle = await window.showDirectoryPicker({ mode: "read" });
    sourceDirName.value = handle.name;

    const files: FileEntry[] = [];
    const fileHandles: { handle: FileSystemFileHandle; relativePath: string }[] = [];
    await readDirectoryRecursive(handle, "", files, fileHandles);

    sourceFiles.value = files;
    sourceFileHandles.value = fileHandles;

    // 自动执行分类
    if (selectedProfileCode.value) {
      runClassification();
    }
  } catch (err: any) {
    if (err.name !== "AbortError") {
      console.error("Failed to read directory:", err);
    }
  }
}

async function readDirectoryRecursive(
  dirHandle: FileSystemDirectoryHandle,
  prefix: string,
  files: FileEntry[],
  fileHandles: { handle: FileSystemFileHandle; relativePath: string }[],
) {
  for await (const [name, handle] of dirHandle.entries()) {
    if (name.startsWith(".")) continue;
    const relPath = prefix ? `${prefix}/${name}` : name;

    if (handle.kind === "file") {
      const fileHandle = handle as FileSystemFileHandle;
      const file = await fileHandle.getFile();
      files.push({ name, size: file.size, relativePath: relPath });
      fileHandles.push({ handle: fileHandle, relativePath: relPath });
    } else if (handle.kind === "directory") {
      await readDirectoryRecursive(handle as FileSystemDirectoryHandle, relPath, files, fileHandles);
    }
  }
}

async function loadProfiles() {
  try {
    profiles.value = await fetchCollectionProfiles();
  } catch (err) {
    console.error("Failed to load profiles:", err);
  }
}

function onProfileChange() {
  if (sourceFiles.value.length > 0) {
    runClassification();
  }
}

function runClassification() {
  if (!selectedProfileCode.value || sourceFiles.value.length === 0) return;

  const sources = selectedProfile.value?.sources || [];
  classification.value = classifyFilesWithSources(sourceFiles.value, selectedProfileCode.value, sources);
  customArtifacts.value = [];
  buildClassificationRows();
}

function buildClassificationRows() {
  const rows: ClassificationRow[] = [];

  // Sources
  for (const [sourceKey, files] of Object.entries(classification.value.sourceMap)) {
    for (const file of files) {
      const handle = sourceFileHandles.value.find((h) => h.relativePath === file.relativePath);
      rows.push({
        file,
        targetKey: sourceKey,
        isArtifact: false,
        handle: handle?.handle || null as any,
      });
    }
  }

  // Artifacts
  for (const art of classification.value.artifactList) {
    const fname = art.path.split("/").pop() || "";
    const file = sourceFiles.value.find((f) => f.name === fname);
    if (file) {
      const handle = sourceFileHandles.value.find((h) => h.relativePath === file.relativePath);
      rows.push({
        file,
        targetKey: `__art__${art.group}`,
        isArtifact: true,
        handle: handle?.handle || null as any,
      });
    }
  }

  // Custom artifacts
  for (const art of customArtifacts.value) {
    const fname = art.path.split("/").pop() || "";
    const file = sourceFiles.value.find((f) => f.name === fname);
    if (file && !rows.some((r) => r.file.relativePath === file.relativePath)) {
      const handle = sourceFileHandles.value.find((h) => h.relativePath === file.relativePath);
      rows.push({
        file,
        targetKey: `__art__${art.group}`,
        isArtifact: true,
        handle: handle?.handle || null as any,
      });
    }
  }

  // Unmatched
  for (const file of classification.value.unmatched) {
    const handle = sourceFileHandles.value.find((h) => h.relativePath === file.relativePath);
    rows.push({
      file,
      targetKey: "__unmatched__",
      isArtifact: false,
      handle: handle?.handle || null as any,
    });
  }

  classificationRows.value = rows;
}

function onReclassify() {
  // 从 rows 重建 classification
  const newSourceMap: Record<string, FileEntry[]> = {};
  const newArtifactList: ArtifactEntry[] = [];
  const newUnmatched: FileEntry[] = [];

  for (const row of classificationRows.value) {
    if (row.targetKey === "__unmatched__") {
      newUnmatched.push(row.file);
    } else if (row.targetKey.startsWith("__art__")) {
      const group = row.targetKey.replace("__art__", "");
      newArtifactList.push({
        path: `artifacts/${group}/${row.file.name}`,
        group,
        kind: "OTHER",
      });
    } else {
      newSourceMap[row.targetKey] = newSourceMap[row.targetKey] || [];
      newSourceMap[row.targetKey].push(row.file);
    }
  }

  classification.value = {
    sourceMap: newSourceMap,
    artifactList: newArtifactList,
    unmatched: newUnmatched,
  };
}

async function selectOutputDir() {
  try {
    const handle = await window.showDirectoryPicker({ mode: "readwrite" });
    outputDirHandle.value = handle;
    outputDirName.value = handle.name;
    generateResult.value = null;
  } catch (err: any) {
    if (err.name !== "AbortError") {
      console.error("Failed to select output directory:", err);
    }
  }
}

async function doGenerate() {
  if (!outputDirHandle.value) return;

  generating.value = true;
  writeProgress.value = "准备中...";
  generateResult.value = null;

  try {
    const sources = selectedProfile.value?.sources || [];
    const { sources: manifestSources, artifacts: manifestArtifacts } = toManifestEntries(
      classification.value.sourceMap,
      classification.value.artifactList,
      sources,
    );

    const sessionId = outputDirName.value;
    const startedAt = new Date().toISOString().replace(/\.\d{3}Z$/, "+08:00");

    const manifest = buildManifest({
      sessionId,
      profileCode: selectedProfileCode.value,
      subjectCode: form.value.subjectCode,
      actionName: form.value.actionName,
      clientId: "manual-import",
      startedAt,
      sources: manifestSources,
      artifacts: manifestArtifacts,
    });

    // Count total operations
    let totalOps = 0;
    for (const files of Object.values(classification.value.sourceMap)) totalOps += files.length;
    totalOps += classification.value.artifactList.length;
    totalOps += classification.value.unmatched.length;
    totalOps += 1; // manifest.json

    let completedOps = 0;

    // Create sources/ directories and write files
    for (const [sourceKey, files] of Object.entries(classification.value.sourceMap)) {
      if (files.length === 0) continue;
      const sourceDir = await ensureDir(outputDirHandle.value, `sources/${sourceKey}`);

      for (const file of files) {
        const row = classificationRows.value.find((r) => r.file.relativePath === file.relativePath);
        if (row?.handle) {
          const fileData = await row.handle.getFile();
          await writeFile(sourceDir, file.name, fileData);
        }
        completedOps++;
        writeProgress.value = `${completedOps}/${totalOps}`;
      }
    }

    // Create artifacts/ directories and write files
    const allArtifacts = [
      ...classification.value.artifactList,
      ...classification.value.unmatched.map((f) => ({
        path: `artifacts/other/${f.name}`,
        group: "other",
        kind: "OTHER",
      })),
    ];

    for (const art of allArtifacts) {
      const artDir = await ensureDir(outputDirHandle.value, `artifacts/${art.group}`);
      const fname = art.path.split("/").pop() || "";
      const row = classificationRows.value.find((r) => r.file.name === fname);
      if (row?.handle) {
        const fileData = await row.handle.getFile();
        await writeFile(artDir, fname, fileData);
      }
      completedOps++;
      writeProgress.value = `${completedOps}/${totalOps}`;
    }

    // Write manifest.json
    const manifestStr = JSON.stringify(manifest, null, 2) + "\n";
    await writeFile(outputDirHandle.value, "manifest.json", new Blob([manifestStr], { type: "application/json" }));
    completedOps++;
    writeProgress.value = `${completedOps}/${totalOps}`;

    generateResult.value = {
      success: true,
      message: `已生成 ${sourceKeyCount.value} 个 source、${allArtifacts.length} 个 artifact、manifest.json 到目录 "${outputDirName.value}"`,
    };
  } catch (err: any) {
    console.error("Generation failed:", err);
    generateResult.value = {
      success: false,
      message: err.message || "未知错误",
    };
  } finally {
    generating.value = false;
  }
}

async function ensureDir(parentHandle: FileSystemDirectoryHandle, path: string): Promise<FileSystemDirectoryHandle> {
  const parts = path.split("/").filter(Boolean);
  let current = parentHandle;
  for (const part of parts) {
    current = await current.getDirectoryHandle(part, { create: true });
  }
  return current;
}

async function writeFile(dirHandle: FileSystemDirectoryHandle, name: string, data: Blob | File) {
  const fileHandle = await dirHandle.getFileHandle(name, { create: true });
  const writable = await fileHandle.createWritable();
  await writable.write(data);
  await writable.close();
}

// ============================================================================
// 初始化
// ============================================================================

loadProfiles();

// 当 sourceFiles 变化且已选 profile 时自动重新分类
watch(
  () => selectedProfileCode.value,
  () => {
    if (sourceFiles.value.length > 0) runClassification();
  },
);
</script>
