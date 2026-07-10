package com.itgeo.fitmate.api.wiki.controller;

import com.itgeo.fitmate.api.auth.application.AuthenticatedUserContext;
import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.wiki.application.WikiCompileService;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiPage;
import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiSpace;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiCompileJobMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiPageMapper;
import com.itgeo.fitmate.api.wiki.infrastructure.mapper.WikiSpaceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WikiController 多用户隔离测试。
 * <p>
 * 重点覆盖 listPages/getPage/getPageBySlug/getCompileJob/recompile 的空间归属校验，
 * 防止 IDOR 越权读取或操作其他用户的私人 Wiki。
 */
@ExtendWith(MockitoExtension.class)
class WikiControllerTest {

    @Mock
    private WikiSpaceMapper spaceMapper;
    @Mock
    private WikiPageMapper pageMapper;
    @Mock
    private WikiCompileJobMapper compileJobMapper;
    @Mock
    private WikiCompileService wikiCompileService;

    @InjectMocks
    private WikiController wikiController;

    private static final Long USER_A_ID = 1001L;
    private static final Long USER_B_ID = 1002L;
    private static final Long GLOBAL_SPACE_ID = 7001L;
    private static final Long USER_A_SPACE_ID = 7002L;
    private static final Long USER_B_SPACE_ID = 7003L;
    private static final Long PAGE_ID = 8001L;
    private static final Long JOB_ID = 8002L;

    private MockedStatic<UserContextHolder> holderMock;

    @BeforeEach
    void setUp() {
        holderMock = org.mockito.Mockito.mockStatic(UserContextHolder.class);
    }

    @AfterEach
    void tearDown() {
        holderMock.close();
    }

    private void loginAs(Long userId) {
        AuthenticatedUserContext ctx = new AuthenticatedUserContext();
        ctx.setUserId(userId);
        holderMock.when(UserContextHolder::getRequired).thenReturn(ctx);
    }

    private WikiSpace globalSpace() {
        WikiSpace s = new WikiSpace();
        s.setId(GLOBAL_SPACE_ID);
        s.setScopeType("GLOBAL");
        s.setOwnerUserId(null);
        s.setTitle("Global Wiki");
        return s;
    }

    private WikiSpace userSpace(Long spaceId, Long ownerUserId) {
        WikiSpace s = new WikiSpace();
        s.setId(spaceId);
        s.setScopeType("USER");
        s.setOwnerUserId(ownerUserId);
        s.setTitle("User Wiki " + ownerUserId);
        return s;
    }

    private WikiPage pageOf(Long pageId, Long spaceId) {
        WikiPage p = new WikiPage();
        p.setId(pageId);
        p.setSpaceId(spaceId);
        p.setPageType("ENTITY");
        p.setTitle("page " + pageId);
        p.setSlug("slug-" + pageId);
        p.setContentMd("content " + pageId);
        return p;
    }

    private WikiCompileJob jobOf(Long jobId, Long spaceId, Long createdByUserId) {
        WikiCompileJob j = new WikiCompileJob();
        j.setId(jobId);
        j.setSpaceId(spaceId);
        j.setCreatedByUserId(createdByUserId);
        j.setStatus("SUCCESS");
        return j;
    }

    // ==================== listPages ====================

    @Test
    void listPages_globalSpace_shouldReturnPages() {
        loginAs(USER_A_ID);
        when(spaceMapper.selectById(GLOBAL_SPACE_ID)).thenReturn(globalSpace());
        when(pageMapper.selectList(any())).thenReturn(List.of(pageOf(PAGE_ID, GLOBAL_SPACE_ID)));

        var result = wikiController.listPages(GLOBAL_SPACE_ID, null);

        assertEquals(1, result.size());
        assertEquals(PAGE_ID, result.get(0).getId());
    }

