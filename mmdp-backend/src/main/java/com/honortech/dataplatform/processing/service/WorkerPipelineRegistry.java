package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.pipeline.dto.WorkerPipelineInfo;
import com.honortech.dataplatform.processing.util.PipelineIdNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Worker Pipeline 内存注册表，按 workerType（CPU/GPU）分区存储。
 * Worker 启动时主动向 Backend 注册其 Pipeline 清单，
 * Backend 缓存于此，供校验和查询使用。
 *
 * 向后兼容：旧方法（无 workerType 参数）搜索所有分区，
 * 新方法（带 workerType 参数）仅搜索指定分区。
 */
@Service
public class WorkerPipelineRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerPipelineRegistry.class);

    /** 外层 key = workerType (CPU/GPU)，内层 key = normalized pipelineId */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, WorkerPipelineInfo>> registry
            = new ConcurrentHashMap<>();

    /** workerType → 最后一次收到 Worker 注册的时间 */
    private final ConcurrentHashMap<String, LocalDateTime> lastRegisteredMap
            = new ConcurrentHashMap<>();

    // ── 分区写入 ──

    /**
     * Worker 启动/心跳时替换指定 workerType 分区。
     * 所有 pipeline_id 在存入前规范化。
     */
    public void replaceAll(String workerType, List<WorkerPipelineInfo> pipelines) {
        String wt = normalizeWorkerType(workerType);
        ConcurrentHashMap<String, WorkerPipelineInfo> partition =
                registry.computeIfAbsent(wt, k -> new ConcurrentHashMap<>());
        partition.clear();
        for (WorkerPipelineInfo info : pipelines) {
            String key = PipelineIdNormalizer.normalize(info.pipelineId());
            partition.put(key, info);
        }
        lastRegisteredMap.put(wt, LocalDateTime.now());
        log.info("[Worker注册] {} Worker 注册表已更新: {} 个 Pipeline, 时间={}",
                wt, pipelines.size(), lastRegisteredMap.get(wt));
    }

    /**
     * 向后兼容：接收无 workerType 的 Pipeline 清单时，推断类型（默认 CPU）。
     */
    public void replaceAll(List<WorkerPipelineInfo> pipelines) {
        if (pipelines == null || pipelines.isEmpty()) {
            return;
        }
        // 从第一条记录的 workerType 推断，默认为 CPU
        String workerType = pipelines.get(0).workerType() != null
                ? normalizeWorkerType(pipelines.get(0).workerType()) : "CPU";
        replaceAll(workerType, pipelines);
    }

    // ── 分区查询 ──

    /** 指定分区是否已注册某 pipeline */
    public boolean isRegistered(String workerType, String pipelineId) {
        ConcurrentHashMap<String, WorkerPipelineInfo> partition = registry.get(normalizeWorkerType(workerType));
        return partition != null && partition.containsKey(PipelineIdNormalizer.normalize(pipelineId));
    }

    /** 向后兼容：搜索所有分区，任意分区匹配即返回 true */
    public boolean isRegistered(String pipelineId) {
        String key = PipelineIdNormalizer.normalize(pipelineId);
        for (ConcurrentHashMap<String, WorkerPipelineInfo> partition : registry.values()) {
            if (partition.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** 获取指定分区已注册的 pipeline ID 集合 */
    public Set<String> getRegisteredIds(String workerType) {
        ConcurrentHashMap<String, WorkerPipelineInfo> partition = registry.get(normalizeWorkerType(workerType));
        return partition != null ? Collections.unmodifiableSet(partition.keySet()) : Collections.emptySet();
    }

    /** 向后兼容：返回所有分区 ID 合并集合 */
    public Set<String> getRegisteredIds() {
        return registry.values().stream()
                .flatMap(p -> p.keySet().stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** 获取注册表完整快照（所有分区合并） */
    public List<WorkerPipelineInfo> getAll() {
        List<WorkerPipelineInfo> all = new ArrayList<>();
        for (ConcurrentHashMap<String, WorkerPipelineInfo> partition : registry.values()) {
            all.addAll(partition.values());
        }
        return all;
    }

    // ── 在线状态 ──

    /** 指定 workerType 是否在线：最近一次注册距今不超过 120 秒 */
    public boolean isWorkerOnline(String workerType) {
        LocalDateTime last = lastRegisteredMap.get(normalizeWorkerType(workerType));
        if (last == null) {
            return false;
        }
        return Duration.between(last, LocalDateTime.now()).getSeconds() <= 120;
    }

    /** 向后兼容：任意 workerType 在线即返回 true */
    public boolean isWorkerOnline() {
        LocalDateTime now = LocalDateTime.now();
        for (LocalDateTime last : lastRegisteredMap.values()) {
            if (Duration.between(last, now).getSeconds() <= 120) {
                return true;
            }
        }
        return false;
    }

    /** 指定 workerType 的最后注册时间 */
    public LocalDateTime getLastRegisteredAt(String workerType) {
        return lastRegisteredMap.get(normalizeWorkerType(workerType));
    }

    /** 向后兼容：返回所有分区中最新的注册时间 */
    public LocalDateTime getLastRegisteredAt() {
        return lastRegisteredMap.values().stream()
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    // ── 全局状态 ──

    /** 所有分区是否均为空 */
    public boolean isEmpty() {
        return registry.values().stream().allMatch(ConcurrentHashMap::isEmpty);
    }

    // ── 内部工具 ──

    private static String normalizeWorkerType(String workerType) {
        if (workerType == null || workerType.isBlank()) {
            return "CPU";
        }
        return workerType.strip().toUpperCase();
    }
}
