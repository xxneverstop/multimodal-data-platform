package com.honortech.dataplatform.common.util;

import com.honortech.dataplatform.common.exception.BizException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * ZIP 归档解压工具，从 SessionImportServiceImpl 中提取为公共方法。
 */
public final class ArchiveUtils {

    private ArchiveUtils() {
    }

    public record ExtractedEntry(String relativePath, String contentType, byte[] bytes) {
    }

    /**
     * 解压 ZIP 字节数组，返回所有非目录条目的列表。
     */
    public static List<ExtractedEntry> extract(byte[] zipBytes) {
        List<ExtractedEntry> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = normalizePath(entry.getName());
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                zis.transferTo(bos);
                entries.add(new ExtractedEntry(path, detectContentType(path), bos.toByteArray()));
            }
        } catch (IOException e) {
            throw new BizException("Failed to read archive ZIP", e);
        }
        return entries;
    }

    private static String normalizePath(String entryName) {
        String normalized = entryName == null || entryName.isBlank() ? "unknown" : entryName.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank() || normalized.contains("../") || normalized.contains("..\\")
                || normalized.startsWith("..")) {
            throw new BizException("Archive contains unsafe entry path: " + entryName);
        }
        return normalized;
    }

    private static String detectContentType(String filename) {
        String ct = URLConnection.guessContentTypeFromName(filename);
        return ct == null ? "application/octet-stream" : ct;
    }
}
