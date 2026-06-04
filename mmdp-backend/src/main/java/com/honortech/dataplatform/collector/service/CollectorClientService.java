package com.honortech.dataplatform.collector.service;

import com.honortech.dataplatform.collector.entity.CollectorClient;

public interface CollectorClientService {

    CollectorClient resolveByCode(String clientCode);
}
