package com.honortech.dataplatform.qc.dto;

public record QcFileInfo(String originalFilename, String fileExt, long fileSize, String contentType) {
}
