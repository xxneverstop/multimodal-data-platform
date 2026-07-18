/**
 * sessionOrganizer.ts — Session 目录整理工具（纯函数）
 *
 * 将原始采集文件分类为 source/artifact，生成 manifest.json。
 * 逻辑与 data/tools/organize_session.py 完全对齐。
 */

import type { CollectionProfileSourceResponse } from "@/types/profile";

// ============================================================================
// 类型定义
// ============================================================================

export interface FileEntry {
  name: string;
  size: number;
  /** 相对于源目录的路径 */
  relativePath: string;
}

export interface ArtifactEntry {
  path: string;
  group: string;
  kind: string;
}

export interface ClassificationResult {
  /** sourceKey → 文件列表 */
  sourceMap: Record<string, FileEntry[]>;
  /** artifact 列表 */
  artifactList: ArtifactEntry[];
  /** 无法归类的文件 */
  unmatched: FileEntry[];
}

export interface ManifestSource {
  type: string;
  path: string;
}

export interface ManifestArtifact {
  path: string;
  kind: string;
}

export interface ManifestData {
  schemaVersion: string;
  clientId: string;
  localRefs: {
    localTaskId: string;
    localSessionId: string;
  };
  task: {
    name: string;
    profileCode: string;
  };
  subject: {
    code: string;
    name: string;
  };
  action: {
    code: string;
    name: string;
  };
  session: {
    startedAt: string;
    timestampPolicy: string;
  };
  sources: Record<string, ManifestSource>;
  artifacts: ManifestArtifact[];
}

// ============================================================================
// Profile 注册表（与 Python 脚本 PROFILE_REGISTRY 对齐）
// ============================================================================

interface SourceRule {
  extensions: string[];
  patterns: string[];
  sourceType: string;
  isDirectory?: boolean;
}

interface ArtifactRule {
  extensions: string[];
  patterns: string[];
  group: string;
  kind: string;
}

interface ProfileRules {
  sources: Record<string, SourceRule>;
  artifactRules: ArtifactRule[];
}

const PROFILE_REGISTRY: Record<string, ProfileRules> = {
  ZED_STEREO_IMU_V1: {
    sources: {
      zed: {
        extensions: [".svo2", ".svo"],
        patterns: ["zed"],
        sourceType: "zed_svo2",
      },
      imu: {
        extensions: [".csv"],
        patterns: ["position", "imu"],
        sourceType: "pose_csv",
      },
    },
    artifactRules: [
      { extensions: [".npz"], patterns: ["depth"], group: "zed", kind: "DEPTH_RAW" },
      { extensions: [".npz"], patterns: ["position"], group: "imu", kind: "POSE_CACHE" },
      { extensions: [".csv"], patterns: ["timestamp", "frame_timestamp"], group: "zed", kind: "FRAME_TIMESTAMPS_CSV" },
      { extensions: [".npz"], patterns: ["timestamp", "frame_timestamp"], group: "zed", kind: "FRAME_TIMESTAMPS_CACHE" },
    ],
  },
  BINOCULAR_HMD_IMU_V1: {
    sources: {
      cam01: {
        extensions: [".mp4", ".avi", ".mov"],
        patterns: ["cam01", "cam_01", "left", "cam_left"],
        sourceType: "video",
      },
      cam02: {
        extensions: [".mp4", ".avi", ".mov"],
        patterns: ["cam02", "cam_02", "right", "cam_right"],
        sourceType: "video",
      },
      hmd: {
        extensions: [".mp4", ".avi", ".mov"],
        patterns: ["hmd", "ego", "headset"],
        sourceType: "video",
      },
      imu: {
        extensions: [".csv", ".jsonl"],
        patterns: ["imu", "gyro", "accel"],
        sourceType: "imu_csv",
      },
    },
    artifactRules: [
      { extensions: [".json"], patterns: ["calibration", "calib", "intrinsics"], group: "calibration", kind: "CALIBRATION" },
    ],
  },
  FAKE_STEREO_IMU_V2: {
    sources: {
      left_frames: {
        extensions: [],
        patterns: ["left_frames"],
        sourceType: "jpg_sequence",
        isDirectory: true,
      },
      right_frames: {
        extensions: [],
        patterns: ["right_frames"],
        sourceType: "jpg_sequence",
        isDirectory: true,
      },
      imu: {
        extensions: [".csv"],
        patterns: ["imu"],
        sourceType: "imu_csv",
      },
      timestamps: {
        extensions: [".csv"],
        patterns: ["timestamp", "frame_timestamp"],
        sourceType: "timestamps_csv",
      },
    },
    artifactRules: [],
  },
};

