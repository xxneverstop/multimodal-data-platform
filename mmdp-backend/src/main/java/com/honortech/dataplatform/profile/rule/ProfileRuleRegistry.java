package com.honortech.dataplatform.profile.rule;

import com.honortech.dataplatform.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfileRuleRegistry {

    private final List<PackageRuleResolver> packageRuleResolvers;
    private final List<ParserRuleResolver> parserRuleResolvers;
    private final List<ArchiveRuleResolver> archiveRuleResolvers;
    private final List<PlaybackRuleResolver> playbackRuleResolvers;

    public ProfileRuleRegistry(
            List<PackageRuleResolver> packageRuleResolvers,
            List<ParserRuleResolver> parserRuleResolvers,
            List<ArchiveRuleResolver> archiveRuleResolvers,
            List<PlaybackRuleResolver> playbackRuleResolvers) {
        this.packageRuleResolvers = packageRuleResolvers;
        this.parserRuleResolvers = parserRuleResolvers;
        this.archiveRuleResolvers = archiveRuleResolvers;
        this.playbackRuleResolvers = playbackRuleResolvers;
    }

    public PackageRuleResolver getPackageRule(String ruleCode) {
        return packageRuleResolvers.stream()
                .filter(item -> item.supports(ruleCode))
                .findFirst()
                .orElseThrow(() -> new BizException("Unsupported package rule: " + ruleCode));
    }

    public ParserRuleResolver getParserRule(String ruleCode) {
        return parserRuleResolvers.stream()
                .filter(item -> item.supports(ruleCode))
                .findFirst()
                .orElseThrow(() -> new BizException("Unsupported parser rule: " + ruleCode));
    }

    public ArchiveRuleResolver getArchiveRule(String ruleCode) {
        return archiveRuleResolvers.stream()
                .filter(item -> item.supports(ruleCode))
                .findFirst()
                .orElseThrow(() -> new BizException("Unsupported archive rule: " + ruleCode));
    }

    public PlaybackRuleResolver getPlaybackRule(String ruleCode) {
        return playbackRuleResolvers.stream()
                .filter(item -> item.supports(ruleCode))
                .findFirst()
                .orElseThrow(() -> new BizException("Unsupported playback rule: " + ruleCode));
    }
}
