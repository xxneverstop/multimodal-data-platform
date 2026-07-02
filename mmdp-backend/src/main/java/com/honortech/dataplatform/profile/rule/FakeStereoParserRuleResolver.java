package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Fake Stereo+IMU Session 解析规则。
 * 根据 ZIP 中的文件相对路径，匹配对应的 profile sourceKey。
 */
@Component
public class FakeStereoParserRuleResolver implements ParserRuleResolver {

    @Override
    public boolean supports(String ruleCode) {
        return "FAKE_STEREO_SESSION_V2".equalsIgnoreCase(ruleCode);
    }

    @Override
    public String resolveSourceKey(
            CollectionProfile profile,
            List<CollectionProfileSource> sources,
            String relativePath) {

        String normalized = relativePath.toLowerCase(Locale.ROOT);

        for (CollectionProfileSource source : sources) {
            String sourceKey = source.getSourceKey().toLowerCase(Locale.ROOT);
            if (normalized.contains(sourceKey)) {
                return source.getSourceKey();
            }
            String pattern = source.getFilePattern();
            if (pattern != null && !pattern.isBlank()) {
                String hint = pattern.replace("%", "").toLowerCase(Locale.ROOT);
                if (!hint.isBlank() && normalized.contains(hint)) {
                    return source.getSourceKey();
                }
            }
        }
        return null;
    }
}
