package com.honortech.dataplatform.profile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.profile.dto.CollectionProfileResponse;
import com.honortech.dataplatform.profile.dto.CollectionProfileSourceResponse;
import com.honortech.dataplatform.profile.dto.CreateProfileRequest;
import com.honortech.dataplatform.profile.dto.CreateProfileSourceRequest;
import com.honortech.dataplatform.profile.dto.CreateSourceItem;
import com.honortech.dataplatform.profile.dto.UpdateProfileRequest;
import com.honortech.dataplatform.profile.dto.UpdateProfileSourceRequest;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import com.honortech.dataplatform.profile.mapper.CollectionProfileMapper;
import com.honortech.dataplatform.profile.mapper.CollectionProfileSourceMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

    @Override
    public CollectionProfileResponse toResponse(CollectionProfile profile) {
        List<CollectionProfileSourceResponse> sources = listSourcesByProfileId(profile.getId()).stream()
                .map(this::toSourceResponse)
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
                profile.getEnabled() != null && profile.getEnabled() == 1,
                profile.getRemark(),
                sources
        );
    }

    @Override
    public List<CollectionProfileResponse> listAllProfileResponses() {
        List<CollectionProfile> all = profileMapper.selectList(new LambdaQueryWrapper<CollectionProfile>()
                .orderByAsc(CollectionProfile::getId));
        return all.stream().map(this::toResponse).toList();
    }

    @Override
    public CollectionProfile createProfile(CreateProfileRequest request) {
        CollectionProfile profile = new CollectionProfile();
        profile.setProfileCode(request.profileCode());
        profile.setProfileName(request.profileName());
        profile.setTaskTypeCode(request.taskTypeCode());
        profile.setModalityGroupCode(defaultIfBlank(request.modalityGroupCode()));
        profile.setDeviceGroupCode(defaultIfBlank(request.deviceGroupCode()));
        profile.setPackageRuleCode(defaultIfBlank(request.packageRuleCode()));
        profile.setParserRuleCode(defaultIfBlank(request.parserRuleCode()));
        profile.setArchiveRuleCode(defaultIfBlank(request.archiveRuleCode()));
        profile.setPlaybackRuleCode(defaultIfBlank(request.playbackRuleCode()));
        profile.setVersion(defaultIfBlank(request.version(), "v1"));
        profile.setRemark(request.remark());
        profile.setEnabled(1);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.insert(profile);

        if (request.sources() != null && !request.sources().isEmpty()) {
            for (CreateSourceItem item : request.sources()) {
                createSourceFromItem(profile.getId(), item);
            }
        }
        return profile;
    }

    @Override
    public CollectionProfile updateProfile(Long profileId, UpdateProfileRequest request) {
        CollectionProfile profile = getRequiredById(profileId);
        profile.setProfileCode(request.profileCode());
        profile.setProfileName(request.profileName());
        profile.setTaskTypeCode(request.taskTypeCode());
        if (request.modalityGroupCode() != null) profile.setModalityGroupCode(defaultIfBlank(request.modalityGroupCode()));
        if (request.deviceGroupCode() != null) profile.setDeviceGroupCode(defaultIfBlank(request.deviceGroupCode()));
        if (request.packageRuleCode() != null) profile.setPackageRuleCode(defaultIfBlank(request.packageRuleCode()));
        if (request.parserRuleCode() != null) profile.setParserRuleCode(defaultIfBlank(request.parserRuleCode()));
        if (request.archiveRuleCode() != null) profile.setArchiveRuleCode(defaultIfBlank(request.archiveRuleCode()));
        if (request.playbackRuleCode() != null) profile.setPlaybackRuleCode(defaultIfBlank(request.playbackRuleCode()));
        if (request.version() != null) profile.setVersion(defaultIfBlank(request.version(), "v1"));
        profile.setRemark(request.remark());
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(profile);
        return profile;
    }

    @Override
    public void disableProfile(Long profileId) {
        CollectionProfile profile = getRequiredById(profileId);
        profile.setEnabled(0);
        profile.setUpdatedAt(LocalDateTime.now());
        profileMapper.updateById(profile);
    }

    @Override
    public CollectionProfileSource addSource(Long profileId, CreateProfileSourceRequest request) {
        getRequiredById(profileId);
        CollectionProfileSource source = buildSourceEntity(profileId, request.sourceKey(), request.sourceName(),
                request.sourceType(), request.deviceRoleCode(), request.requiredFlag(),
                request.filePattern(), request.parsedAssetType(), request.playbackKind(),
                request.expectedFps(), request.expectedSampleRate(), request.sortOrder());
        profileSourceMapper.insert(source);
        return source;
    }

    @Override
    public CollectionProfileSource updateSource(Long profileId, Long sourceId, UpdateProfileSourceRequest request) {
        CollectionProfileSource source = profileSourceMapper.selectById(sourceId);
        if (source == null || !source.getProfileId().equals(profileId)) {
            throw new BizException("Source not found: " + sourceId + " for profile: " + profileId);
        }
        if (request.sourceKey() != null) {
            source.setSourceKey(request.sourceKey());
        }
        if (request.sourceName() != null) {
            source.setSourceName(request.sourceName());
        }
        if (request.sourceType() != null) {
            source.setSourceType(request.sourceType());
        }
        if (request.deviceRoleCode() != null) {
            source.setDeviceRoleCode(request.deviceRoleCode());
        }
        if (request.requiredFlag() != null) {
            source.setRequiredFlag(request.requiredFlag() ? 1 : 0);
        }
        if (request.filePattern() != null) {
            source.setFilePattern(request.filePattern());
        }
        if (request.parsedAssetType() != null) {
            source.setParsedAssetType(request.parsedAssetType());
        }
        if (request.playbackKind() != null) {
            source.setPlaybackKind(request.playbackKind());
        }
        if (request.expectedFps() != null) {
            source.setExpectedFps(request.expectedFps());
        }
        if (request.expectedSampleRate() != null) {
            source.setExpectedSampleRate(request.expectedSampleRate());
        }
        if (request.sortOrder() != null) {
            source.setSortOrder(request.sortOrder());
        }
        source.setUpdatedAt(LocalDateTime.now());
        profileSourceMapper.updateById(source);
        return source;
    }

    @Override
    public void disableSource(Long profileId, Long sourceId) {
        CollectionProfileSource source = profileSourceMapper.selectById(sourceId);
        if (source == null || !source.getProfileId().equals(profileId)) {
            throw new BizException("Source not found: " + sourceId + " for profile: " + profileId);
        }
        source.setEnabled(0);
        source.setUpdatedAt(LocalDateTime.now());
        profileSourceMapper.updateById(source);
    }

    @Override
    public CollectionProfileSourceResponse toSourceResponse(CollectionProfileSource source) {
        return new CollectionProfileSourceResponse(
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
        );
    }

    private void createSourceFromItem(Long profileId, CreateSourceItem item) {
        CollectionProfileSource source = buildSourceEntity(profileId, item.sourceKey(), item.sourceName(),
                item.sourceType(), item.deviceRoleCode(), item.requiredFlag(),
                item.filePattern(), item.parsedAssetType(), item.playbackKind(),
                item.expectedFps(), item.expectedSampleRate(), item.sortOrder());
        profileSourceMapper.insert(source);
    }

    private CollectionProfileSource buildSourceEntity(
            Long profileId, String sourceKey, String sourceName, String sourceType,
            String deviceRoleCode, Boolean requiredFlag, String filePattern,
            String parsedAssetType, String playbackKind, Double expectedFps,
            Double expectedSampleRate, Integer sortOrder) {
        CollectionProfileSource source = new CollectionProfileSource();
        source.setProfileId(profileId);
        source.setSourceKey(sourceKey);
        source.setSourceName(sourceName);
        source.setSourceType(sourceType);
        source.setDeviceRoleCode(deviceRoleCode);
        source.setRequiredFlag(requiredFlag != null && requiredFlag ? 1 : 0);
        source.setFilePattern(filePattern);
        source.setParsedAssetType(defaultIfBlank(parsedAssetType, "OTHER"));
        source.setPlaybackKind(playbackKind);
        source.setExpectedFps(expectedFps);
        source.setExpectedSampleRate(expectedSampleRate);
        source.setSortOrder(sortOrder != null ? sortOrder : 0);
        source.setEnabled(1);
        source.setCreatedAt(LocalDateTime.now());
        source.setUpdatedAt(LocalDateTime.now());
        return source;
    }

    private static String defaultIfBlank(String value) {
        return defaultIfBlank(value, "");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