// ============================================================================
// 启发式规则（profile 不在注册表时使用）
// ============================================================================

const FILENAME_SOURCE_HINTS: [string, string][] = [
  ["zed", "zed"],
  ["imu", "imu"],
  ["gyro", "imu"],
  ["accel", "imu"],
  ["position", "imu"],
  ["orientation", "imu"],
  ["cam01", "cam01"],
  ["cam_01", "cam01"],
  ["cam_left", "cam01"],
  ["left", "cam01"],
  ["cam02", "cam02"],
  ["cam_02", "cam02"],
  ["cam_right", "cam02"],
  ["right", "cam02"],
  ["hmd", "hmd"],
  ["ego", "hmd"],
  ["headset", "hmd"],
  ["timestamp", "timestamps"],
  ["depth", "depth"],
  ["motion", "motion"],
  ["bvh", "motion"],
];

// ============================================================================
// 核心分类函数
// ============================================================================

function matchRule(fileName: string, extensions: string[], patterns: string[]): boolean {
  const nameLower = fileName.toLowerCase();
  if (extensions.length > 0) {
    if (!extensions.some((ext) => nameLower.endsWith(ext))) {
      return false;
    }
  }
  if (patterns.length > 0) {
    if (!patterns.some((p) => nameLower.includes(p.toLowerCase()))) {
      return false;
    }
  }
  return true;
}

/**
 * 根据内置注册表分类文件
 */
function classifyByRegistry(files: FileEntry[], profileCode: string): ClassificationResult {
  const registry = PROFILE_REGISTRY[profileCode];
  const sourceDefs = registry.sources;
  const artifactRules = registry.artifactRules;

  const sourceMap: Record<string, FileEntry[]> = {};
  for (const sk of Object.keys(sourceDefs)) {
    if (!sourceDefs[sk].isDirectory) {
      sourceMap[sk] = [];
    }
  }

  const artifactList: ArtifactEntry[] = [];
  const unmatched: FileEntry[] = [];

  for (const file of files) {
    // 尝试匹配 source
    let matchedSource: string | null = null;
    for (const [sourceKey, rules] of Object.entries(sourceDefs)) {
      if (rules.isDirectory) continue;
      if (matchRule(file.name, rules.extensions, rules.patterns)) {
        matchedSource = sourceKey;
        break;
      }
    }

    if (matchedSource) {
      sourceMap[matchedSource].push(file);
      continue;
    }

    // 尝试匹配 artifact
    let matchedArtifact: ArtifactRule | null = null;
    for (const rule of artifactRules) {
      if (matchRule(file.name, rule.extensions, rule.patterns)) {
        matchedArtifact = rule;
        break;
      }
    }

    if (matchedArtifact) {
      artifactList.push({
        path: `artifacts/${matchedArtifact.group}/${file.name}`,
        group: matchedArtifact.group,
        kind: matchedArtifact.kind,
      });
      continue;
    }

    // 无法归类
    unmatched.push(file);
  }

  return { sourceMap, artifactList, unmatched };
}

/**
 * 纯启发式分类（profile 不在注册表时使用）
 */
