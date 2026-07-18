package com.honortech.dataplatform.annotation.controller;

import com.honortech.dataplatform.annotation.dto.AnnotationProgressResponse;
import com.honortech.dataplatform.annotation.dto.MotionAnnotationRequest;
import com.honortech.dataplatform.annotation.dto.MotionAnnotationResponse;
import com.honortech.dataplatform.annotation.service.MotionAnnotationService;
import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.common.auth.AuthSessionUser;
import com.honortech.dataplatform.common.auth.AuthSessionUtils;
import jakarta.servlet.http.HttpSession;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动作标注控制器
 */
@Validated
@RestController
@RequestMapping("/api/annotation")
public class MotionAnnotationController {

    private final MotionAnnotationService motionAnnotationService;

    public MotionAnnotationController(MotionAnnotationService motionAnnotationService) {
        this.motionAnnotationService = motionAnnotationService;
    }

    /**
     * 获取指定 Asset 的标注信息（不存在则懒初始化）
     */
    @GetMapping("/assets/{assetId}")
    public ApiResponse<MotionAnnotationResponse> getAnnotation(@PathVariable Long assetId) {
        return ApiResponse.success(motionAnnotationService.getByAssetId(assetId));
    }

    /**
     * 通过 fileId 查找标注（MotionViewer 使用）
     */
    @GetMapping("/assets/by-file/{fileId}")
    public ApiResponse<MotionAnnotationResponse> getAnnotationByFileId(@PathVariable Long fileId) {
        return ApiResponse.success(motionAnnotationService.getByFileId(fileId));
    }

    /**
     * 保存/更新标注
     */
    @PutMapping("/assets/{assetId}")
    public ApiResponse<MotionAnnotationResponse> upsertAnnotation(
            @PathVariable Long assetId,
            @Validated @RequestBody MotionAnnotationRequest request,
            HttpSession session) {
        AuthSessionUser user = AuthSessionUtils.getRequiredUser(session);
        return ApiResponse.success(motionAnnotationService.upsert(assetId, request, user.userId()));
    }

    /**
     * Session 标注进度统计
     */
    @GetMapping("/sessions/{sessionId}/progress")
    public ApiResponse<AnnotationProgressResponse> getSessionProgress(@PathVariable Long sessionId) {
        return ApiResponse.success(motionAnnotationService.getSessionProgress(sessionId));
    }

    /**
     * Task 标注进度统计
     */
    @GetMapping("/tasks/{taskId}/progress")
    public ApiResponse<AnnotationProgressResponse> getTaskProgress(@PathVariable Long taskId) {
        return ApiResponse.success(motionAnnotationService.getTaskProgress(taskId));
    }
}
