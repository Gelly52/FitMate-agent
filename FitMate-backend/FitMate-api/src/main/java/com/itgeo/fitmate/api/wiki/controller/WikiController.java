package com.itgeo.fitmate.api.wiki.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import com.itgeo.fitmate.api.wiki.dto.WikiCompileJobItem;
import com.itgeo.fitmate.api.wiki.dto.WikiPageItem;
import com.itgeo.fitmate.api.wiki.dto.WikiSpaceItem;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiSpace;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiCompileJobMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiPageMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiSpaceMapper;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wiki 管理 API。
 *
 * 提供空间列表、页面列表、页面详情、编译任务查询与手动重建入口。
 */
@RestController
@RequestMapping("/wiki")
@RequiredArgsConstructor
public class WikiController {

    private final WikiSpaceMapper spaceMapper;
    private final WikiPageMapper pageMapper;
    private final WikiCompileJobMapper compileJobMapper;
    private final WikiCompileService wikiCompileService;

    /**
     * 查询可见的 Wiki 空间列表（GLOBAL + 当前用户 USER）。
     */
    @GetMapping("/spaces")
    public List<WikiSpaceItem> listSpaces(@RequestParam(required = false) Long userId) {
        LambdaQueryWrapper<WikiSpace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WikiSpace::getScopeType, "GLOBAL");
        if (userId != null) {
            wrapper.or().eq(WikiSpace::getScopeType, "USER").eq(WikiSpace::getOwnerUserId, userId);
        }
        return spaceMapper.selectList(wrapper).stream()
                .map(this::toSpaceItem)
                .collect(Collectors.toList());
    }

    /**
     * 查询某空间下的页面列表。
     */
    @GetMapping("/spaces/{spaceId}/pages")
    public List<WikiPageItem> listPages(@PathVariable Long spaceId,
                                        @RequestParam(required = false) String pageType) {
        LambdaQueryWrapper<WikiPage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WikiPage::getSpaceId, spaceId);
        if (pageType != null) {
            wrapper.eq(WikiPage::getPageType, pageType);
        }
        wrapper.orderByDesc(WikiPage::getUpdatedAt);
        return pageMapper.selectList(wrapper).stream()
                .map(this::toPageItem)
                .collect(Collectors.toList());
    }

    /**
     * 查询单个页面详情。
     */
    @GetMapping("/pages/{pageId}")
    public WikiPageItem getPage(@PathVariable Long pageId) {
        WikiPage page = pageMapper.selectById(pageId);
        return page == null ? null : toPageItem(page);
    }

    /**
     * 查询编译任务状态。
     */
    @GetMapping("/compile/{jobId}")
    public WikiCompileJobItem getCompileJob(@PathVariable Long jobId) {
        WikiCompileJob job = compileJobMapper.selectById(jobId);
        return job == null ? null : toJobItem(job);
    }

    /**
     * 手动触发重新编译（同步执行）。
     */
    @PostMapping("/rebuild/{jobId}")
    public String recompile(@PathVariable Long jobId) {
        wikiCompileService.executeCompile(jobId);
        return "已触发重新编译";
    }

    private WikiSpaceItem toSpaceItem(WikiSpace space) {
        WikiSpaceItem item = new WikiSpaceItem();
        item.setId(space.getId());
        item.setScopeType(space.getScopeType());
        item.setOwnerUserId(space.getOwnerUserId());
        item.setTitle(space.getTitle());
        item.setDescription(space.getDescription());
        item.setStatus(space.getStatus());
        return item;
    }

    private WikiPageItem toPageItem(WikiPage page) {
        WikiPageItem item = new WikiPageItem();
        item.setId(page.getId());
        item.setSpaceId(page.getSpaceId());
        item.setPageType(page.getPageType());
        item.setTitle(page.getTitle());
        item.setSlug(page.getSlug());
        item.setContentMd(page.getContentMd());
        item.setCharCount(page.getCharCount());
        item.setStatus(page.getStatus());
        item.setCompiledAt(page.getCompiledAt() == null ? null : page.getCompiledAt().toString());
        return item;
    }

    private WikiCompileJobItem toJobItem(WikiCompileJob job) {
        WikiCompileJobItem item = new WikiCompileJobItem();
        item.setId(job.getId());
        item.setSpaceId(job.getSpaceId());
        item.setTriggerType(job.getTriggerType());
        item.setSourceDocId(job.getSourceDocId());
        item.setStatus(job.getStatus());
        item.setErrorMessage(job.getErrorMessage());
        item.setStartedAt(job.getStartedAt() == null ? null : job.getStartedAt().toString());
        item.setFinishedAt(job.getFinishedAt() == null ? null : job.getFinishedAt().toString());
        return item;
    }
}
