package com.honortech.dataplatform.profile.service;

import com.honortech.dataplatform.profile.dto.CollectionProfileResponse;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;

import java.util.List;

public interface CollectionProfileService {

    CollectionProfile getRequiredById(Long profileId);

    CollectionProfile getRequiredByCode(String profileCode);

    List<CollectionProfile> listEnabledProfiles();

    List<CollectionProfileSource> listSourcesByProfileId(Long profileId);

    List<CollectionProfileResponse> listProfileResponses();

    CollectionProfileResponse getProfileResponse(Long profileId);

    void ensureDefaultProfiles();
}
