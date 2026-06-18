package com.honortech.dataplatform.profile.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.profile.dto.CollectionProfileResponse;
import com.honortech.dataplatform.profile.dto.CollectionProfileSourceResponse;
import com.honortech.dataplatform.profile.dto.CreateProfileRequest;
import com.honortech.dataplatform.profile.dto.CreateProfileSourceRequest;
import com.honortech.dataplatform.profile.dto.UpdateProfileRequest;
import com.honortech.dataplatform.profile.dto.UpdateProfileSourceRequest;
import com.honortech.dataplatform.profile.service.CollectionProfileService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/collection-profiles")
@Validated
public class CollectionProfileController {

    private final CollectionProfileService collectionProfileService;

    public CollectionProfileController(CollectionProfileService collectionProfileService) {
        this.collectionProfileService = collectionProfileService;
    }

    @GetMapping
    public ApiResponse<List<CollectionProfileResponse>> listProfiles(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        if (includeDisabled) {
            return ApiResponse.success(collectionProfileService.listAllProfileResponses());
        }
        return ApiResponse.success(collectionProfileService.listProfileResponses());
    }

    @GetMapping("/{profileId}")
    public ApiResponse<CollectionProfileResponse> getProfile(@PathVariable Long profileId) {
        return ApiResponse.success(collectionProfileService.getProfileResponse(profileId));
    }

    @PostMapping
    public ApiResponse<CollectionProfileResponse> createProfile(
            @Valid @RequestBody CreateProfileRequest request) {
        return ApiResponse.success("Profile创建成功",
                collectionProfileService.toResponse(collectionProfileService.createProfile(request)));
    }

    @PutMapping("/{profileId}")
    public ApiResponse<CollectionProfileResponse> updateProfile(
            @PathVariable Long profileId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success("Profile更新成功",
                collectionProfileService.toResponse(collectionProfileService.updateProfile(profileId, request)));
    }

    @DeleteMapping("/{profileId}")
    public ApiResponse<Void> disableProfile(@PathVariable Long profileId) {
        collectionProfileService.disableProfile(profileId);
        return ApiResponse.success("Profile已禁用", null);
    }

    @PostMapping("/{profileId}/sources")
    public ApiResponse<CollectionProfileSourceResponse> addSource(
            @PathVariable Long profileId,
            @Valid @RequestBody CreateProfileSourceRequest request) {
        return ApiResponse.success("Source添加成功",
                collectionProfileService.toSourceResponse(
                        collectionProfileService.addSource(profileId, request)));
    }

    @PutMapping("/{profileId}/sources/{sourceId}")
    public ApiResponse<CollectionProfileSourceResponse> updateSource(
            @PathVariable Long profileId,
            @PathVariable Long sourceId,
            @Valid @RequestBody UpdateProfileSourceRequest request) {
        return ApiResponse.success("Source更新成功",
                collectionProfileService.toSourceResponse(
                        collectionProfileService.updateSource(profileId, sourceId, request)));
    }

    @DeleteMapping("/{profileId}/sources/{sourceId}")
    public ApiResponse<Void> disableSource(
            @PathVariable Long profileId,
            @PathVariable Long sourceId) {
        collectionProfileService.disableSource(profileId, sourceId);
        return ApiResponse.success("Source已禁用", null);
    }
}
