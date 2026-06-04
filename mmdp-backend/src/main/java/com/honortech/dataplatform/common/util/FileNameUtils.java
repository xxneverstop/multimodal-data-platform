package com.honortech.dataplatform.common.util;

import java.util.Locale;
import java.util.UUID;

public final class FileNameUtils {

    private FileNameUtils() {
    }

    public static String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    public static String buildObjectKey(Long taskId, String filename) {
        String extension = getExtension(filename);
        String suffix = extension.isBlank() ? "" : "." + extension;
        return "tasks/" + taskId + "/" + UUID.randomUUID() + suffix;
    }

    /**
     * 统一 OSS 对象路径：tasks/{taskCode}/sessions/{sessionCode}/{role}/{sourceKey}/{filename}
     */
    public static String buildObjectKey(String taskCode, String sessionCode, String fileRole,
                                        String sourceKey, String filename) {
        String safeRole = fileRole == null || fileRole.isBlank() ? "unknown" : fileRole;
        String safeSourceKey = sourceKey == null || sourceKey.isBlank() ? "unknown" : sourceKey;
        return "tasks/" + taskCode + "/sessions/" + sessionCode
                + "/" + safeRole + "/" + safeSourceKey + "/" + filename;
    }
}