function classifyHeuristic(files: FileEntry[]): ClassificationResult {
  const sourceMap: Record<string, FileEntry[]> = {};
  const artifactList: ArtifactEntry[] = [];
  const unmatched: FileEntry[] = [];

  for (const file of files) {
    const nameLower = file.name.toLowerCase();
    const ext = nameLower.includes(".") ? nameLower.substring(nameLower.lastIndexOf(".")) : "";

    // 按文件名关键词匹配
    let foundSource: string | null = null;
    for (const [keyword, sourceKey] of FILENAME_SOURCE_HINTS) {
      if (nameLower.includes(keyword)) {
        foundSource = sourceKey;
        break;
      }
    }

    if (foundSource === "depth") {
      artifactList.push({ path: `artifacts/depth/${file.name}`, group: "depth", kind: "DEPTH_RAW" });
      continue;
    }
    if (foundSource === "timestamps") {
      artifactList.push({ path: `artifacts/timestamps/${file.name}`, group: "timestamps", kind: "FRAME_TIMESTAMPS" });
      continue;
    }
    if (foundSource) {
      sourceMap[foundSource] = sourceMap[foundSource] || [];
      sourceMap[foundSource].push(file);
      continue;
    }

    // 按扩展名匹配
    if ([".svo2", ".svo"].includes(ext)) {
      sourceMap["zed"] = sourceMap["zed"] || [];
      sourceMap["zed"].push(file);
    } else if ([".mp4", ".avi", ".mov"].includes(ext)) {
      sourceMap["video"] = sourceMap["video"] || [];
      sourceMap["video"].push(file);
    } else if (ext === ".npz") {
      artifactList.push({ path: `artifacts/other/${file.name}`, group: "other", kind: "NPZ_CACHE" });
    } else if ([".csv", ".jsonl"].includes(ext)) {
      sourceMap["imu"] = sourceMap["imu"] || [];
      sourceMap["imu"].push(file);
    } else if (ext === ".json") {
      artifactList.push({ path: `artifacts/other/${file.name}`, group: "other", kind: "JSON_ARTIFACT" });
    } else if ([".md", ".txt"].includes(ext)) {
      const kind = nameLower.includes("readme") ? "README" : "DOCUMENT";
      artifactList.push({ path: `artifacts/docs/${file.name}`, group: "docs", kind });
    } else if ([".jpg", ".png", ".jpeg"].includes(ext)) {
      artifactList.push({ path: `artifacts/preview/${file.name}`, group: "preview", kind: "PREVIEW" });
    } else {
      unmatched.push(file);
    }
  }

  // 后处理：同目录下 ≥3 张图片提升为 source
  promoteImageSequences(sourceMap, artifactList);

  return { sourceMap, artifactList, unmatched };
}

function promoteImageSequences(sourceMap: Record<string, FileEntry[]>, artifactList: ArtifactEntry[]): void {
  // 统计每个"原目录"下的图片 artifact
  const imgByDir: Record<string, number> = {};
  for (const item of artifactList) {
    const fname = item.path.split("/").pop() || "";
    if (/\.(jpg|png|jpeg)$/i.test(fname)) {
      // 按 relativePath 的父目录分组
      // artifact 没有保存 relativePath，这里简化处理
      const dir = "images";
      imgByDir[dir] = (imgByDir[dir] || 0) + 1;
    }
  }
  // 简化：如果有 ≥3 个图片 artifact，全部提升为 image_sequence source
  for (const [dir, count] of Object.entries(imgByDir)) {
    if (count >= 3) {
      const toPromote: ArtifactEntry[] = [];
      const remaining: ArtifactEntry[] = [];
      for (const item of artifactList) {
        const fname = item.path.split("/").pop() || "";
        if (/\.(jpg|png|jpeg)$/i.test(fname)) {
          toPromote.push(item);
        } else {
          remaining.push(item);
        }
      }
      // 移到 sourceMap
      for (const item of toPromote) {
        const fname = item.path.split("/").pop() || "";
        sourceMap["image_sequence"] = sourceMap["image_sequence"] || [];
        // 创建简化的 FileEntry
        sourceMap["image_sequence"].push({ name: fname, size: 0, relativePath: fname });
      }
      // 原地修改 artifactList（清空后重填）
      artifactList.length = 0;
      artifactList.push(...remaining);
    }
  }
}

/**
 * 主入口：将文件列表分类为 source/artifact。
 *
 * @param files - 待分类的文件列表
 * @param profileCode - Profile 代码（如 ZED_STEREO_IMU_V1）
 * @returns 分类结果
 */
export function classifyFiles(
  files: FileEntry[],
  profileCode: string,
): ClassificationResult {
  if (PROFILE_REGISTRY[profileCode]) {
    return classifyByRegistry(files, profileCode);
  }
  return classifyHeuristic(files);
}

