package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class DefaultSessionParserRuleResolver implements ParserRuleResolver {

    @Override
    public boolean supports(String ruleCode) {
        return "SESSION_JSONL_VIDEO_IMU_V1".equalsIgnoreCase(ruleCode);
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
