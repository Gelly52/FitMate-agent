package com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 心率记录 Mapper。
 */
@Mapper
public interface HeartRateMapper extends BaseMapper<HeartRate> {
}
