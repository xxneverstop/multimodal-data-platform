package com.honortech.dataplatform.file.service;

import com.honortech.dataplatform.file.dto.FileUploadResponse;
import com.honortech.dataplatform.file.entity.DataFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DataFileService {

    FileUploadResponse uploadTaskFile(Long taskId, MultipartFile file, String assetTypeValue);

    DataFile getFile(Long fileId);

    List<DataFile> listFilesByTaskId(Long taskId);
}
