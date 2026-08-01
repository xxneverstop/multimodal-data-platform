package com.honortech.dataplatform.processing.util;

/**
 * Pipeline ID 规范化工具。
 * 全链路统一规范：trim + uppercase，确保 MySQL（大小写不敏感）、
 * Java ConcurrentHashMap（大小写敏感）、Python dict（大小写敏感）
 * 三层之间 pipeline_id 匹配一致。
 */
public final class PipelineIdNormalizer {

    private PipelineIdNormalizer() {
    }

    /**
     * 规范形式：去除首尾空白 + 转大写。
     *
     * @param raw 原始值，可为 null
     * @return 规范化后的字符串；raw 为 null 时返回 null
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        return raw.trim().toUpperCase();
    }
}
