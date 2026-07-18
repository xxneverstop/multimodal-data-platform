package com.honortech.dataplatform.processing.service;

import com.honortech.dataplatform.pipeline.dto.WorkerPipelineInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Worker Pipeline 清单查询服务。
 * 从 WorkerPipelineRegistry 内存注册表读取（Worker 启动时主动注册），
 * 不再依赖 pipeline-manifest.json 文件读取。
 */
@Service
public class WorkerManifestService {

    private static final Logger log = LoggerFactory.getLogger(WorkerManifestService.class);

    private final WorkerPipelineRegistry registry;

    public WorkerManifestService(WorkerPipelineRegistry registry) {
        this.registry = registry;
        log.info("WorkerManifestService 已初始化（从内存注册表读取）");
    }

    /**
     * 获取 Worker 端已注册的 Pipeline 清单。
     * 注册表为空时返回空列表（Worker 可能尚未启动或已失联）。
     */
    public List<WorkerPipelineInfo> getAvailablePipelines() {
        List<WorkerPipelineInfo> pipelines = registry.getAll();
        if (pipelines.isEmpty()) {
            log.warn("Worker Pipeline 注册表为空，Worker 可能尚未启动");
        }
        return pipelines;
    }
}
