package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;

import java.util.List;

public interface ParserRuleResolver {

    boolean supports(String ruleCode);

    String resolveSourceKey(CollectionProfile profile, List<CollectionProfileSource> sources, String relativePath);
}