    @Test
    void listPages_ownerUserSpace_shouldReturnPages() {
        loginAs(USER_A_ID);
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));
        when(pageMapper.selectList(any())).thenReturn(List.of(pageOf(PAGE_ID, USER_A_SPACE_ID)));

        var result = wikiController.listPages(USER_A_SPACE_ID, null);

        assertEquals(1, result.size());
    }

    /**
     * IDOR 场景：USER_B 尝试列出 USER_A 私人空间的页面，应返回空列表且不查询 page 表。
     */
    @Test
    void listPages_crossUserSpace_shouldReturnEmptyAndNotQueryPages() {
        loginAs(USER_B_ID);
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));

        var result = wikiController.listPages(USER_A_SPACE_ID, null);

        assertTrue(result.isEmpty(), "跨用户列出页面必须返回空");
        verify(pageMapper, never()).selectList(any());
    }

    @Test
    void listPages_spaceNotExists_shouldReturnEmpty() {
        loginAs(USER_A_ID);
        when(spaceMapper.selectById(anyLong())).thenReturn(null);

        var result = wikiController.listPages(9999L, null);

        assertTrue(result.isEmpty());
        verify(pageMapper, never()).selectList(any());
    }

    // ==================== getPage ====================

    @Test
    void getPage_ownerUser_shouldReturnPage() {
        loginAs(USER_A_ID);
        when(pageMapper.selectById(PAGE_ID)).thenReturn(pageOf(PAGE_ID, USER_A_SPACE_ID));
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));

        var result = wikiController.getPage(PAGE_ID);

        assertNotNull(result);
        assertEquals(PAGE_ID, result.getId());
    }

    @Test
    void getPage_globalSpace_shouldReturnPage() {
        loginAs(USER_A_ID);
        when(pageMapper.selectById(PAGE_ID)).thenReturn(pageOf(PAGE_ID, GLOBAL_SPACE_ID));
        when(spaceMapper.selectById(GLOBAL_SPACE_ID)).thenReturn(globalSpace());

        var result = wikiController.getPage(PAGE_ID);

        assertNotNull(result);
        assertEquals(PAGE_ID, result.getId());
    }

    /**
     * IDOR 场景：USER_B 通过枚举 pageId 读取 USER_A 私人空间的页面，应返回 null。
     */
    @Test
    void getPage_crossUser_shouldReturnNull() {
        loginAs(USER_B_ID);
        when(pageMapper.selectById(PAGE_ID)).thenReturn(pageOf(PAGE_ID, USER_A_SPACE_ID));
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));

        var result = wikiController.getPage(PAGE_ID);

        assertNull(result, "跨用户读取页面必须返回 null");
    }

    @Test
    void getPage_pageNotExists_shouldReturnNull() {
        loginAs(USER_A_ID);
        when(pageMapper.selectById(anyLong())).thenReturn(null);

        var result = wikiController.getPage(9999L);

        assertNull(result);
        verify(spaceMapper, never()).selectById(anyLong());
    }

    // ==================== getPageBySlug ====================

    @Test
    void getPageBySlug_ownerUser_shouldReturnPage() {
        loginAs(USER_A_ID);
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));
        when(pageMapper.selectOne(any())).thenReturn(pageOf(PAGE_ID, USER_A_SPACE_ID));

        var result = wikiController.getPageBySlug(USER_A_SPACE_ID, "slug-1");

        assertNotNull(result);
        assertEquals(PAGE_ID, result.getId());
    }

    /**
     * IDOR 场景：USER_B 通过 spaceId+slug 读取 USER_A 私人空间页面，应返回 null。
     */
    @Test
    void getPageBySlug_crossUser_shouldReturnNull() {
        loginAs(USER_B_ID);
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));

        var result = wikiController.getPageBySlug(USER_A_SPACE_ID, "slug-1");

        assertNull(result);
        verify(pageMapper, never()).selectOne(any());
    }

    // ==================== getCompileJob ====================

    @Test
    void getCompileJob_ownerUser_shouldReturnJob() {
        loginAs(USER_A_ID);
        when(compileJobMapper.selectById(JOB_ID)).thenReturn(jobOf(JOB_ID, USER_A_SPACE_ID, USER_A_ID));
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));

        var result = wikiController.getCompileJob(JOB_ID);

        assertNotNull(result);
        assertEquals(JOB_ID, result.getId());
    }

    /**
     * IDOR 场景：USER_B 查询 USER_A 的编译任务详情，应返回 null。
     */
    @Test
    void getCompileJob_crossUser_shouldReturnNull() {
        loginAs(USER_B_ID);
        when(compileJobMapper.selectById(JOB_ID)).thenReturn(jobOf(JOB_ID, USER_A_SPACE_ID, USER_A_ID));
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));

        var result = wikiController.getCompileJob(JOB_ID);

        assertNull(result);
    }

    // ==================== recompile ====================

    @Test
    void recompile_ownerUser_shouldExecute() {
        loginAs(USER_A_ID);
        when(compileJobMapper.selectById(JOB_ID)).thenReturn(jobOf(JOB_ID, USER_A_SPACE_ID, USER_A_ID));
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));

        String result = wikiController.recompile(JOB_ID);

        assertEquals("已触发重新编译", result);
        verify(wikiCompileService, times(1)).executeCompile(JOB_ID);
    }

    @Test
    void recompile_globalSpace_shouldExecute() {
        loginAs(USER_A_ID);
        when(compileJobMapper.selectById(JOB_ID)).thenReturn(jobOf(JOB_ID, GLOBAL_SPACE_ID, USER_A_ID));
        when(spaceMapper.selectById(GLOBAL_SPACE_ID)).thenReturn(globalSpace());

        String result = wikiController.recompile(JOB_ID);

        assertEquals("已触发重新编译", result);
        verify(wikiCompileService, times(1)).executeCompile(JOB_ID);
    }

    /**
     * IDOR 场景：USER_B 触发 USER_A 的编译任务，应被拒绝且不调用 executeCompile。
     */
    @Test
    void recompile_crossUser_shouldBeDeniedAndNotExecute() {
        loginAs(USER_B_ID);
        when(compileJobMapper.selectById(JOB_ID)).thenReturn(jobOf(JOB_ID, USER_A_SPACE_ID, USER_A_ID));
        when(spaceMapper.selectById(USER_A_SPACE_ID)).thenReturn(userSpace(USER_A_SPACE_ID, USER_A_ID));

        String result = wikiController.recompile(JOB_ID);

        assertTrue(result.contains("无权") || result.contains("权限") || result.contains("拒绝"),
                "跨用户触发编译应返回无权提示: " + result);
        verify(wikiCompileService, never()).executeCompile(anyLong());
    }

    @Test
    void recompile_jobNotExists_shouldBeDenied() {
        loginAs(USER_A_ID);
        when(compileJobMapper.selectById(anyLong())).thenReturn(null);

        String result = wikiController.recompile(9999L);

        assertTrue(result.contains("无权") || result.contains("不存在") || result.contains("权限"),
                "不存在的 job 应返回提示: " + result);
        verify(wikiCompileService, never()).executeCompile(anyLong());
    }
}
