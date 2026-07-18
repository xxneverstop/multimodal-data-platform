package com.honortech.dataplatform.annotation.service;

import com.honortech.dataplatform.annotation.dto.AnnotationProgressResponse;
import com.honortech.dataplatform.annotation.dto.MotionAnnotationRequest;
import com.honortech.dataplatform.annotation.dto.MotionAnnotationResponse;

/**
 * 动作标注服务接口
 */
public interface MotionAnnotationService {

    /**
     * 获取标注信息，若不存在则懒初始化
     */
    MotionAnnotationResponse getByAssetId(Long assetId);

    /**
     * 通过 fileId 查找标注，用于 MotionViewer 中通过文件 ID 定位标注
     */
    MotionAnnotationResponse getByFileId(Long fileId);

    /**
     * 创建或更新标注，含乐观锁版本校验
     */
    MotionAnnotationResponse upsert(Long assetId, MotionAnnotationRequest request, Long annotatorId);

    /**
     * Session 下所有 Asset 的标注进度聚合
     */
    AnnotationProgressResponse getSessionProgress(Long sessionId);

    /**
     * Task 下所有 Asset 的标注进度聚合
     */
    AnnotationProgressResponse getTaskProgress(Long taskId);
}
