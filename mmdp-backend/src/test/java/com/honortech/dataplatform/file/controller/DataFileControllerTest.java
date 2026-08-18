package com.honortech.dataplatform.file.controller;

import com.honortech.dataplatform.asset.entity.DataAsset;
import com.honortech.dataplatform.asset.service.DataAssetService;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.common.storage.StorageProvider;
import com.honortech.dataplatform.common.storage.StorageRouter;
import com.honortech.dataplatform.common.storage.StorageService;
import com.honortech.dataplatform.file.entity.DataFile;
import com.honortech.dataplatform.file.service.DataFileService;
import com.honortech.dataplatform.processing.dto.ProcessingJobResponse;
import com.honortech.dataplatform.processing.service.ProcessingJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class DataFileControllerTest {

    private final DataFileService dataFileService = Mockito.mock(DataFileService.class);
    private final StorageRouter storageRouter = Mockito.mock(StorageRouter.class);
    private final StorageService storageService = Mockito.mock(StorageService.class);
    private final DataAssetService dataAssetService = Mockito.mock(DataAssetService.class);
    private final ProcessingJobService processingJobService = Mockito.mock(ProcessingJobService.class);
    private final DataFileController controller = new DataFileController(
            dataFileService,
            storageRouter,
            dataAssetService,
            processingJobService
    );

    @BeforeEach
    void setUp() {
        when(dataAssetService.listByTaskId(100L)).thenReturn(List.of());
        when(processingJobService.listJobsByTaskId(100L)).thenReturn(List.of());
    }

    @Test
    void shouldDownloadRawAndDerivedSessionFilesAsZip() throws Exception {
        DataFile rawFile = file(1L, "SOURCE", "camera/video.mp4", "video.mp4", "raw-key");
        DataFile derivedFile = file(2L, "PROCESSED_OUTPUT", "../metrics/result.json", "result.json", "derived-key");
        DataFile viewerFile = file(3L, "PROCESSED_OUTPUT", "viewer/motion.json", "motion.json", "viewer-key");
        DataAsset metricsAsset = derivedAsset(2L, 42L);
        DataAsset viewerAsset = derivedAsset(3L, 43L);
        ProcessingJobResponse metricsJob = processingJob(42L, "MOTION_METRICS");
        ProcessingJobResponse viewerJob = processingJob(43L, "MOTION_VIEWER");
        when(dataFileService.listFilesBySessionId(7L)).thenReturn(List.of(rawFile, derivedFile, viewerFile));
        when(dataAssetService.listByTaskId(100L)).thenReturn(List.of(metricsAsset, viewerAsset));
        when(processingJobService.listJobsByTaskId(100L)).thenReturn(List.of(metricsJob, viewerJob));
        when(storageRouter.get(StorageProvider.OSS)).thenReturn(storageService);
        when(storageService.download("bucket", "raw-key"))
                .thenReturn(new ByteArrayInputStream("raw".getBytes(StandardCharsets.UTF_8)));
        when(storageService.download("bucket", "derived-key"))
                .thenReturn(new ByteArrayInputStream("derived".getBytes(StandardCharsets.UTF_8)));
        when(storageService.download("bucket", "viewer-key"))
                .thenReturn(new ByteArrayInputStream("viewer".getBytes(StandardCharsets.UTF_8)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.downloadSessionFiles(7L, response);

        assertEquals("application/zip", response.getContentType());
        assertTrue(response.getHeader("Content-Disposition").contains("session-7-all-assets.zip"));
        List<ArchiveEntry> entries = readEntries(response.getContentAsByteArray());
        assertEquals(List.of(
                        "raw/camera/video.mp4",
                        "derived/MOTION_METRICS/job-42/metrics/result.json",
                        "derived/MOTION_VIEWER/job-43/viewer/motion.json"
                ),
                entries.stream().map(ArchiveEntry::name).toList());
        assertArrayEquals("raw".getBytes(StandardCharsets.UTF_8), entries.get(0).content());
        assertArrayEquals("derived".getBytes(StandardCharsets.UTF_8), entries.get(1).content());
        assertArrayEquals("viewer".getBytes(StandardCharsets.UTF_8), entries.get(2).content());
    }

    @Test
    void shouldDisambiguateDuplicateArchivePaths() throws Exception {
        DataFile first = file(11L, "SOURCE", "same.csv", "same.csv", "first-key");
        DataFile second = file(12L, "SOURCE", "same.csv", "same.csv", "second-key");
        when(dataFileService.listFilesBySessionId(8L)).thenReturn(List.of(first, second));
        when(storageRouter.get(StorageProvider.OSS)).thenReturn(storageService);
        when(storageService.download("bucket", "first-key")).thenReturn(new ByteArrayInputStream(new byte[]{1}));
        when(storageService.download("bucket", "second-key")).thenReturn(new ByteArrayInputStream(new byte[]{2}));
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.downloadSessionFiles(8L, response);

        List<String> entryNames = readEntries(response.getContentAsByteArray()).stream().map(ArchiveEntry::name).toList();
        assertEquals(List.of("raw/same.csv", "raw/file-12-same.csv"), entryNames);
    }

    @Test
    void shouldRejectDownloadWhenSessionHasNoFiles() {
        when(dataFileService.listFilesBySessionId(9L)).thenReturn(List.of());

        BizException exception = assertThrows(
                BizException.class,
                () -> controller.downloadSessionFiles(9L, new MockHttpServletResponse())
        );

        assertEquals("该采集暂无可下载文件", exception.getMessage());
    }

    private DataFile file(Long id, String fileRole, String relativePath, String originalFilename, String objectKey) {
        DataFile file = new DataFile();
        file.setId(id);
        file.setTaskId(100L);
        file.setFileRole(fileRole);
        file.setRelativePath(relativePath);
        file.setOriginalFilename(originalFilename);
        file.setStorageProvider("OSS");
        file.setBucketName("bucket");
        file.setObjectKey(objectKey);
        return file;
    }

    private DataAsset derivedAsset(Long fileId, Long jobId) {
        DataAsset asset = new DataAsset();
        asset.setFileId(fileId);
        asset.setProducedByJobId(jobId);
        return asset;
    }

    private ProcessingJobResponse processingJob(Long jobId, String pipelineId) {
        ProcessingJobResponse job = Mockito.mock(ProcessingJobResponse.class);
        when(job.id()).thenReturn(jobId);
        when(job.pipelineId()).thenReturn(pipelineId);
        return job;
    }

    private List<ArchiveEntry> readEntries(byte[] archive) throws Exception {
        List<ArchiveEntry> entries = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.add(new ArchiveEntry(entry.getName(), zip.readAllBytes()));
            }
        }
        return entries;
    }

    private record ArchiveEntry(String name, byte[] content) {
    }
}
