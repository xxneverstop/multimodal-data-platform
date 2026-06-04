package com.honortech.dataplatform.collector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.collector.entity.CollectorClient;
import com.honortech.dataplatform.collector.mapper.CollectorClientMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class CollectorClientServiceImpl implements CollectorClientService {

    private final CollectorClientMapper collectorClientMapper;

    public CollectorClientServiceImpl(CollectorClientMapper collectorClientMapper) {
        this.collectorClientMapper = collectorClientMapper;
    }

    @Override
    public CollectorClient resolveByCode(String clientCode) {
        if (clientCode == null || clientCode.isBlank()) {
            return null;
        }
        CollectorClient existing = collectorClientMapper.selectOne(new LambdaQueryWrapper<CollectorClient>()
                .eq(CollectorClient::getClientCode, clientCode.trim()));
        if (existing != null) {
            return existing;
        }
        CollectorClient client = new CollectorClient();
        client.setClientCode(clientCode.trim());
        client.setClientName(clientCode.trim());
        client.setStatus("ACTIVE");
        client.setCreatedAt(LocalDateTime.now());
        client.setUpdatedAt(LocalDateTime.now());
        collectorClientMapper.insert(client);
        return client;
    }
}
