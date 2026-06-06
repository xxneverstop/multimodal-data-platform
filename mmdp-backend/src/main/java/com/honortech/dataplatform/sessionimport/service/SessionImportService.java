package com.honortech.dataplatform.sessionimport.service;

import com.honortech.dataplatform.sessionimport.dto.SessionImportRequestContext;
import com.honortech.dataplatform.sessionimport.dto.FinalizeSessionImportRequest;
import com.honortech.dataplatform.sessionimport.dto.FinalizeSessionImportResponse;
import com.honortech.dataplatform.sessionimport.dto.InitiateImportUploadRequest;
import com.honortech.dataplatform.sessionimport.dto.InitiateImportUploadResponse;
import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;

public interface SessionImportService {

    SessionImportResponse importSession(SessionImportRequestContext context);

    InitiateImportUploadResponse initiateImportUpload(Long taskId, InitiateImportUploadRequest request);

    FinalizeSessionImportResponse finalizeImport(FinalizeSessionImportRequest request);
}
