package com.honortech.dataplatform.qc.dto;

import java.util.List;

public record QcSampleData(int sampledRows, List<String> previewLines) {
}
