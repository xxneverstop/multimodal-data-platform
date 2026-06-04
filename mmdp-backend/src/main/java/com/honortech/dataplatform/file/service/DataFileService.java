package com.honortech.dataplatform.file.service;

import com.honortech.dataplatform.file.dto.CompleteDirectUploadRequest;
import com.honortech.dataplatform.file.dto.DataFileResponse;
import com.honortech.dataplatform.file.dto.FileUploadResponse;
import com.honortech.dataplatform.file.dto.InitiateDirectUploadRequest;
import com.honortech.dataplatform.file.dto.InitiateDirectUploadResponse;
import com.honortech.dataplatform.file.entity.DataFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DataFileService {

    /** @deprecated use {@link #uploadTaskFiles(Long, Long, List, MultipartFile)} */
    @Deprecated
    FileUploadResponse uploadTaskFile(Long taskId, MultipartFile file, String assetTypeValue);

    FileUploadResponse uploadTaskFiles(Long taskId, Long sessionId, List<MultipartFile> files, MultipartFile archive);

    InitiateDirectUploadResponse initiateDirectUpload(Long taskId, InitiateDirectUploadRequest request);

    DataFileResponse completeDirectUpload(CompleteDirectUploadRequest request);

    DataFile getFile(Long fileId);

    List<DataFile> listFilesByTaskId(Long taskId);
}
