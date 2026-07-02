package com.honortech.dataplatform.processing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.honortech.dataplatform.pipeline.dto.WorkerPipelineInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * Worker Manifest 文件读取服务。
 * 直接读取 Python Worker 启动时生成的 pipeline-manifest.json，
 * 无需 Worker 主动向 Backend 注册，消除序列化通道的可靠性问题。
 */
@Service
public class WorkerManifestService {

    private static final Logger log = LoggerFactory.getLogger(WorkerManifestService.class);

    private final File manifestFile;

    public WorkerManifestService(
            @Value("${worker.manifest-path:../mmdp-worker/pipeline-manifest.json}")
            String manifestPath) {
        this.manifestFile = new File(manifestPath);
        log.info("Worker manifest path: {}", manifestFile.getAbsolutePath());
    }

    /**
     * 读取 pipeline-manifest.json 并解析为 Pipeline 元数据列表。
     * 文件不存在或解析失败时返回空列表，不抛异常。
     */
    public List<WorkerPipelineInfo> getAvailablePipelines() {
        if (!manifestFile.exists()) {
            log.warn("Manifest file not found: {}", manifestFile.getAbsolutePath());
            return Collections.emptyList();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            // Python 端使用 snake_case，Jackson 需要显式配置映射策略
            mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
            return mapper.readValue(manifestFile, new TypeReference<List<WorkerPipelineInfo>>() {});
        } catch (Exception e) {
            log.error("Failed to parse manifest file: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
