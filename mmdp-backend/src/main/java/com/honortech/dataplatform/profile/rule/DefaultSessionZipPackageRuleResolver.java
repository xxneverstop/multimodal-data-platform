package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.sessionimport.dto.NormalizedSessionManifest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(1)
public class DefaultSessionZipPackageRuleResolver implements PackageRuleResolver {

    @Override
    public boolean supports(String ruleCode) {
        // 通用实现：所有标准 Session 目录导入都适用此校验逻辑
        return true;
    }

    @Override
    public void validate(CollectionProfile profile, NormalizedSessionManifest manifest, List<String> relativePaths) {
        if (relativePaths.isEmpty()) {
            throw new BizException("Profile package rule expects at least one extracted file");
        }
        if (manifest.sources() == null || !manifest.sources().fieldNames().hasNext()) {
            throw new BizException("Profile package rule requires manifest sources");
        }
    }
}
