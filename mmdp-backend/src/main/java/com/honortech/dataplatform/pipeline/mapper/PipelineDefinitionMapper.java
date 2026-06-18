package com.honortech.dataplatform.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.honortech.dataplatform.pipeline.entity.PipelineDefinition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PipelineDefinitionMapper extends BaseMapper<PipelineDefinition> {
}
