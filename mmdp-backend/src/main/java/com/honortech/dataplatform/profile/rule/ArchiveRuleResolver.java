package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.profile.entity.CollectionProfile;

public interface ArchiveRuleResolver {

    boolean supports(String ruleCode);

    String fileRoleForArchive(CollectionProfile profile);

    String fileRoleForExtracted(CollectionProfile profile, String relativePath);
}
