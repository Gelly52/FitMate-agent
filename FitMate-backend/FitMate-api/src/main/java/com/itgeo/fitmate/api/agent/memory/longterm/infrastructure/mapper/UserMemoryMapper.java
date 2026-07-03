package com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.agent.memory.longterm.infrastructure.entity.UserMemory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemory> {
}
