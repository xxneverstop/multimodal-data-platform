package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.profile.entity.CollectionProfile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class DefaultSessionArchiveRuleResolver implements ArchiveRuleResolver {

    @Override
    public boolean supports(String ruleCode) {
        // 通用实现：所有标准 Session 目录导入都适用
        return true;
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
