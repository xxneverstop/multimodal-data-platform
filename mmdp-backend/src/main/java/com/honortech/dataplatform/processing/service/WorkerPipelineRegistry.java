package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.pipeline.dto.WorkerPipelineInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Worker Pipeline 内存注册表。
 * Worker 启动时主动向 Backend 注册其 Pipeline 清单，
 * Backend 缓存于此，供校验和查询使用，替代文件读取方式。
 */
@Service
public class WorkerPipelineRegistry {

    private static final Logger log = LoggerFactory.getLogger(WorkerPipelineRegistry.class);

    /** pipelineId → WorkerPipelineInfo */
    private final ConcurrentHashMap<String, WorkerPipelineInfo> registry = new ConcurrentHashMap<>();

    /** 最后一次收到 Worker 注册的时间 */
    private volatile LocalDateTime lastRegisteredAt;

    /**
     * Worker 启动/心跳时全量替换注册表。
     */
    public void replaceAll(List<WorkerPipelineInfo> pipelines) {
        registry.clear();
        for (WorkerPipelineInfo info : pipelines) {
            registry.put(info.pipelineId(), info);
        }
        lastRegisteredAt = LocalDateTime.now();
        log.info("[Worker注册] 注册表已更新: {} 个 Pipeline, 时间={}",
                pipelines.size(), lastRegisteredAt);
    }

    /**
     * 查询某个 pipeline 是否已被 Worker 注册。
     */
    public boolean isRegistered(String pipelineId) {
        return registry.containsKey(pipelineId);
    }

    /**
     * 获取所有已注册的 pipeline ID 集合。
     */
    public Set<String> getRegisteredIds() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    /**
     * 获取注册表完整快照。
     */
    public List<WorkerPipelineInfo> getAll() {
        return new ArrayList<>(registry.values());
    }

    /**
     * 最后一次 Worker 注册时间，用于判断 Worker 是否在线。
     * 返回 null 表示从未注册。
     */
    public LocalDateTime getLastRegisteredAt() {
        return lastRegisteredAt;
    }

    /**
     * 注册表是否为空（Worker 从未注册或已失联）。
     */
    public boolean isEmpty() {
        return registry.isEmpty();
    }
}