/**
 * 主入口：使用 API 返回的 profile sources 为主力进行分类。
 * API 数据足够时直接用它；只有 API 无数据时才回退到硬编码注册表。
 */
export function classifyFilesWithSources(
  files: FileEntry[],
  profileCode: string,
  profileSources: CollectionProfileSourceResponse[],
): ClassificationResult {
  // 主力：用 API 返回的 sources 定义分类
  if (profileSources.length > 0) {
    return classifyByApiSources(files, profileSources);
  }

  // 兜底：API 没有返回 sources（不太可能，但做保护）
  return classifyFiles(files, profileCode);
}

/**
 * 将 SQL LIKE 的 filePattern 转为文件名匹配。
 * % 匹配任意字符，其他字符精确匹配（按顺序）。
 * 例：%episode_%.hdf5 → 匹配 "episode_0.hdf5", "episode_5.hdf5"
 */
function matchFilePattern(fileName: string, sqlLikePattern: string): boolean {
  // 按 % 切分，保留所有片段
  const fragments = sqlLikePattern.split("%");
  if (fragments.length === 1) {
    // 没有 % → 精确匹配
    return fileName.toLowerCase() === sqlLikePattern.toLowerCase();
  }

  const nameLower = fileName.toLowerCase();
  let pos = 0;

  for (let i = 0; i < fragments.length; i++) {
    const fragment = fragments[i].toLowerCase();
    if (fragment === "") continue;

    const foundAt = nameLower.indexOf(fragment, pos);
    if (foundAt === -1) return false;
    pos = foundAt + fragment.length;

    // 第一个非空片段必须在开头（除非 pattern 以 % 开头）
    if (i === 0 && !sqlLikePattern.startsWith("%")) {
      if (foundAt !== 0) return false;
    }
  }

  // 最后一个非空片段必须在结尾（除非 pattern 以 % 结尾）
  if (!sqlLikePattern.endsWith("%")) {
    const lastNonEmpty = fragments.filter((f) => f !== "").pop();
    if (lastNonEmpty) {
      if (!nameLower.endsWith(lastNonEmpty.toLowerCase())) return false;
    }
  }

  return true;
}

/**
 * 根据 API 返回的 sources 数组分类文件。
 * 优先 filePattern 匹配 → 扩展名+文件名启发式 → 归为 artifact。
 */
function classifyByApiSources(
  files: FileEntry[],
  profileSources: CollectionProfileSourceResponse[],
): ClassificationResult {
  // 构建 sourceType → 典型扩展名的映射
  const sourceTypeExtensions: Record<string, string[]> = {
    video: [".mp4", ".avi", ".mov", ".mkv"],
    imu: [".csv"],
    imu_csv: [".csv"],
    imu_jsonl: [".jsonl"],
    pose_csv: [".csv"],
    zed_svo2: [".svo2", ".svo"],
    zed_mcap: [".mcap"],
    jpg_sequence: [".jpg", ".jpeg", ".png"],
    motion_bvh: [".bvh"],
    motion_fbx: [".fbx"],
    audio: [".wav", ".mp3", ".flac"],
    timestamps_csv: [".csv"],
    file: [".hdf5", ".h5", ".svo2", ".svo", ".csv", ".parquet", ".npz", ".json", ".jsonl"],
  };

  const sourceMap: Record<string, FileEntry[]> = {};
  for (const s of profileSources) {
    sourceMap[s.sourceKey] = [];
  }
  const artifactList: ArtifactEntry[] = [];
  const unmatched: FileEntry[] = [];

  for (const file of files) {
    let matchedSourceKey: string | null = null;

    // 第一优先：API 的 filePattern（SQL LIKE 语义）
    for (const source of profileSources) {
      const fp = source.filePattern;
      if (fp && matchFilePattern(file.name, fp)) {
        matchedSourceKey = source.sourceKey;
        break;
      }
    }

    // 第二优先：根据 sourceType 的典型扩展名 + 文件名关键词
    if (!matchedSourceKey) {
      const nameLower = file.name.toLowerCase();
      const ext = nameLower.includes(".") ? nameLower.substring(nameLower.lastIndexOf(".")) : "";

      for (const source of profileSources) {
        const st = source.sourceType || "file";
        const exts = sourceTypeExtensions[st] || sourceTypeExtensions["file"] || [];

        // 扩展名匹配 + sourceKey 关键词校验
        if (ext && exts.includes(ext)) {
          if (nameLower.includes(source.sourceKey.toLowerCase())) {
            matchedSourceKey = source.sourceKey;
            break;
          }
        }
      }

      // 仅扩展名匹配（不要求关键词），且只有一个 source 匹配此扩展名时才自动分配
      if (!matchedSourceKey && ext) {
        const matchCandidates = profileSources.filter((s) => {
          const st = s.sourceType || "file";
          const exts = sourceTypeExtensions[st] || sourceTypeExtensions["file"] || [];
          return exts.includes(ext);
        });
        if (matchCandidates.length === 1) {
          matchedSourceKey = matchCandidates[0].sourceKey;
        }
      }
    }

    if (matchedSourceKey) {
      sourceMap[matchedSourceKey].push(file);
    } else {
      // 无法匹配任何 source → 归为 artifact
      const ext = file.name.toLowerCase().includes(".")
        ? file.name.toLowerCase().substring(file.name.lastIndexOf("."))
        : "";
      const group = ext.replace(".", "") || "other";
      artifactList.push({
        path: `artifacts/${group}/${file.name}`,
        group,
        kind: "OTHER",
      });
    }
  }

  return { sourceMap, artifactList, unmatched };
}

