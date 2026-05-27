package com.honortech.dataplatform.session.service;

import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;
import com.honortech.dataplatform.session.dto.SessionResponse;
import com.honortech.dataplatform.session.entity.CollectionSession;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CollectionSessionService {

    SessionResponse importSession(Long taskId, MultipartFile manifestFile, List<MultipartFile> dataFiles);

    List<SessionResponse> listByTaskId(Long taskId);

    List<SessionResponse> listAll();

    CollectionSession getBySessionId(String sessionId);

    SessionPlaybackResponse getPlaybackData(String sessionId);
}
