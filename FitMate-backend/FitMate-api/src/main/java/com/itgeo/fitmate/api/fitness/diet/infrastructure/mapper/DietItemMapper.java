package com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DietItemMapper extends BaseMapper<DietItem> {
}
