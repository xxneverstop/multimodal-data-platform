package com.honortech.dataplatform.qc.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.honortech.dataplatform.common.enums.AssetType;
import com.honortech.dataplatform.common.enums.QcStatus;
import com.honortech.dataplatform.common.exception.BizException;
import com.honortech.dataplatform.qc.dto.QcCheckResult;
import com.honortech.dataplatform.qc.dto.QcExecutionResult;
import com.honortech.dataplatform.qc.dto.QcFileInfo;
import com.honortech.dataplatform.qc.dto.QcReportPayload;
import com.honortech.dataplatform.qc.dto.QcSampleData;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class QcInspectionServiceImpl implements QcInspectionService {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of("txt", "csv", "json", "bvh", "fbx");
    private static final Pattern TIMESTAMP_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}[- T]\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?$");

    private final ObjectMapper objectMapper;

    public QcInspectionServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public QcExecutionResult inspect(String originalFilename, String fileExt, String contentType, byte[] content, AssetType assetType) {
        List<QcCheckResult> checks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> previewLines = new ArrayList<>();

        addFilePresenceChecks(fileExt, content, checks, warnings, errors);

        String detectedFormat = "binary";
        if (assetType == AssetType.RGB_VIDEO_MP4) {
            detectedFormat = "video-mp4";
            inspectVideoMp4(fileExt, contentType, content.length, checks, warnings, errors);
        } else if (assetType == AssetType.SMPL_RESULT) {
            detectedFormat = "smpl-result";
            inspectSmplResult(fileExt, checks, errors);
        } else if ("txt".equalsIgnoreCase(fileExt)) {
            detectedFormat = "txt-timeseries";
            inspectTxtTimeseries(content, checks, warnings, errors, previewLines);
        } else if ("csv".equalsIgnoreCase(fileExt)) {
            detectedFormat = "csv";
            inspectCsv(content, checks, warnings, errors, previewLines, assetType);
        } else if ("json".equalsIgnoreCase(fileExt)) {
            detectedFormat = "json";
            inspectJson(content, checks, warnings, errors, previewLines);
        } else {
            warnings.add("File content inspection is limited for extension: " + fileExt);
            checks.add(new QcCheckResult("supportedExtension", evaluateExtension(fileExt).name(), extensionMessage(fileExt)));
        }

        if (!"txt".equalsIgnoreCase(fileExt) && !"csv".equalsIgnoreCase(fileExt) && !"json".equalsIgnoreCase(fileExt)) {
            if (!SUPPORTED_EXTENSIONS.contains(fileExt.toLowerCase(Locale.ROOT))) {
                errors.add("Unsupported extension: " + fileExt);
            }
        }

        QcStatus overallStatus = summarize(checks, warnings, errors);
        String summary = buildSummary(overallStatus, warnings, errors, detectedFormat);

        QcReportPayload payload = new QcReportPayload(
                new QcFileInfo(originalFilename, fileExt, content.length, contentType),
                detectedFormat,
                summary,
                overallStatus.name(),
                checks,
                new QcSampleData(previewLines.size(), previewLines),
                warnings,
                errors
        );

        return new QcExecutionResult(overallStatus, summary, toJson(payload), detectedFormat);
    }

    private void addFilePresenceChecks(
            String fileExt,
            byte[] content,
            List<QcCheckResult> checks,
            List<String> warnings,
            List<String> errors) {
        checks.add(new QcCheckResult("fileNotEmpty", content.length > 0 ? "PASSED" : "FAILED",
                content.length > 0 ? "File has content" : "File is empty"));

        if (content.length == 0) {
            errors.add("File is empty");
        }

        QcStatus extensionStatus = evaluateExtension(fileExt);
        checks.add(new QcCheckResult("supportedExtension", extensionStatus.name(), extensionMessage(fileExt)));
        if (extensionStatus == QcStatus.FAILED) {
            errors.add("Unsupported extension: " + fileExt);
        }

        if (content.length > 0 && content.length < 64) {
            warnings.add("File is very small and may be incomplete");
            checks.add(new QcCheckResult("fileSizeReasonable", QcStatus.WARNING.name(), "File is smaller than 64 bytes"));
        } else if (content.length > 100L * 1024 * 1024) {
            warnings.add("File is large; synchronous QC may become slow");
            checks.add(new QcCheckResult("fileSizeReasonable", QcStatus.WARNING.name(), "File is larger than 100 MB"));
        } else {
            checks.add(new QcCheckResult("fileSizeReasonable", QcStatus.PASSED.name(), "File size is within MVP thresholds"));
        }
    }

    private void inspectTxtTimeseries(
            byte[] content,
            List<QcCheckResult> checks,
            List<String> warnings,
            List<String> errors,
            List<String> previewLines) {
        List<String> lines = sampleLines(content, previewLines);
        if (lines.isEmpty()) {
            checks.add(new QcCheckResult("readablePreview", QcStatus.FAILED.name(), "No readable lines found"));
            errors.add("TXT file has no readable non-empty lines");
            return;
        }

        checks.add(new QcCheckResult("readablePreview", QcStatus.PASSED.name(), "Successfully read sample lines"));

        List<String[]> rows = lines.stream().map(line -> line.split(",")).toList();
        boolean firstColumnTimestampLike = rows.stream()
                .limit(Math.min(5, rows.size()))
                .allMatch(parts -> parts.length > 0 && TIMESTAMP_PATTERN.matcher(parts[0].trim()).matches());
        checks.add(new QcCheckResult(
                "timestampLikeFirstColumn",
                firstColumnTimestampLike ? QcStatus.PASSED.name() : QcStatus.WARNING.name(),
                firstColumnTimestampLike ? "First column looks like timestamps" : "First column does not consistently look like timestamps"
        ));
        if (!firstColumnTimestampLike) {
            warnings.add("First column is not consistently timestamp-like in sampled rows");
        }

        int expectedColumns = rows.getFirst().length;
        boolean stableColumns = rows.stream().allMatch(parts -> parts.length == expectedColumns);
        checks.add(new QcCheckResult(
                "stableColumnCount",
                stableColumns ? QcStatus.PASSED.name() : QcStatus.FAILED.name(),
                stableColumns ? "Sampled rows have consistent column counts" : "Sampled rows have inconsistent column counts"
        ));
        if (!stableColumns) {
            errors.add("Sampled rows have inconsistent column counts");
        }

        boolean numericColumns = rows.stream().allMatch(parts -> {
            if (parts.length < 2) {
                return false;
            }
            return Arrays.stream(parts, 1, parts.length).allMatch(this::isNumeric);
        });
        checks.add(new QcCheckResult(
                "numericValueColumns",
                numericColumns ? QcStatus.PASSED.name() : QcStatus.FAILED.name(),
                numericColumns ? "Numeric columns are parsable" : "One or more sampled numeric columns could not be parsed"
        ));
        if (!numericColumns) {
            errors.add("One or more sampled numeric columns could not be parsed");
        }
    }

    private void inspectCsv(
            byte[] content,
            List<QcCheckResult> checks,
            List<String> warnings,
            List<String> errors,
            List<String> previewLines,
            AssetType assetType) {
        List<String> lines = sampleLines(content, previewLines);
        if (lines.size() < 2) {
            checks.add(new QcCheckResult("readableCsv", QcStatus.FAILED.name(), "CSV must contain header and at least one data row"));
            errors.add("CSV must contain header and at least one data row");
            return;
        }

        checks.add(new QcCheckResult("readableCsv", QcStatus.PASSED.name(), "CSV header and sample rows were read"));
        String[] headers = lines.getFirst().split(",");
        List<String> normalizedHeaders = Arrays.stream(headers).map(header -> header.trim().toLowerCase(Locale.ROOT)).toList();

        boolean hasTimestamp = normalizedHeaders.stream().anyMatch("timestamp"::equals);
        checks.add(new QcCheckResult(
                "timestampHeader",
                hasTimestamp ? QcStatus.PASSED.name() : QcStatus.WARNING.name(),
                hasTimestamp ? "CSV contains timestamp header" : "CSV does not contain timestamp header"
        ));
        if (!hasTimestamp) {
            warnings.add("CSV does not contain timestamp header");
        }

        boolean hasImuFields = normalizedHeaders.stream().anyMatch(header -> header.contains("acc") || header.contains("gyro"));
        checks.add(new QcCheckResult(
                "imuHeaders",
                hasImuFields ? QcStatus.PASSED.name() : QcStatus.WARNING.name(),
                hasImuFields ? "CSV contains acc/gyro related headers" : "CSV does not contain acc/gyro related headers"
        ));
        if (!hasImuFields) {
            warnings.add("CSV does not contain acc/gyro related headers");
        }

        if (assetType == AssetType.MOCAP_CSV) {
            boolean hasQuaternion = normalizedHeaders.stream().anyMatch(header -> header.contains("quat") || header.contains("quaternion"));
            checks.add(new QcCheckResult(
                    "mocapQuaternionHeaders",
                    hasQuaternion ? QcStatus.PASSED.name() : QcStatus.WARNING.name(),
                    hasQuaternion ? "CSV contains quaternion related headers" : "CSV does not contain quaternion related headers"
            ));
            if (!hasTimestamp || (!hasImuFields && !hasQuaternion)) {
                warnings.add("MOCAP_CSV is missing timestamp or motion-related headers");
            }
        }
    }

    private void inspectJson(
            byte[] content,
            List<QcCheckResult> checks,
            List<String> warnings,
            List<String> errors,
            List<String> previewLines) {
        List<String> lines = sampleLines(content, previewLines);
        try {
            objectMapper.readTree(new String(content, StandardCharsets.UTF_8));
            checks.add(new QcCheckResult("jsonParseable", QcStatus.PASSED.name(), "JSON content is parseable"));
        } catch (Exception exception) {
            checks.add(new QcCheckResult("jsonParseable", QcStatus.FAILED.name(), "JSON content could not be parsed"));
            errors.add("JSON content could not be parsed");
        }
        warnings.add("JSON QC is limited to syntax validation in MVP");
    }

    private List<String> sampleLines(byte[] content, List<String> previewLines) {
        String text = new String(content, StandardCharsets.UTF_8);
        List<String> lines = text.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .limit(10)
                .toList();
        previewLines.addAll(lines.stream().limit(3).toList());
        return lines;
    }

    private QcStatus evaluateExtension(String fileExt) {
        return SUPPORTED_EXTENSIONS.contains(fileExt.toLowerCase(Locale.ROOT)) ? QcStatus.PASSED : QcStatus.FAILED;
    }

    private String extensionMessage(String fileExt) {
        return SUPPORTED_EXTENSIONS.contains(fileExt.toLowerCase(Locale.ROOT))
                ? "Extension is supported"
                : "Extension is not supported by the MVP";
    }

    private boolean isNumeric(String value) {
        try {
            Double.parseDouble(value.trim());
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private QcStatus summarize(List<QcCheckResult> checks, List<String> warnings, List<String> errors) {
        boolean hasFailedCheck = checks.stream().anyMatch(check -> QcStatus.FAILED.name().equals(check.status()));
        if (hasFailedCheck || !errors.isEmpty()) {
            return QcStatus.FAILED;
        }
        boolean hasWarningCheck = checks.stream().anyMatch(check -> QcStatus.WARNING.name().equals(check.status()));
        if (hasWarningCheck || !warnings.isEmpty()) {
            return QcStatus.WARNING;
        }
        return QcStatus.PASSED;
    }

    private String buildSummary(QcStatus status, List<String> warnings, List<String> errors, String detectedFormat) {
        if (status == QcStatus.FAILED) {
            return "QC failed for " + detectedFormat + " file: " + String.join("; ", errors);
        }
        if (status == QcStatus.WARNING) {
            return "QC completed with warnings for " + detectedFormat + " file: " + String.join("; ", warnings);
        }
        return "QC passed for " + detectedFormat + " file";
    }

    private String toJson(QcReportPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BizException("Failed to serialize QC report", exception);
        }
    }

    private void inspectSmplResult(String fileExt, List<QcCheckResult> checks, List<String> errors) {
        boolean supported = List.of("npz", "json", "pkl").contains(fileExt.toLowerCase(Locale.ROOT));
        checks.add(new QcCheckResult(
                "smplResultExtension",
                supported ? QcStatus.PASSED.name() : QcStatus.FAILED.name(),
                supported ? "SMPL result extension is supported" : "SMPL result extension must be npz/json/pkl"
        ));
        if (!supported) {
            errors.add("SMPL result extension must be npz/json/pkl");
        }
    }

    private void inspectVideoMp4(
            String fileExt,
            String contentType,
            long fileSize,
            List<QcCheckResult> checks,
            List<String> warnings,
            List<String> errors) {
        boolean mp4Extension = "mp4".equalsIgnoreCase(fileExt);
        checks.add(new QcCheckResult(
                "videoMp4Extension",
                mp4Extension ? QcStatus.PASSED.name() : QcStatus.FAILED.name(),
                mp4Extension ? "Video extension is mp4" : "RGB_VIDEO_MP4 must use mp4 extension"
        ));
        if (!mp4Extension) {
            errors.add("RGB_VIDEO_MP4 must use mp4 extension");
        }

        boolean contentTypeMatches = contentType != null && (contentType.equalsIgnoreCase("video/mp4") || contentType.equalsIgnoreCase("application/octet-stream"));
        checks.add(new QcCheckResult(
                "videoContentType",
                contentTypeMatches ? QcStatus.PASSED.name() : QcStatus.WARNING.name(),
                contentTypeMatches ? "Video content type is acceptable" : "Video content type is not video/mp4"
        ));
        if (!contentTypeMatches) {
            warnings.add("RGB_VIDEO_MP4 content type is not video/mp4");
        }

        if (fileSize < 1024) {
            checks.add(new QcCheckResult("videoFileSize", QcStatus.WARNING.name(), "Video file is unusually small"));
            warnings.add("RGB_VIDEO_MP4 file is unusually small");
        } else {
            checks.add(new QcCheckResult("videoFileSize", QcStatus.PASSED.name(), "Video file size is within MVP threshold"));
        }
    }
}
