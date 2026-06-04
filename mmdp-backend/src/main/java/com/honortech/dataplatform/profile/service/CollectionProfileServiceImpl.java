package com.honortech.dataplatform.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.profile.dto.CollectionProfileResponse;
import com.honortech.dataplatform.profile.dto.CollectionProfileSourceResponse;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import com.honortech.dataplatform.profile.mapper.CollectionProfileMapper;
import com.honortech.dataplatform.profile.mapper.CollectionProfileSourceMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CollectionProfileServiceImpl implements CollectionProfileService {

    public static final String DEFAULT_PROFILE_CODE = "BINOCULAR_HMD_IMU_V1";

    private final CollectionProfileMapper profileMapper;
    private final CollectionProfileSourceMapper profileSourceMapper;

    public CollectionProfileServiceImpl(
            CollectionProfileMapper profileMapper,
            CollectionProfileSourceMapper profileSourceMapper) {
        this.profileMapper = profileMapper;
        this.profileSourceMapper = profileSourceMapper;
    }

    @PostConstruct
    public void initializeDefaults() {
        ensureDefaultProfiles();
    }

    @Override
    public CollectionProfile getRequiredById(Long profileId) {
        CollectionProfile profile = profileMapper.selectById(profileId);
        if (profile == null) {
            throw new BizException("Profile not found: " + profileId);
        }
        return profile;
    }

    @Override
    public CollectionProfile getRequiredByCode(String profileCode) {
        CollectionProfile profile = profileMapper.selectOne(new LambdaQueryWrapper<CollectionProfile>()
                .eq(CollectionProfile::getProfileCode, profileCode));
        if (profile == null) {
            throw new BizException("Profile not found: " + profileCode);
        }
        return profile;
    }

    @Override
    public List<CollectionProfile> listEnabledProfiles() {
        return profileMapper.selectList(new LambdaQueryWrapper<CollectionProfile>()
                .eq(CollectionProfile::getEnabled, 1)
                .orderByAsc(CollectionProfile::getId));
    }

    @Override
    public List<CollectionProfileSource> listSourcesByProfileId(Long profileId) {
        return profileSourceMapper.selectList(new LambdaQueryWrapper<CollectionProfileSource>()
                .eq(CollectionProfileSource::getProfileId, profileId)
                .eq(CollectionProfileSource::getEnabled, 1)
                .orderByAsc(CollectionProfileSource::getSortOrder, CollectionProfileSource::getId));
    }

    @Override
    public List<CollectionProfileResponse> listProfileResponses() {
        return listEnabledProfiles().stream().map(this::toResponse).toList();
    }

    @Override
    public CollectionProfileResponse getProfileResponse(Long profileId) {
        return toResponse(getRequiredById(profileId));
    }

    @Override
    public void ensureDefaultProfiles() {
        CollectionProfile existing = profileMapper.selectOne(new LambdaQueryWrapper<CollectionProfile>()
                .eq(CollectionProfile::getProfileCode, DEFAULT_PROFILE_CODE));
        if (existing != null) {
            ensureDefaultSources(existing.getId());
            return;
        }
        CollectionProfile profile = new CollectionProfile();
        profile.setProfileCode(DEFAULT_PROFILE_CODE);
        profile.setProfileName("双目+HMD+IMU 默认采集规则");
        profile.setTaskTypeCode("HUMAN_DEMO");
        profile.setModalityGroupCode("RGB_RGB_EGO_IMU");
        profile.setDeviceGroupCode("BINOCULAR_HMD_IMU");
        profile.setPackageRuleCode("SESSION_ZIP_V1");
        profile.setParserRuleCode("SESSION_JSONL_VIDEO_IMU_V1");
        profile.setArchiveRuleCode("SESSION_ARCHIVE_V1");
        profile.setPlaybackRuleCode("MULTI_VIDEO_IMU_V1");
        profile.setVersion("v1");
        profile.setEnabled(1);
        profile.setRemark("当前测试链路默认Profile");
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.insert(profile);
        ensureDefaultSources(profile.getId());
    }

    private void ensureDefaultSources(Long profileId) {
        ensureSource(profileId, "left", "Left Camera", "video", "CAM_LEFT", true, "%left%", "RGB_VIDEO_MP4", "video", 30.0, null, 1);
        ensureSource(profileId, "right", "Right Camera", "video", "CAM_RIGHT", true, "%right%", "RGB_VIDEO_MP4", "video", 30.0, null, 2);
        ensureSource(profileId, "hmd", "HMD View", "video", "HMD", true, "%hmd%", "RGB_VIDEO_MP4", "video", 30.0, null, 3);
        ensureSource(profileId, "imu", "IMU", "imu", "IMU", true, "%imu%", "MOCAP_CSV", "imu_curve", null, 100.0, 4);
    }

    private void ensureSource(
            Long profileId,
            String sourceKey,
            String sourceName,
            String sourceType,
            String deviceRoleCode,
            boolean requiredFlag,
            String filePattern,
            String parsedAssetType,
            String playbackKind,
            Double expectedFps,
            Double expectedSampleRate,
            int sortOrder) {
        CollectionProfileSource existing = profileSourceMapper.selectOne(new LambdaQueryWrapper<CollectionProfileSource>()
                .eq(CollectionProfileSource::getProfileId, profileId)
                .eq(CollectionProfileSource::getSourceKey, sourceKey));
        if (existing != null) {
            return;
        }
        CollectionProfileSource source = new CollectionProfileSource();
        source.setProfileId(profileId);
        source.setSourceKey(sourceKey);
        source.setSourceName(sourceName);
        source.setSourceType(sourceType);
        source.setDeviceRoleCode(deviceRoleCode);
        source.setRequiredFlag(requiredFlag ? 1 : 0);
        source.setFilePattern(filePattern);
        source.setParsedAssetType(parsedAssetType);
        source.setPlaybackKind(playbackKind);
        source.setExpectedFps(expectedFps);
        source.setExpectedSampleRate(expectedSampleRate);
        source.setSortOrder(sortOrder);
        source.setEnabled(1);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        profileSourceMapper.insert(source);
    }

    private CollectionProfileResponse toResponse(CollectionProfile profile) {
        List<CollectionProfileSourceResponse> sources = listSourcesByProfileId(profile.getId()).stream()
                .map(source -> new CollectionProfileSourceResponse(
                        source.getId(),
                        source.getSourceKey(),
                        source.getSourceName(),
                        source.getSourceType(),
                        source.getDeviceRoleCode(),
                        source.getRequiredFlag() != null && source.getRequiredFlag() == 1,
                        source.getFilePattern(),
                        source.getParsedAssetType(),
                        source.getPlaybackKind(),
                        source.getExpectedFps(),
                        source.getExpectedSampleRate(),
                        source.getSortOrder()
                ))
                .toList();
        return new CollectionProfileResponse(
                profile.getId(),
                profile.getProfileCode(),
                profile.getProfileName(),
                profile.getTaskTypeCode(),
                profile.getModalityGroupCode(),
                profile.getDeviceGroupCode(),
                profile.getPackageRuleCode(),
                profile.getParserRuleCode(),
                profile.getArchiveRuleCode(),
                profile.getPlaybackRuleCode(),
                profile.getVersion(),
                sources
        );
    }
}
