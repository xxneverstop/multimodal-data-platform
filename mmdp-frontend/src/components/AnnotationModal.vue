<template>
  <Teleport to="body">
    <div v-if="open" class="am-overlay" @click.self="$emit('close')">
      <div class="am-modal" @click.stop>
        <div class="am-hdr">
          <span>{{ mode === 'edit' ? '编辑标注' : '新增标注' }}</span>
          <button class="am-close" @click="$emit('close')">✕</button>
        </div>

        <div class="am-body">
          <!-- 标注范围 -->
          <label class="am-label">标注范围</label>
          <div class="am-range-row">
            <div class="am-range-item">
              <span class="am-range-hint">起始帧</span>
              <input
                type="number"
                v-model.number="form.frame"
                class="am-input"
                :min="0"
                :max="totalFrames"
              />
            </div>
            <div v-if="!form.singleFrame" class="am-range-item">
              <span class="am-range-hint">结束帧</span>
              <input
                type="number"
                v-model.number="form.endFrame"
                class="am-input"
                :min="form.frame"
                :max="totalFrames"
              />
            </div>
            <span v-if="!form.singleFrame && form.endFrame != null" class="am-range-count">
              {{ Math.abs((form.endFrame || form.frame) - form.frame) + 1 }}帧
            </span>
          </div>
          <label class="am-check">
            <input type="checkbox" v-model="form.singleFrame" />
            单帧标注（仅标记第 {{ form.frame }} 帧）
          </label>

          <hr class="am-divider" />

          <!-- 缺陷类型 -->
          <label class="am-label">缺陷类型</label>
          <div class="am-defect-grid">
            <button
              v-for="d in defectOptions"
              :key="d.value"
              class="am-chip"
              :class="{ active: form.defectType === d.value }"
              @click="form.defectType = form.defectType === d.value ? '' : d.value"
            >
              {{ d.label }}
            </button>
          </div>

          <!-- 严重程度 -->
          <label class="am-label" style="margin-top:12px">严重程度</label>
          <div class="am-severity-row">
            <label v-for="s in severityOpts" :key="s.value" class="am-severity-item" :style="{ borderColor: form.severity === s.value ? s.color : 'transparent' }">
              <input type="radio" v-model="form.severity" :value="s.value" />
              <span class="am-sev-dot" :style="{ background: s.color }" />
              {{ s.label }}
            </label>
          </div>

          <!-- 描述 -->
          <label class="am-label" style="margin-top:12px">描述</label>
          <textarea
            v-model="form.description"
            class="am-textarea"
            rows="3"
            :placeholder="mode === 'create' ? '描述该帧/段的问题...' : ''"
          />
        </div>

        <div class="am-footer">
          <button v-if="mode === 'edit'" class="am-btn am-btn-del" @click="$emit('delete')">删除</button>
          <div class="am-footer-right">
            <button class="am-btn am-btn-cancel" @click="$emit('close')">取消</button>
            <button class="am-btn am-btn-save" @click="handleSave">保存</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { reactive, watch } from "vue";
import type { FrameIssueItem } from "@/types/annotation";
import { SEVERITY_OPTIONS } from "@/types/annotation";

const props = defineProps<{
  open: boolean;
  mode: "create" | "edit";
  defaultFrame?: number;
  defaultEndFrame?: number;
  editIssue?: FrameIssueItem | null;
  totalFrames?: number;
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "save", issue: FrameIssueItem): void;
  (e: "delete"): void;
}>();

const defectOptions = [
  { value: "jointJump", label: "关节跳变" },
  { value: "sliding", label: "脚底滑动" },
  { value: "jointDeformity", label: "关节畸形" },
  { value: "floatPenetrate", label: "漂浮/穿透" },
  { value: "displacementMissing", label: "位移缺失" },
  { value: "flatScene", label: "平地场景" },
  { value: "temporalConsistency", label: "时间异常" },
  { value: "other", label: "其他" },
];

const severityOpts = SEVERITY_OPTIONS;

const form = reactive({
  frame: 0,
  endFrame: null as number | null,
  singleFrame: false,
  defectType: "",
  severity: "medium" as string,
  description: "",
});

