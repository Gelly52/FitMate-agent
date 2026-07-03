package com.itgeo.fitmate.api.wiki.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("t_wiki_page")
public class WikiPage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long spaceId;
    private String pageType;        // INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG/PROFILE
    private String title;
    private String slug;
    private String contentMd;
    private String contentHash;
    private String frontmatterJson;
    private Integer charCount;
    private String status;          // DRAFT / PUBLISHED
    private Long sourceDocId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime compiledAt;
}
