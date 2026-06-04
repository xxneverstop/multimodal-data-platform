package com.honortech.dataplatform.sessionimport.service;

import com.honortech.dataplatform.sessionimport.dto.SessionImportRequestContext;
import com.honortech.dataplatform.sessionimport.dto.SessionImportResponse;

public interface SessionImportService {

    SessionImportResponse importSession(SessionImportRequestContext context);
}
