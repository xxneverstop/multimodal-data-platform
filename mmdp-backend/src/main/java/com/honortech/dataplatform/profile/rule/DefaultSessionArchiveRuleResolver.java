package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.profile.entity.CollectionProfile;
import org.springframework.stereotype.Component;

@Component
public class DefaultSessionArchiveRuleResolver implements ArchiveRuleResolver {

    @Override
    public boolean supports(String ruleCode) {
        return "SESSION_ARCHIVE_V1".equalsIgnoreCase(ruleCode);
    }

    @Override
    public String fileRoleForArchive(CollectionProfile profile) {
        return "SESSION_ARCHIVE";
    }

    @Override
    public String fileRoleForExtracted(CollectionProfile profile, String relativePath) {
        return "EXTRACTED_STRUCTURED";
    }
}
