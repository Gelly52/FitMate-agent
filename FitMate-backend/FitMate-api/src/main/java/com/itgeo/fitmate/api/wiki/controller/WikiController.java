package com.itgeo.fitmate.api.wiki.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
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
import com.itgeo.fitmate.common.response.LeeResult;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public List<WikiSpaceItem> listSpaces() {
        Long userId = UserContextHolder.getRequired().getUserId();
        LambdaQueryWrapper<WikiSpace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WikiSpace::getScopeType, "GLOBAL");
        wrapper.or().eq(WikiSpace::getScopeType, "USER").eq(WikiSpace::getOwnerUserId, userId);
        return spaceMapper.selectList(wrapper).stream()
                .map(this::toSpaceItem)
                .collect(Collectors.toList());
    }

    /**
     * 查询某空间下的页面列表。
     * <p>
     * 校验当前用户有权访问该空间（GLOBAL 或自己的 USER space），无权访问时返回空列表。
     */
    @GetMapping("/spaces/{spaceId}/pages")
    public List<WikiPageItem> listPages(@PathVariable Long spaceId,
                                        @RequestParam(required = false) String pageType) {
        Long userId = UserContextHolder.getRequired().getUserId();
        if (resolveAccessibleSpace(userId, spaceId) == null) {
            return Collections.emptyList();
        }
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
     * <p>
     * 校验页面所属空间的归属，无权访问时返回 null。
     */
    @GetMapping("/pages/{pageId}")
    public WikiPageItem getPage(@PathVariable Long pageId) {
        Long userId = UserContextHolder.getRequired().getUserId();
        WikiPage page = pageMapper.selectById(pageId);
        if (page == null) {
            return null;
        }
        if (resolveAccessibleSpace(userId, page.getSpaceId()) == null) {
            return null;
        }
        return toPageItem(page);
    }

    /**
     * 按 slug 查询单个页面详情（供前端 wikilink 跳转使用）。
     * <p>
     * 校验空间归属，无权访问时返回 null。
     */
    @GetMapping("/spaces/{spaceId}/pages/{slug}")
    public WikiPageItem getPageBySlug(@PathVariable Long spaceId,
                                      @PathVariable String slug) {
        Long userId = UserContextHolder.getRequired().getUserId();
        if (resolveAccessibleSpace(userId, spaceId) == null) {
            return null;
        }
        WikiPage page = pageMapper.selectOne(
                new LambdaQueryWrapper<WikiPage>()
                        .eq(WikiPage::getSpaceId, spaceId)
                        .eq(WikiPage::getSlug, slug));
        return page == null ? null : toPageItem(page);
    }

    /**
     * 删除指定 Wiki 页面（校验空间归属后清理向量/关键词索引）。
     */
    @DeleteMapping("/pages/{pageId}")
    public LeeResult deletePage(@PathVariable Long pageId) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            wikiCompileService.deletePage(userId, pageId);
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            return LeeResult.errorException("Wiki 页面删除失败");
        }
    }

    /**
     * 查询编译任务状态。
     * <p>
     * 校验任务所属空间的归属，无权访问时返回 null。
     */
    @GetMapping("/compile/{jobId}")
    public WikiCompileJobItem getCompileJob(@PathVariable Long jobId) {
        Long userId = UserContextHolder.getRequired().getUserId();
        WikiCompileJob job = compileJobMapper.selectById(jobId);
        if (job == null) {
            return null;
        }
        if (resolveAccessibleSpace(userId, job.getSpaceId()) == null) {
            return null;
        }
        return toJobItem(job);
    }

    /**
     * 手动触发重新编译（同步执行）。
     * <p>
     * 校验任务所属空间的归属，无权访问时拒绝执行。
     */
    @PostMapping("/rebuild/{jobId}")
    public String recompile(@PathVariable Long jobId) {
        Long userId = UserContextHolder.getRequired().getUserId();
        WikiCompileJob job = compileJobMapper.selectById(jobId);
        if (job == null || resolveAccessibleSpace(userId, job.getSpaceId()) == null) {
            return "无权操作或任务不存在";
        }
        wikiCompileService.executeCompile(jobId);
        return "已触发重新编译";
    }

    /**
     * 校验当前用户是否有权访问指定空间。
     * <p>
     * GLOBAL 空间所有登录用户可访问；USER 空间仅 owner 可访问。
     *
     * @param userId  当前登录用户ID
     * @param spaceId 空间ID
     * @return 空间实体；不存在或无权访问返回 null
     */
    private WikiSpace resolveAccessibleSpace(Long userId, Long spaceId) {
        if (userId == null || spaceId == null) {
            return null;
        }
        WikiSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            return null;
        }
        if ("GLOBAL".equals(space.getScopeType())) {
            return space;
        }
        if ("USER".equals(space.getScopeType()) && userId.equals(space.getOwnerUserId())) {
            return space;
        }
        return null;
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
        item.setUpdatedAt(page.getUpdatedAt() == null ? null : page.getUpdatedAt().toString());
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
