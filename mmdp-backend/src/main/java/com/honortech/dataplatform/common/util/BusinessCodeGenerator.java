package com.honortech.dataplatform.common.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.honortech.dataplatform.session.entity.CollectionSession;
import com.honortech.dataplatform.session.mapper.CollectionSessionMapper;
import com.honortech.dataplatform.task.entity.AcquisitionTask;
import com.honortech.dataplatform.task.mapper.AcquisitionTaskMapper;
import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 业务编码生成器，生成格式：{PREFIX}-{yyyyMMdd}-{4位序号}
 * 如：TASK-20260602-0001、SESS-20260602-0001
 */
@Component
public class BusinessCodeGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DecimalFormat SEQ_FMT = new DecimalFormat("0000");

    private final AcquisitionTaskMapper taskMapper;
    private final CollectionSessionMapper sessionMapper;

    public BusinessCodeGenerator(AcquisitionTaskMapper taskMapper, CollectionSessionMapper sessionMapper) {
        this.taskMapper = taskMapper;
        this.sessionMapper = sessionMapper;
    }

    /**
     * 生成下一个业务编码
     * @param prefix 编码前缀，如 "TASK"、"SESS"
     */
    public synchronized String next(String prefix) {
        String dateKey = LocalDate.now().format(DATE_FMT);
        String likePattern = prefix + "-" + dateKey + "-%";

        int nextSeq = switch (prefix) {
            case "TASK" -> {
                int maxSeq = taskMapper.selectList(
                        new LambdaQueryWrapper<AcquisitionTask>()
                                .likeRight(AcquisitionTask::getTaskCode, prefix + "-" + dateKey + "-")
                                .orderByDesc(AcquisitionTask::getTaskCode)
                                .last("LIMIT 1")
                ).stream()
                        .map(AcquisitionTask::getTaskCode)
                        .filter(code -> code != null && code.length() >= prefix.length() + 14)
                        .map(code -> code.substring(code.length() - 4))
                        .mapToInt(Integer::parseInt)
                        .max()
                        .orElse(0);
                yield maxSeq + 1;
            }
            case "SESS" -> {
                int maxSeq = sessionMapper.selectList(
                        new LambdaQueryWrapper<CollectionSession>()
                                .likeRight(CollectionSession::getSessionCode, prefix + "-" + dateKey + "-")
                                .orderByDesc(CollectionSession::getSessionCode)
                                .last("LIMIT 1")
                ).stream()
                        .map(CollectionSession::getSessionCode)
                        .filter(code -> code != null && code.length() >= prefix.length() + 14)
                        .map(code -> code.substring(code.length() - 4))
                        .mapToInt(Integer::parseInt)
                        .max()
                        .orElse(0);
                yield maxSeq + 1;
            }
            default -> 1;
        };

        return prefix + "-" + dateKey + "-" + SEQ_FMT.format(nextSeq);
    }
}
