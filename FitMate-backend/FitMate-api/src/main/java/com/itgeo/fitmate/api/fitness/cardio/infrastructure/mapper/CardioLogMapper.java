package com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 有氧训练日志 Mapper。
 */
@Mapper
public interface CardioLogMapper extends BaseMapper<CardioLog> {
}
