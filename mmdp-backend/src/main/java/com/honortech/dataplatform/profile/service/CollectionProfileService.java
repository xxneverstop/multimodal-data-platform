package com.honortech.dataplatform.profile.service;

import com.honortech.dataplatform.profile.dto.CollectionProfileResponse;
import com.honortech.dataplatform.profile.dto.CollectionProfileSourceResponse;
import com.honortech.dataplatform.profile.dto.CreateProfileRequest;
import com.honortech.dataplatform.profile.dto.CreateProfileSourceRequest;
import com.honortech.dataplatform.profile.dto.UpdateProfileRequest;
import com.honortech.dataplatform.profile.dto.UpdateProfileSourceRequest;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;

import java.util.List;

public interface CollectionProfileService {

    CollectionProfile getRequiredById(Long profileId);

    CollectionProfile getRequiredByCode(String profileCode);

    List<CollectionProfile> listEnabledProfiles();

    List<CollectionProfileSource> listSourcesByProfileId(Long profileId);

    List<CollectionProfileResponse> listProfileResponses();

    List<CollectionProfileResponse> listAllProfileResponses();

    CollectionProfileResponse getProfileResponse(Long profileId);

    void ensureDefaultProfiles();

    CollectionProfile createProfile(CreateProfileRequest request);

    CollectionProfile updateProfile(Long profileId, UpdateProfileRequest request);

    void disableProfile(Long profileId);

    CollectionProfileSource addSource(Long profileId, CreateProfileSourceRequest request);

    CollectionProfileSource updateSource(Long profileId, Long sourceId, UpdateProfileSourceRequest request);

    void disableSource(Long profileId, Long sourceId);

    CollectionProfileSourceResponse toSourceResponse(CollectionProfileSource source);

    CollectionProfileResponse toResponse(CollectionProfile profile);
}
