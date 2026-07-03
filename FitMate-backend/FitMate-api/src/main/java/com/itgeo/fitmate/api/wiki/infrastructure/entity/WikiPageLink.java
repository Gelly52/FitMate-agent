package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_page_link")
public class WikiPageLink {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromPageId;
    private Long toPageId;
    private String linkText;
    private LocalDateTime createdAt;
}
