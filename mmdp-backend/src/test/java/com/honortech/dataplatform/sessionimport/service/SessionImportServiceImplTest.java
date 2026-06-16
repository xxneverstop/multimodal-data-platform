package com.honortech.dataplatform.sessionimport.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.collector.service.CollectorClientService;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.SessionImportStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.storage.ObjectStat;
import com.honortech.dataplatform.common.storage.StorageProvider;
import com.honortech.dataplatform.common.storage.StorageProperties;
import com.honortech.dataplatform.common.storage.StorageRouter;
import com.honortech.dataplatform.common.storage.StorageService;
import com.honortech.dataplatform.common.storage.StoredFile;
import com.honortech.dataplatform.common.storage.TemporaryCredentials;
import com.honortech.dataplatform.common.util.BusinessCodeGenerator;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.mapper.DataFileMapper;
import com.honortech.dataplatform.profile.entity.CollectionProfile;
import com.honortech.dataplatform.profile.entity.CollectionProfileSource;
import com.honortech.dataplatform.profile.rule.DefaultPlaybackRuleResolver;
import com.honortech.dataplatform.profile.rule.DefaultSessionArchiveRuleResolver;
import com.honortech.dataplatform.profile.rule.DefaultSessionParserRuleResolver;
import com.honortech.dataplatform.profile.rule.DefaultSessionZipPackageRuleResolver;
import com.honortech.dataplatform.profile.rule.ProfileRuleRegistry;
import com.honortech.dataplatform.profile.service.CollectionProfileService;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.sessionimport.dto.FinalizeSessionImportRequest;
import com.honortech.dataplatform.sessionimport.dto.FinalizeSessionImportResponse;
import com.honortech.dataplatform.sessionimport.dto.FinalizeSessionImportUploadedFile;
import com.honortech.dataplatform.sessionimport.dto.SessionImportRequestContext;
import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;
import com.honortech.dataplatform.sessionimport.entity.SessionImportRecord;
import com.honortech.dataplatform.sessionimport.mapper.SessionImportRecordMapper;
import com.honortech.dataplatform.subject.entity.Subject;
import com.honortech.dataplatform.subject.service.SubjectService;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.mapper.AcquisitionTaskMapper;
import com.honortech.dataplatform.task.service.AcquisitionTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Map;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionImportServiceImplTest {

    private final CollectionSessionMapper sessionMapper = Mockito.mock(CollectionSessionMapper.class);
    private final DataFileMapper dataFileMapper = Mockito.mock(DataFileMapper.class);
    private final SessionImportRecordMapper sessionImportRecordMapper = Mockito.mock(SessionImportRecordMapper.class);
    private final AcquisitionTaskService acquisitionTaskService = Mockito.mock(AcquisitionTaskService.class);
    private final AcquisitionTaskMapper acquisitionTaskMapper = Mockito.mock(AcquisitionTaskMapper.class);
    private final DataAssetService dataAssetService = Mockito.mock(DataAssetService.class);
    private final StorageRouter storageRouter = Mockito.mock(StorageRouter.class);
    private final StorageService storageService = Mockito.mock(StorageService.class);
    private final StorageProperties storageProperties = new StorageProperties();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SubjectService subjectService = Mockito.mock(SubjectService.class);
    private final CollectionProfileService collectionProfileService = Mockito.mock(CollectionProfileService.class);
    private final CollectorClientService collectorClientService = Mockito.mock(CollectorClientService.class);
    private final BusinessCodeGenerator businessCodeGenerator = Mockito.mock(BusinessCodeGenerator.class);

    private SessionImportServiceImpl service;
    private CollectionProfile defaultProfile;

    @BeforeEach
    void setUp() {
        ProfileRuleRegistry profileRuleRegistry = new ProfileRuleRegistry(
                List.of(new DefaultSessionZipPackageRuleResolver()),
                List.of(new DefaultSessionParserRuleResolver()),
                List.of(new DefaultSessionArchiveRuleResolver()),
                List.of(new DefaultPlaybackRuleResolver())
        );
        service = new SessionImportServiceImpl(
                sessionMapper,
                dataFileMapper,
                sessionImportRecordMapper,
                acquisitionTaskService,
                acquisitionTaskMapper,
                dataAssetService,
                storageRouter,
                storageProperties,
                objectMapper,
                subjectService,
                collectionProfileService,
                collectorClientService,
                profileRuleRegistry,
                businessCodeGenerator
        );

        AtomicLong importIds = new AtomicLong(100L);
        AtomicLong fileIds = new AtomicLong(200L);
        AtomicLong sessionIds = new AtomicLong(300L);

        Mockito.doAnswer(invocation -> {
            SessionImportRecord record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId(importIds.getAndIncrement());
            }
            return 1;
        }).when(sessionImportRecordMapper).insert(any(SessionImportRecord.class));

        Mockito.doAnswer(invocation -> {
            DataFile file = invocation.getArgument(0);
            if (file.getId() == null) {
                file.setId(fileIds.getAndIncrement());
            }
            return 1;
        }).when(dataFileMapper).insert(any(DataFile.class));

        Mockito.doAnswer(invocation -> {
            CollectionSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(sessionIds.getAndIncrement());
            }
            return 1;
        }).when(sessionMapper).insert(any(CollectionSession.class));

        when(storageRouter.defaultService()).thenReturn(storageService);
        when(storageService.provider()).thenReturn(StorageProvider.OSS);
        storageProperties.getOss().setBucket("mmdp-bucket");
        storageProperties.getOss().setRegion("cn-hangzhou");
        storageProperties.getOss().setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        when(storageService.upload(any(), any(), any(), any())).thenAnswer(invocation -> new StoredFile(
                StorageProvider.OSS,
                "mmdp-bucket",
                invocation.getArgument(0, String.class),
                invocation.getArgument(3, String.class),
                invocation.getArgument(1, byte[].class).length,
                invocation.getArgument(2, String.class),
                "https://oss.example.com/mmdp-bucket/" + invocation.getArgument(0, String.class)
        ));
        when(storageService.assumeUploadCredentials(any(), any(), any())).thenReturn(
                new TemporaryCredentials("ak", "sk", "token", "2026-06-05T12:00:00Z")
        );
        Mockito.doNothing().when(storageService).copyObject(any(), any(), any(), any());
        when(storageService.headObject(any(), any())).thenAnswer(invocation ->
                new ObjectStat(resolveSizeByObjectKey(invocation.getArgument(1, String.class)), "etag")
        );
        when(sessionMapper.selectOne(any())).thenReturn(null);
        when(sessionImportRecordMapper.selectOne(any())).thenReturn(null);
        when(collectorClientService.resolveByCode(any())).thenReturn(null);
        when(businessCodeGenerator.next(any())).thenAnswer(invocation -> invocation.getArgument(0, String.class) + "-001");

        defaultProfile = new CollectionProfile();
        defaultProfile.setId(1L);
        defaultProfile.setProfileCode("BINOCULAR_HMD_IMU_V1");
        defaultProfile.setProfileName("Default Profile");
        defaultProfile.setModalityGroupCode("RGB_RGB_EGO_IMU");
        defaultProfile.setDeviceGroupCode("BINOCULAR_HMD_IMU");
        defaultProfile.setPackageRuleCode("SESSION_ZIP_V1");
        defaultProfile.setParserRuleCode("SESSION_JSONL_VIDEO_IMU_V1");
        defaultProfile.setArchiveRuleCode("SESSION_ARCHIVE_V1");
        defaultProfile.setPlaybackRuleCode("MULTI_VIDEO_IMU_V1");
        when(collectionProfileService.getRequiredByCode(defaultProfile.getProfileCode())).thenReturn(defaultProfile);
        when(collectionProfileService.getRequiredById(defaultProfile.getId())).thenReturn(defaultProfile);
        when(collectionProfileService.listSourcesByProfileId(defaultProfile.getId())).thenReturn(List.of(
                source("imu", "%imu%", "MOCAP_CSV", "imu"),
                source("cam01", "%cam01%", "RGB_VIDEO_MP4", "video")
        ));

        Subject subject = new Subject();
        subject.setId(5L);
        subject.setSubjectCode("S-001");
        subject.setSubjectName("Subject A");
        when(subjectService.resolveSubject(any(), any())).thenReturn(subject);
    }

    @Test
    void shouldAutoCreateTaskAndImportArchivePackage() throws Exception {
        AcquisitionTask createdTask = new AcquisitionTask();
        createdTask.setId(11L);
        createdTask.setProfileId(defaultProfile.getId());
        createdTask.setCollectDate(LocalDate.of(2026, 5, 29));
        when(acquisitionTaskService.createTask(any())).thenReturn(createdTask);

        MockMultipartFile manifest = new MockMultipartFile(
                "manifest",
                "manifest.json",
                "application/json",
                """
                {
                  "schemaVersion": "2.0",
                  "localRefs": {"localTaskId": "LT-1", "localSessionId": "LS-1"},
                  "task": {"name": "Walk Task", "profileCode": "BINOCULAR_HMD_IMU_V1"},
                  "subject": {"name": "Subject A", "code": "S-001"},
                  "action": {"name": "Walk"},
                  "session": {"startedAt": "2026-05-29T10:20:30", "durationMs": 1000},
                  "sources": {
                    "imu": {"type": "imu"},
                    "cam01": {"type": "video"}
                  }
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile archive = new MockMultipartFile(
                "archive",
                "session.zip",
                "application/zip",
                buildZip(
                        "imu/data.jsonl", "{\"sample\":1}\n".getBytes(StandardCharsets.UTF_8),
                        "cam01/video.mp4", new byte[]{1, 2, 3, 4}
                )
        );

        SessionImportResponse response = service.importSession(new SessionImportRequestContext(
                SessionImportServiceImpl.SOURCE_ENDPOINT_SESSION_IMPORTS,
                null,
                manifest,
                archive,
                null
        ));

        assertEquals(11L, response.platformTaskId());
        assertEquals(SessionImportStatus.IMPORTED.name(), response.status());
        assertEquals(false, response.existing());
        verify(acquisitionTaskService).createTask(any());
        verify(dataFileMapper, Mockito.times(3)).insert(any(DataFile.class));
        verify(sessionMapper).insert(any(CollectionSession.class));
        verify(dataAssetService, Mockito.atLeast(2)).createAcquisitionAsset(any(), any(), any(), any(), any(AssetType.class));
    }

    @Test
    void shouldReturnExistingImportWhenSessionAlreadyImported() {
        CollectionSession existingSession = new CollectionSession();
        existingSession.setId(9L);
        existingSession.setTaskId(7L);
        existingSession.setSessionId("LS-dup");
        when(sessionMapper.selectOne(any())).thenReturn(existingSession);

        SessionImportRecord existingRecord = new SessionImportRecord();
        existingRecord.setId(88L);
        existingRecord.setStatus(SessionImportStatus.IMPORTED.name());
        when(sessionImportRecordMapper.selectOne(any())).thenReturn(existingRecord);

        MockMultipartFile manifest = new MockMultipartFile(
                "manifest",
                "manifest.json",
                "application/json",
                """
                {
                  "schemaVersion": "2.0",
                  "localRefs": {"localSessionId": "LS-dup"},
                  "task": {"name": "Old Task", "profileCode": "BINOCULAR_HMD_IMU_V1"},
                  "subject": {"name": "Subject A"},
                  "action": {"name": "Walk"},
                  "session": {"startedAt": "2026-05-29T10:20:30", "durationMs": 1000},
                  "sources": {"imu": {"type": "imu"}}
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        SessionImportResponse response = service.importSession(new SessionImportRequestContext(
                SessionImportServiceImpl.SOURCE_ENDPOINT_SESSION_IMPORTS,
                null,
                manifest,
                null,
                List.of(new MockMultipartFile("files", "imu.jsonl", "application/json", "{}".getBytes(StandardCharsets.UTF_8)))
        ));

        assertEquals(true, response.existing());
        assertEquals(88L, response.importId());
        verify(acquisitionTaskService, never()).createTask(any());
        verify(sessionMapper, never()).insert(any(CollectionSession.class));
    }

    @Test
    void shouldUseLegacyFallbackTaskIdWhenManifestHasNoPlatformTaskId() {
        AcquisitionTask task = new AcquisitionTask();
        task.setId(99L);
        task.setProfileId(defaultProfile.getId());
        when(acquisitionTaskService.getTask(99L)).thenReturn(task);

        MockMultipartFile manifest = new MockMultipartFile(
                "manifest",
                "legacy.json",
                "application/json",
                """
                {
                  "taskId": "LOCAL-TASK-1",
                  "sessionId": "LOCAL-SESSION-1",
                  "taskName": "Legacy Task",
                  "profileCode": "BINOCULAR_HMD_IMU_V1",
                  "subjectName": "Subject Legacy",
                  "actionName": "Jump",
                  "startedAt": "2026-05-29T10:20:30",
                  "durationMs": 1000,
                  "sources": {"imu": {"type": "imu"}}
                }
                """.getBytes(StandardCharsets.UTF_8)
        );
        MultipartFile file = new MockMultipartFile("files", "imu-data.jsonl", "application/json", "{\"ok\":true}".getBytes(StandardCharsets.UTF_8));

        SessionImportResponse response = service.importSession(new SessionImportRequestContext(
                SessionImportServiceImpl.SOURCE_ENDPOINT_LEGACY_TASK_ROUTE,
                99L,
                manifest,
                null,
                List.of(file)
        ));

        assertEquals(99L, response.platformTaskId());
        verify(acquisitionTaskService).getTask(99L);
        verify(acquisitionTaskService, never()).createTask(any());
    }

    @Test
    void shouldMarkImportFailedForUnsafeArchiveEntries() throws Exception {
        AcquisitionTask createdTask = new AcquisitionTask();
        createdTask.setId(15L);
        createdTask.setProfileId(defaultProfile.getId());
        when(acquisitionTaskService.createTask(any())).thenReturn(createdTask);

        MockMultipartFile manifest = new MockMultipartFile(
                "manifest",
                "manifest.json",
                "application/json",
                """
                {
                  "localRefs": {"localSessionId": "LS-unsafe"},
                  "task": {"name": "Walk Task", "profileCode": "BINOCULAR_HMD_IMU_V1"},
                  "subject": {"name": "Subject A"},
                  "action": {"name": "Walk"},
                  "session": {"startedAt": "2026-05-29T10:20:30", "durationMs": 1000},
                  "sources": {"imu": {"type": "imu"}}
                }
                """.getBytes(StandardCharsets.UTF_8)
        );

        MockMultipartFile archive = new MockMultipartFile(
                "archive",
                "session.zip",
                "application/zip",
                buildZip("../evil.jsonl", "{}".getBytes(StandardCharsets.UTF_8))
        );

        assertThrows(BizException.class, () -> service.importSession(new SessionImportRequestContext(
                SessionImportServiceImpl.SOURCE_ENDPOINT_SESSION_IMPORTS,
                null,
                manifest,
                archive,
                null
        )));

        ArgumentCaptor<SessionImportRecord> captor = ArgumentCaptor.forClass(SessionImportRecord.class);
        verify(sessionImportRecordMapper, Mockito.atLeastOnce()).updateById(captor.capture());
        SessionImportRecord lastUpdate = captor.getValue();
        assertEquals(SessionImportStatus.FAILED.name(), lastUpdate.getStatus());
    }

    @Test
    void shouldFinalizeDirectoryImportAndCreateSessionAssets() {
        AcquisitionTask task = new AcquisitionTask();
        task.setId(21L);
        task.setTaskCode("TASK-021");
        task.setSubjectCode("S-001");
        task.setProfileId(defaultProfile.getId());
        task.setCollectDate(LocalDate.of(2026, 6, 5));
        when(acquisitionTaskService.getTask(21L)).thenReturn(task);

        FinalizeSessionImportResponse response = service.finalizeImport(new FinalizeSessionImportRequest(
                21L,
                "imp-001",
                "imp-001",
                objectMapper.valueToTree(Map.of(
                        "localRefs", Map.of("localSessionId", "LS-dir-001"),
                        "task", Map.of("name", "Walk Task", "profileCode", "BINOCULAR_HMD_IMU_V1"),
                        "subject", Map.of("code", "S-001", "name", "Subject A"),
                        "action", Map.of("name", "Walk"),
                        "session", Map.of("startedAt", "2026-06-05T10:20:30", "timestampPolicy", "device"),
                        "sources", Map.of(
                                "imu", Map.of("path", "sources/imu/data_imu.jsonl"),
                                "cam01", Map.of("path", "sources/cam01/video.mp4")
                        ),
                        "artifacts", List.of(Map.of("path", "artifacts/readme.md"))
                )),
                List.of(
                        new FinalizeSessionImportUploadedFile("manifest.json", "manifest.json", "imports/21/imp-001/manifest.json", "application/json", 512L, null),
                        new FinalizeSessionImportUploadedFile("data_imu.jsonl", "sources/imu/data_imu.jsonl", "imports/21/imp-001/sources/imu/data_imu.jsonl", "application/json", 128L, null),
                        new FinalizeSessionImportUploadedFile("video.mp4", "sources/cam01/video.mp4", "imports/21/imp-001/sources/cam01/video.mp4", "video/mp4", 2048L, null),
                        new FinalizeSessionImportUploadedFile("readme.md", "artifacts/readme.md", "imports/21/imp-001/artifacts/readme.md", "text/markdown", 64L, null)
                )
        ));

        assertEquals(false, response.existing());
        assertEquals(SessionImportStatus.IMPORTED.name(), response.status());
        assertEquals(4, response.createdFileCount());
        assertEquals(2, response.createdAssetCount());
        verify(sessionMapper).insert(any(CollectionSession.class));
        verify(dataFileMapper, Mockito.times(4)).insert(any(DataFile.class));
        verify(dataAssetService, Mockito.times(2)).createAcquisitionAsset(eq(21L), any(), any(), any(), any(AssetType.class));
    }

    @Test
    void shouldRejectDirectoryImportWhenSubjectCodeConflicts() {
        AcquisitionTask task = new AcquisitionTask();
        task.setId(22L);
        task.setSubjectCode("S-002");
        task.setProfileId(defaultProfile.getId());
        task.setCollectDate(LocalDate.of(2026, 6, 5));
        when(acquisitionTaskService.getTask(22L)).thenReturn(task);

        assertThrows(BizException.class, () -> service.finalizeImport(new FinalizeSessionImportRequest(
                22L,
                "imp-002",
                "imp-002",
                objectMapper.valueToTree(Map.of(
                        "localRefs", Map.of("localSessionId", "LS-dir-002"),
                        "task", Map.of("profileCode", "BINOCULAR_HMD_IMU_V1"),
                        "subject", Map.of("code", "S-001"),
                        "action", Map.of("name", "Walk"),
                        "session", Map.of("startedAt", "2026-06-05T10:20:30", "timestampPolicy", "device"),
                        "sources", Map.of("imu", Map.of("path", "sources/imu/data_imu.jsonl"))
                )),
                List.of(
                        new FinalizeSessionImportUploadedFile("manifest.json", "manifest.json", "imports/22/imp-002/manifest.json", "application/json", 256L, null),
                        new FinalizeSessionImportUploadedFile("data_imu.jsonl", "sources/imu/data_imu.jsonl", "imports/22/imp-002/sources/imu/data_imu.jsonl", "application/json", 128L, null)
                )
        )));
    }

    @Test
    void shouldRejectDirectoryImportWhenSourcePathIsOutsideItsSourceDirectory() {
        AcquisitionTask task = new AcquisitionTask();
        task.setId(23L);
        task.setTaskCode("TASK-023");
        task.setSubjectCode("S-001");
        task.setProfileId(defaultProfile.getId());
        task.setCollectDate(LocalDate.of(2026, 6, 5));
        when(acquisitionTaskService.getTask(23L)).thenReturn(task);

        BizException exception = assertThrows(BizException.class, () -> service.finalizeImport(new FinalizeSessionImportRequest(
                23L,
                "imp-003",
                "imp-003",
                objectMapper.valueToTree(Map.of(
                        "localRefs", Map.of("localSessionId", "LS-dir-003"),
                        "task", Map.of("name", "Walk Task", "profileCode", "BINOCULAR_HMD_IMU_V1"),
                        "subject", Map.of("code", "S-001", "name", "Subject A"),
                        "action", Map.of("name", "Walk"),
                        "session", Map.of("startedAt", "2026-06-05T10:20:30", "timestampPolicy", "device"),
                        "sources", Map.of(
                                "imu", Map.of("path", "sources/cam01/data_imu.jsonl"),
                                "cam01", Map.of("path", "sources/cam01/video.mp4")
                        )
                )),
                List.of(
                        new FinalizeSessionImportUploadedFile("manifest.json", "manifest.json", "imports/23/imp-003/manifest.json", "application/json", 512L, null),
                        new FinalizeSessionImportUploadedFile("data_imu.jsonl", "sources/cam01/data_imu.jsonl", "imports/23/imp-003/sources/cam01/data_imu.jsonl", "application/json", 128L, null),
                        new FinalizeSessionImportUploadedFile("video.mp4", "sources/cam01/video.mp4", "imports/23/imp-003/sources/cam01/video.mp4", "video/mp4", 2048L, null)
                )
        )));

        assertEquals("Manifest source path must be under sources/imu/: sources/cam01/data_imu.jsonl", exception.getMessage());
    }

    @Test
    void shouldRejectDirectoryImportWhenArtifactPathIsOutsideArtifactsDirectory() {
        AcquisitionTask task = new AcquisitionTask();
        task.setId(24L);
        task.setTaskCode("TASK-024");
        task.setSubjectCode("S-001");
        task.setProfileId(defaultProfile.getId());
        task.setCollectDate(LocalDate.of(2026, 6, 5));
        when(acquisitionTaskService.getTask(24L)).thenReturn(task);

        BizException exception = assertThrows(BizException.class, () -> service.finalizeImport(new FinalizeSessionImportRequest(
                24L,
                "imp-004",
                "imp-004",
                objectMapper.valueToTree(Map.of(
                        "localRefs", Map.of("localSessionId", "LS-dir-004"),
                        "task", Map.of("name", "Walk Task", "profileCode", "BINOCULAR_HMD_IMU_V1"),
                        "subject", Map.of("code", "S-001", "name", "Subject A"),
                        "action", Map.of("name", "Walk"),
                        "session", Map.of("startedAt", "2026-06-05T10:20:30", "timestampPolicy", "device"),
                        "sources", Map.of(
                                "imu", Map.of("path", "sources/imu/data_imu.jsonl"),
                                "cam01", Map.of("path", "sources/cam01/video.mp4")
                        ),
                        "artifacts", List.of(Map.of("path", "sources/imu/readme.md"))
                )),
                List.of(
                        new FinalizeSessionImportUploadedFile("manifest.json", "manifest.json", "imports/24/imp-004/manifest.json", "application/json", 512L, null),
                        new FinalizeSessionImportUploadedFile("data_imu.jsonl", "sources/imu/data_imu.jsonl", "imports/24/imp-004/sources/imu/data_imu.jsonl", "application/json", 128L, null),
                        new FinalizeSessionImportUploadedFile("video.mp4", "sources/cam01/video.mp4", "imports/24/imp-004/sources/cam01/video.mp4", "video/mp4", 2048L, null),
                        new FinalizeSessionImportUploadedFile("readme.md", "sources/imu/readme.md", "imports/24/imp-004/sources/imu/readme.md", "text/markdown", 64L, null)
                )
        )));

        assertEquals("Manifest artifact path must be under artifacts/: sources/imu/readme.md", exception.getMessage());
    }

    @Test
    void shouldFinalizeZedImuDirectoryImportWithTwoSourcesAndArtifacts() {
        CollectionProfile zedProfile = new CollectionProfile();
        zedProfile.setId(2L);
        zedProfile.setProfileCode("ZED_STEREO_IMU_V1");
        zedProfile.setProfileName("ZED Stereo + IMU Raw Import");
        zedProfile.setModalityGroupCode("ZED_IMU_RAW");
        zedProfile.setDeviceGroupCode("ZED_IMU");
        zedProfile.setPackageRuleCode("SESSION_ZIP_V1");
        zedProfile.setParserRuleCode("SESSION_JSONL_VIDEO_IMU_V1");
        zedProfile.setArchiveRuleCode("SESSION_ARCHIVE_V1");
        zedProfile.setPlaybackRuleCode("MULTI_VIDEO_IMU_V1");

        when(collectionProfileService.getRequiredByCode("ZED_STEREO_IMU_V1")).thenReturn(zedProfile);
        when(collectionProfileService.getRequiredById(2L)).thenReturn(zedProfile);
        when(collectionProfileService.listSourcesByProfileId(2L)).thenReturn(List.of(
                sourceForProfile(2L, "zed", "%zed%", "OTHER", "zed_mcap"),
                sourceForProfile(2L, "imu", "%position%", "OTHER", "pose_csv")
        ));

        AcquisitionTask task = new AcquisitionTask();
        task.setId(25L);
        task.setTaskCode("TASK-025");
        task.setSubjectCode("S-001");
        task.setProfileId(2L);
        task.setCollectDate(LocalDate.of(2026, 6, 2));
        when(acquisitionTaskService.getTask(25L)).thenReturn(task);

        FinalizeSessionImportResponse response = service.finalizeImport(new FinalizeSessionImportRequest(
                25L,
                "imp-zed-001",
                "imp-zed-001",
                objectMapper.valueToTree(Map.of(
                        "localRefs", Map.of("localTaskId", "ZED-IMU-TASK-001", "localSessionId", "zed-imu-1"),
                        "task", Map.of("name", "ZED+IMU 原始采集导入", "profileCode", "ZED_STEREO_IMU_V1"),
                        "subject", Map.of("code", "S-001", "name", "Subject A"),
                        "action", Map.of("code", "zed_imu_capture", "name", "ZED+IMU Capture"),
                        "session", Map.of("startedAt", "2026-06-02T10:36:00", "timestampPolicy", "device"),
                        "sources", Map.of(
                                "zed", Map.of("type", "zed_mcap", "path", "sources/zed/zed.svo2"),
                                "imu", Map.of("type", "pose_csv", "path", "sources/imu/position.csv")
                        ),
                        "artifacts", List.of(
                                Map.of("path", "artifacts/zed/depth.npz", "kind", "DEPTH_RAW"),
                                Map.of("path", "artifacts/zed/frame_timestamps.csv", "kind", "FRAME_TIMESTAMPS_CSV"),
                                Map.of("path", "artifacts/zed/frame_timestamps.npz", "kind", "FRAME_TIMESTAMPS_CACHE"),
                                Map.of("path", "artifacts/imu/position.npz", "kind", "POSE_CACHE")
                        )
                )),
                List.of(
                        new FinalizeSessionImportUploadedFile("manifest.json", "manifest.json", "imports/25/imp-zed-001/manifest.json", "application/json", 512L, null),
                        new FinalizeSessionImportUploadedFile("zed.svo2", "sources/zed/zed.svo2", "imports/25/imp-zed-001/sources/zed/zed.svo2", "application/octet-stream", 27501328L, null),
                        new FinalizeSessionImportUploadedFile("position.csv", "sources/imu/position.csv", "imports/25/imp-zed-001/sources/imu/position.csv", "text/csv", 63687L, null),
                        new FinalizeSessionImportUploadedFile("depth.npz", "artifacts/zed/depth.npz", "imports/25/imp-zed-001/artifacts/zed/depth.npz", "application/octet-stream", 13819049L, null),
                        new FinalizeSessionImportUploadedFile("frame_timestamps.csv", "artifacts/zed/frame_timestamps.csv", "imports/25/imp-zed-001/artifacts/zed/frame_timestamps.csv", "text/csv", 20026L, null),
                        new FinalizeSessionImportUploadedFile("frame_timestamps.npz", "artifacts/zed/frame_timestamps.npz", "imports/25/imp-zed-001/artifacts/zed/frame_timestamps.npz", "application/octet-stream", 4875L, null),
                        new FinalizeSessionImportUploadedFile("position.npz", "artifacts/imu/position.npz", "imports/25/imp-zed-001/artifacts/imu/position.npz", "application/octet-stream", 6053L, null)
                )
        ));

        assertEquals(false, response.existing());
        assertEquals(SessionImportStatus.IMPORTED.name(), response.status());
        assertEquals(7, response.createdFileCount());
        assertEquals(2, response.createdAssetCount());
        assertEquals("ZED_STEREO_IMU_V1", response.profileCode());
        verify(sessionMapper).insert(any(CollectionSession.class));
        verify(storageService, Mockito.times(7)).copyObject(eq("mmdp-bucket"), any(), eq("mmdp-bucket"), any());
        verify(dataFileMapper, Mockito.times(7)).insert(any(DataFile.class));
        verify(dataAssetService, Mockito.times(2)).createAcquisitionAsset(eq(25L), any(), any(), any(), any(AssetType.class));
        ArgumentCaptor<DataFile> fileCaptor = ArgumentCaptor.forClass(DataFile.class);
        verify(dataFileMapper, Mockito.times(7)).insert(fileCaptor.capture());
        assertTrue(fileCaptor.getAllValues().stream().allMatch(file ->
                file.getObjectKey().startsWith("tasks/TASK-025/sessions/SESS-001/imports/")));
    }

    @Test
    void shouldRejectZedImuDirectoryImportWhenRequiredSourceMissing() {
        CollectionProfile zedProfile = new CollectionProfile();
        zedProfile.setId(2L);
        zedProfile.setProfileCode("ZED_STEREO_IMU_V1");
        zedProfile.setProfileName("ZED Stereo + IMU Raw Import");
        zedProfile.setModalityGroupCode("ZED_IMU_RAW");
        zedProfile.setDeviceGroupCode("ZED_IMU");
        zedProfile.setPackageRuleCode("SESSION_ZIP_V1");
        zedProfile.setParserRuleCode("SESSION_JSONL_VIDEO_IMU_V1");
        zedProfile.setArchiveRuleCode("SESSION_ARCHIVE_V1");
        zedProfile.setPlaybackRuleCode("MULTI_VIDEO_IMU_V1");

        when(collectionProfileService.getRequiredByCode("ZED_STEREO_IMU_V1")).thenReturn(zedProfile);
        when(collectionProfileService.getRequiredById(2L)).thenReturn(zedProfile);
        when(collectionProfileService.listSourcesByProfileId(2L)).thenReturn(List.of(
                sourceForProfile(2L, "zed", "%zed%", "OTHER", "zed_mcap"),
                sourceForProfile(2L, "imu", "%position%", "OTHER", "pose_csv")
        ));

        AcquisitionTask task = new AcquisitionTask();
        task.setId(26L);
        task.setTaskCode("TASK-026");
        task.setSubjectCode("S-001");
        task.setProfileId(2L);
        task.setCollectDate(LocalDate.of(2026, 6, 2));
        when(acquisitionTaskService.getTask(26L)).thenReturn(task);

        BizException exception = assertThrows(BizException.class, () -> service.finalizeImport(new FinalizeSessionImportRequest(
                26L,
                "imp-zed-002",
                "imp-zed-002",
                objectMapper.valueToTree(Map.of(
                        "localRefs", Map.of("localSessionId", "zed-imu-2"),
                        "task", Map.of("name", "ZED+IMU 原始采集导入", "profileCode", "ZED_STEREO_IMU_V1"),
                        "subject", Map.of("code", "S-001", "name", "Subject A"),
                        "action", Map.of("code", "zed_imu_capture", "name", "ZED+IMU Capture"),
                        "session", Map.of("startedAt", "2026-06-02T10:36:00", "timestampPolicy", "device"),
                        "sources", Map.of(
                                "zed", Map.of("type", "zed_mcap", "path", "sources/zed/zed.svo2")
                        )
                )),
                List.of(
                        new FinalizeSessionImportUploadedFile("manifest.json", "manifest.json", "imports/26/imp-zed-002/manifest.json", "application/json", 512L, null),
                        new FinalizeSessionImportUploadedFile("zed.svo2", "sources/zed/zed.svo2", "imports/26/imp-zed-002/sources/zed/zed.svo2", "application/octet-stream", 27501328L, null)
                )
        )));

        assertEquals("Manifest missing required source: imu", exception.getMessage());
    }

    private CollectionProfileSource source(String sourceKey, String filePattern, String assetType, String sourceType) {
        CollectionProfileSource source = new CollectionProfileSource();
        source.setProfileId(defaultProfile.getId());
        source.setSourceKey(sourceKey);
        source.setSourceName(sourceKey);
        source.setSourceType(sourceType);
        source.setFilePattern(filePattern);
        source.setParsedAssetType(assetType);
        source.setRequiredFlag(1);
        source.setEnabled(1);
        source.setSortOrder(1);
        return source;
    }

    private CollectionProfileSource sourceForProfile(Long profileId, String sourceKey, String filePattern, String assetType, String sourceType) {
        CollectionProfileSource source = new CollectionProfileSource();
        source.setProfileId(profileId);
        source.setSourceKey(sourceKey);
        source.setSourceName(sourceKey);
        source.setSourceType(sourceType);
        source.setFilePattern(filePattern);
        source.setParsedAssetType(assetType);
        source.setRequiredFlag(1);
        source.setEnabled(1);
        source.setSortOrder(1);
        return source;
    }

    private byte[] buildZip(Object... entries) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (int i = 0; i < entries.length; i += 2) {
                String name = (String) entries[i];
                byte[] bytes = (byte[]) entries[i + 1];
                zipOutputStream.putNextEntry(new ZipEntry(name));
                zipOutputStream.write(bytes);
                zipOutputStream.closeEntry();
            }
        }
        return outputStream.toByteArray();
    }

    private long resolveSizeByObjectKey(String objectKey) {
        if (objectKey.endsWith("manifest.json")) {
            return 512L;
        }
        if (objectKey.endsWith("data_imu.jsonl")) {
            return 128L;
        }
        if (objectKey.endsWith("video.mp4")) {
            return 2048L;
        }
        if (objectKey.endsWith("readme.md")) {
            return 64L;
        }
        if (objectKey.endsWith("zed.svo2")) {
            return 27501328L;
        }
        if (objectKey.endsWith("position.csv")) {
            return 63687L;
        }
        if (objectKey.endsWith("depth.npz")) {
            return 13819049L;
        }
        if (objectKey.endsWith("frame_timestamps.csv")) {
            return 20026L;
        }
        if (objectKey.endsWith("frame_timestamps.npz")) {
            return 4875L;
        }
        if (objectKey.endsWith("position.npz")) {
            return 6053L;
        }
        return 1L;
    }
}
