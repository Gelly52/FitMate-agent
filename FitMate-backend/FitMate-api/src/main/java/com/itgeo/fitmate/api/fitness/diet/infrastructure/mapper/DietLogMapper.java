package com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DietLogMapper extends BaseMapper<DietLog> {
}
