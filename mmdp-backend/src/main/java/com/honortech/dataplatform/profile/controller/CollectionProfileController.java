package com.honortech.dataplatform.profile.controller;

import com.honortech.dataplatform.common.api.ApiResponse;
import com.honortech.dataplatform.profile.dto.CollectionProfileResponse;
import com.honortech.dataplatform.profile.service.CollectionProfileService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/collection-profiles")
public class CollectionProfileController {

    private final CollectionProfileService collectionProfileService;

    public CollectionProfileController(CollectionProfileService collectionProfileService) {
        this.collectionProfileService = collectionProfileService;
    }

    @GetMapping
    public ApiResponse<List<CollectionProfileResponse>> listProfiles() {
        return ApiResponse.success(collectionProfileService.listProfileResponses());
    }

    @GetMapping("/{profileId}")
    public ApiResponse<CollectionProfileResponse> getProfile(@PathVariable Long profileId) {
        return ApiResponse.success(collectionProfileService.getProfileResponse(profileId));
    }
}
