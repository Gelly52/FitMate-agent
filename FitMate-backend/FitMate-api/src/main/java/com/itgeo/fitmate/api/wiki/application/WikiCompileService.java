package com.itgeo.fitmate.api.wiki.application;

import com.itgeo.fitmate.api.wiki.infrastructure.entity.WikiCompileJob;
import java.util.Optional;

/**
 * Wiki 编译服务。
 *
 * 负责将原始资料通过 LLM 编译为结构化 wiki 页面（INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY 等），
 * 并同步到 MySQL 与 Redis 向量/关键词索引。
 *
 * 编译流程为异步执行（@Async），通过 t_wiki_compile_job 跟踪状态。
 */
public interface WikiCompileService {

    /**
     * 投递一个编译任务（异步执行）。
     *
     * @param spaceId     目标空间
     * @param sourceDocId 源文档 ID
     * @param triggerBy   触发人 userId
     * @return 创建的 compile job
     */
    WikiCompileJob submitCompileJob(Long spaceId, Long sourceDocId, Long triggerBy);

    /**
     * 同步执行编译（供异步 runner 调用）。
     *
     * @param jobId compile job 主键
     */
    void executeCompile(Long jobId);

    /**
     * 查询任务状态。
     *
     * @param jobId compile job 主键
     * @return 任务实体（不存在返回 empty）
     */
    Optional<WikiCompileJob> getJob(Long jobId);

    /**
     * 获取或创建用户的 USER space（不存在则创建）。
     *
     * @param userId 用户主键
     * @return space 主键
     */
    Long getOrCreateUserSpace(Long userId);

    /**
     * 删除指定 Wiki 页面及其向量/关键词索引。
     * <p>
     * 删除前校验页面所属空间的 ownerUserId 与传入 userId 匹配，防止越权删除。
     *
     * @param userId 当前用户主键（用于归属校验）
     * @param pageId 待删除 Wiki 页面主键
     */
    void deletePage(Long userId, Long pageId);

    /**
     * 清理超过保留期限的 Wiki 页面（系统定时任务调用）。
     *
     * @return 实际清理的页面数量
     */
    int cleanupExpiredPages();
}
