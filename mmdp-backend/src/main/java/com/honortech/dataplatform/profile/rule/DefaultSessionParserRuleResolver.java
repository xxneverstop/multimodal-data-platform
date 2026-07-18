package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
@Order(1)
public class DefaultSessionParserRuleResolver implements ParserRuleResolver {

    @Override
    public boolean supports(String ruleCode) {
        // 通用实现：按 sourceKey 名称或 filePattern 匹配文件路径，适用于所有标准 Session 目录导入
        return true;
    }

    @Override
    public String resolveSourceKey(CollectionProfile profile, List<CollectionProfileSource> sources, String relativePath) {
        String normalized = relativePath.toLowerCase(Locale.ROOT);
        for (CollectionProfileSource source : sources) {
            String sourceKey = source.getSourceKey().toLowerCase(Locale.ROOT);
            if (normalized.contains(sourceKey)) {
                return source.getSourceKey();
            }
            String pattern = source.getFilePattern();
            if (pattern != null && !pattern.isBlank()) {
                String patternHint = pattern.replace("%", "").toLowerCase(Locale.ROOT);
                if (!patternHint.isBlank() && normalized.contains(patternHint)) {
                    return source.getSourceKey();
                }
            }
        }
        return null;
    }
}