// 重置表单
function resetForm() {
  form.frame = props.defaultFrame ?? 0;
  form.endFrame = props.defaultEndFrame ?? null;
  form.singleFrame = props.defaultEndFrame == null;
  form.defectType = "";
  form.severity = "medium";
  form.description = "";
}

// open 变化时重填
watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) return;
    if (props.mode === "edit" && props.editIssue) {
      const issue = props.editIssue;
      form.frame = issue.frame;
      form.endFrame = issue.endFrame ?? null;
      form.singleFrame = issue.endFrame == null;
      form.defectType = issue.defectType || issue.category || "";
      form.severity = issue.severity || "medium";
      form.description = issue.description || "";
    } else {
      resetForm();
    }
  },
  { immediate: true }
);

function handleSave() {
  const issue: FrameIssueItem = {
    frame: form.frame,
    endFrame: form.singleFrame ? undefined : (form.endFrame ?? undefined),
    description: form.description.trim(),
    severity: form.severity as FrameIssueItem["severity"],
    category: form.defectType,
    defectType: form.defectType || "other",
  };
  emit("save", issue);
}
</script>

<style scoped>
.am-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
  font-family: system-ui, -apple-system, sans-serif;
}

.am-modal {
  background: #1e1e1e;
  border: 1px solid #333;
  border-radius: 12px;
  width: 440px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  color: #d4d4d4;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.4);
}

.am-hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #333;
  font-size: 14px;
  font-weight: 600;
}
.am-close {
  background: none;
  border: none;
  color: #888;
  cursor: pointer;
  font-size: 16px;
  padding: 0 2px;
}

.am-body {
  padding: 16px 18px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.am-label {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  color: #888;
  letter-spacing: 0.05em;
}

.am-range-row {
  display: flex;
  gap: 10px;
  align-items: flex-end;
}
.am-range-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.am-range-hint {
  font-size: 10px;
  color: #666;
}
.am-range-count {
  font-size: 11px;
  color: #999;
  padding-bottom: 4px;
}

.am-input {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 5px;
  color: #e0e0e0;
  padding: 6px 10px;
  font-size: 13px;
  font-family: inherit;
  width: 80px;
}
.am-input:focus {
  outline: none;
  border-color: #2563eb;
}

.am-check {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #999;
  cursor: pointer;
  margin-top: 2px;
}
.am-check input {
  accent-color: #2563eb;
}

.am-divider {
  border: none;
  border-top: 1px solid #333;
  margin: 4px 0;
}

.am-defect-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 5px;
}
.am-chip {
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.03);
  color: #aaa;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  text-align: center;
  transition: all 0.12s;
}
.am-chip:hover {
  background: rgba(255, 255, 255, 0.06);
}
.am-chip.active {
  background: rgba(37, 99, 235, 0.2);
  border-color: #2563eb;
  color: #fff;
}

.am-severity-row {
  display: flex;
  gap: 8px;
}
.am-severity-item {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border: 2px solid transparent;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.03);
  cursor: pointer;
  font-size: 12px;
  color: #bbb;
  transition: all 0.12s;
}
.am-severity-item input {
  accent-color: #2563eb;
}
.am-sev-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.am-textarea {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: #e0e0e0;
  padding: 8px 10px;
  font-size: 12px;
  font-family: inherit;
  resize: vertical;
  line-height: 1.4;
}
.am-textarea:focus {
  outline: none;
  border-color: #2563eb;
}

.am-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 18px;
  border-top: 1px solid #333;
}
.am-footer-right {
  display: flex;
  gap: 8px;
  margin-left: auto;
}
.am-btn {
  padding: 7px 16px;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
  border: none;
}
.am-btn-cancel {
  background: rgba(255, 255, 255, 0.06);
  color: #999;
  border: 1px solid rgba(255, 255, 255, 0.1);
}
.am-btn-cancel:hover {
  background: rgba(255, 255, 255, 0.1);
}
.am-btn-save {
  background: #2563eb;
  color: #fff;
  font-weight: 600;
}
.am-btn-save:hover {
  background: #1d4ed8;
}
.am-btn-del {
  background: transparent;
  color: #ef4444;
  border: 1px solid rgba(239, 68, 68, 0.3);
}
.am-btn-del:hover {
  background: rgba(239, 68, 68, 0.1);
}
</style>
