package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.sessionimport.dto.NormalizedSessionManifest;

import java.util.List;

public interface PackageRuleResolver {

    boolean supports(String ruleCode);

    void validate(CollectionProfile profile, NormalizedSessionManifest manifest, List<String> relativePaths);
}
