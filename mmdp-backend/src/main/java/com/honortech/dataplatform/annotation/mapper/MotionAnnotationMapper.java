package com.honortech.dataplatform.annotation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.honortech.dataplatform.annotation.entity.MotionAnnotation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动作标注 Mapper
 */
@Mapper
public interface MotionAnnotationMapper extends BaseMapper<MotionAnnotation> {
}