/**
 * 判断 profile 是否在注册表中
 */
export function isProfileKnown(profileCode: string): boolean {
  return profileCode in PROFILE_REGISTRY;
}

/**
 * 生成 manifest JSON 对象
 */
export function buildManifest(params: {
  sessionId: string;
  profileCode: string;
  subjectCode: string;
  actionName: string;
  clientId: string;
  startedAt: string;
  sources: Record<string, ManifestSource>;
  artifacts: ManifestArtifact[];
}): ManifestData {
  return {
    schemaVersion: "session-directory-v1",
    clientId: params.clientId,
    localRefs: {
      localTaskId: `MANUAL-${params.sessionId}`,
      localSessionId: params.sessionId,
    },
    task: {
      name: `${params.profileCode} 采集导入`,
      profileCode: params.profileCode,
    },
    subject: {
      code: params.subjectCode,
      name: params.subjectCode,
    },
    action: {
      code: params.actionName.toLowerCase().replace(/\s+/g, "_"),
      name: params.actionName,
    },
    session: {
      startedAt: params.startedAt,
      timestampPolicy: "device",
    },
    sources: params.sources,
    artifacts: params.artifacts,
  };
}

/**
 * 将 sourceMap + artifactList 转为 manifest 所需的 sources/artifacts 格式
 */
export function toManifestEntries(
  sourceMap: Record<string, FileEntry[]>,
  artifactList: ArtifactEntry[],
  profileSources: CollectionProfileSourceResponse[],
): {
  sources: Record<string, ManifestSource>;
  artifacts: ManifestArtifact[];
} {
  // 构建 sourceType 速查表
  const typeMap: Record<string, string> = {};
  for (const ps of profileSources) {
    typeMap[ps.sourceKey] = ps.sourceType || "other";
  }

  const sources: Record<string, ManifestSource> = {};
  for (const [sourceKey, files] of Object.entries(sourceMap)) {
    if (files.length === 0) continue;
    const sourceType = typeMap[sourceKey] || "other";

    if (files.length === 1) {
      sources[sourceKey] = {
        type: sourceType,
        path: `sources/${sourceKey}/${files[0].name}`,
      };
    } else {
      sources[sourceKey] = {
        type: sourceType,
        path: `sources/${sourceKey}/`,
      };
    }
  }

  const artifacts: ManifestArtifact[] = artifactList.map((a) => ({
    path: a.path,
    kind: a.kind,
  }));

  return { sources, artifacts };
}

/**
 * 格式化字节数为人类可读字符串
 */
export function humanSize(bytes: number): string {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB"];
  let i = 0;
  let size = bytes;
  while (size >= 1024 && i < units.length - 1) {
    size /= 1024;
    i++;
  }
  return `${size.toFixed(1)} ${units[i]}`;
}
