package com.honortech.dataplatform.session.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.honortech.dataplatform.session.dto.SessionPlaybackResponse;
import com.honortech.dataplatform.session.dto.SessionListItemResponse;
import com.honortech.dataplatform.session.dto.SessionListQueryRequest;
import com.honortech.dataplatform.session.dto.SessionResponse;
import com.honortech.dataplatform.session.entity.CollectionSession;

import java.util.List;

public interface CollectionSessionService {

    List<SessionResponse> listByTaskId(Long taskId);

    List<SessionResponse> listAll();

    IPage<SessionListItemResponse> listPage(SessionListQueryRequest request);

    CollectionSession getBySessionId(String sessionId);

    SessionPlaybackResponse getPlaybackData(String sessionId);

    SessionPlaybackResponse getPlaybackData(String sessionId, Long jobId);

    boolean canPlay(String sessionId, Long jobId);
}
