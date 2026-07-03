package com.itgeo.fitmate.api.wiki.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WikiPageMapper extends BaseMapper<WikiPage> {
}
