-- FitMate 数据库初始化脚本
-- 目标数据库来自：mcp-server/src/main/resources/application-dev.yml
-- 数据库名：fitmate
-- 执行方式：可整体执行，也可按 Phase 分段执行

CREATE DATABASE IF NOT EXISTS `fitmate`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `fitmate`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- Phase 0：用户主数据与登录会话
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    `user_key` VARCHAR(64) NOT NULL COMMENT '外部稳定业务标识，兼容旧接口与测试账号',
    `username` VARCHAR(64) NOT NULL COMMENT '登录名',
    `password_hash` VARCHAR(255) DEFAULT NULL COMMENT '密码哈希，测试阶段可为空',
    `nickname` VARCHAR(100) DEFAULT NULL COMMENT '昵称',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0-禁用 1-正常',
    `last_login_at` DATETIME DEFAULT NULL COMMENT '最近登录时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_user_key` (`user_key`),
    UNIQUE KEY `uk_user_username` (`username`),
    UNIQUE KEY `uk_user_email` (`email`),
    KEY `idx_user_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户主数据表';

CREATE TABLE IF NOT EXISTS `t_user_login_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '登录会话主键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `refresh_token_hash` VARCHAR(128) NOT NULL COMMENT '刷新令牌哈希',
    `client_ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端 IP',
    `user_agent` VARCHAR(255) DEFAULT NULL COMMENT '客户端 UA',
    `expired_at` DATETIME NOT NULL COMMENT '过期时间',
    `revoked_at` DATETIME DEFAULT NULL COMMENT '撤销时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_login_session_token` (`refresh_token_hash`),
    KEY `idx_login_session_user` (`user_id`, `created_at`),
    KEY `idx_login_session_expired` (`expired_at`),
    CONSTRAINT `fk_login_session_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录会话表';

-- ============================================================
-- Phase 1：训练与身体指标
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_training_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '训练主表ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `training_date` DATE NOT NULL COMMENT '训练日期',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '训练摘要，供 recent 列表直接展示',
    `primary_muscle_group` VARCHAR(64) DEFAULT NULL COMMENT '主要训练肌群，可为空',
    `total_volume` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '当日总训练量，便于周统计聚合',
    `source` VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chat/import',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_training_user_date` (`user_id`, `training_date`),
    KEY `idx_training_user_date` (`user_id`, `training_date`),
    KEY `idx_training_created_at` (`created_at`),
    CONSTRAINT `fk_training_log_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='训练主表';

CREATE TABLE IF NOT EXISTS `t_training_exercise` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '训练动作明细ID',
    `training_log_id` BIGINT NOT NULL COMMENT '所属训练主表ID',
    `exercise_name` VARCHAR(100) NOT NULL COMMENT '动作名称',
    `sets` INT NOT NULL DEFAULT 1 COMMENT '组数',
    `reps` INT NOT NULL DEFAULT 1 COMMENT '每组次数',
    `weight` DECIMAL(8,2) NOT NULL DEFAULT 0 COMMENT '重量 kg',
    `order_num` INT NOT NULL DEFAULT 0 COMMENT '动作顺序',
    `estimated_muscle_group` VARCHAR(64) DEFAULT NULL COMMENT '可选的推断肌群',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_exercise_training_log_id` (`training_log_id`),
    CONSTRAINT `fk_exercise_training_log` FOREIGN KEY (`training_log_id`) REFERENCES `t_training_log` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='训练动作明细表';

CREATE TABLE IF NOT EXISTS `t_body_metrics` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '身体指标记录ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `record_date` DATE NOT NULL COMMENT '记录日期',
    `weight` DECIMAL(5,2) DEFAULT NULL COMMENT '体重 kg',
    `body_fat` DECIMAL(4,2) DEFAULT NULL COMMENT '体脂 %',
    `sleep_hours` DECIMAL(3,1) DEFAULT NULL COMMENT '睡眠时长 h',
    `fatigue_level` VARCHAR(16) DEFAULT NULL COMMENT '疲劳度：低/中/高',
    `note` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '近期变化摘要，供 recent 列表展示',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_metrics_user_date` (`user_id`, `record_date`),
    KEY `idx_metrics_user_date` (`user_id`, `record_date`),
    KEY `idx_metrics_created_at` (`created_at`),
    CONSTRAINT `fk_body_metrics_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='身体指标事实表';

-- ============================================================
-- Phase 2：聊天记录与会话模型
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '会话主键',
    `session_code` VARCHAR(64) NOT NULL COMMENT '对外会话标识',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `scene_type` VARCHAR(20) NOT NULL DEFAULT 'chat' COMMENT 'chat/rag/internet/agent',
    `title` VARCHAR(200) DEFAULT NULL COMMENT '会话标题，可由首条消息生成',
    `last_bot_msg_id` VARCHAR(64) DEFAULT NULL COMMENT '最近机器人消息ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chat_session_code` (`session_code`),
    KEY `idx_chat_session_user` (`user_id`, `updated_at`),
    KEY `idx_chat_session_scene` (`scene_type`, `updated_at`),
    CONSTRAINT `fk_chat_session_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天会话表';

CREATE TABLE IF NOT EXISTS `t_chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '消息主键',
    `session_id` BIGINT NOT NULL COMMENT '所属会话主键',
    `seq_no` INT NOT NULL DEFAULT 0 COMMENT '消息顺序',
    `role` VARCHAR(20) NOT NULL COMMENT 'user/assistant/system',
    `message_type` VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '消息类型',
    `source_type` VARCHAR(20) NOT NULL DEFAULT 'chat' COMMENT 'chat/rag/internet/agent',
    `content` LONGTEXT NOT NULL COMMENT '消息正文',
    `bot_msg_id` VARCHAR(64) DEFAULT NULL COMMENT '机器人消息ID',
    `sources_json` JSON DEFAULT NULL COMMENT '可选的知识来源列表',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_chat_message_session_seq` (`session_id`, `seq_no`),
    KEY `idx_chat_message_bot` (`bot_msg_id`),
    KEY `idx_chat_message_created` (`created_at`),
    CONSTRAINT `fk_chat_message_session` FOREIGN KEY (`session_id`) REFERENCES `t_chat_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';

-- ============================================================
-- Phase 3：RAG 元数据、配置与评测记录
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_rag_document` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文档主键',
    `user_id` BIGINT DEFAULT NULL COMMENT '上传用户主键，系统文档可为空',
    `file_name` VARCHAR(255) NOT NULL COMMENT '展示文件名',
    `source_count` INT NOT NULL DEFAULT 0 COMMENT '原始文档片段数',
    `file_hash` VARCHAR(64) DEFAULT NULL COMMENT '内容哈希，便于去重',
    `file_type` VARCHAR(50) DEFAULT NULL COMMENT 'txt/pdf/md/docx 等',
    `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小',
    `storage_uri` VARCHAR(500) DEFAULT NULL COMMENT '原文件存储位置',
    `chunk_count` INT NOT NULL DEFAULT 0 COMMENT '切片数量',
    `vector_status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/processing/completed/failed',
    `metadata_json` JSON DEFAULT NULL COMMENT '额外元数据',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rag_file_hash` (`file_hash`),
    KEY `idx_rag_document_user` (`user_id`, `created_at`),
    KEY `idx_rag_document_status` (`vector_status`, `updated_at`),
    CONSTRAINT `fk_rag_document_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 文档元数据表';

CREATE TABLE IF NOT EXISTS `t_rag_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置主键',
    `scope_type` VARCHAR(20) NOT NULL COMMENT 'global/user/document',
    `scope_id` BIGINT NOT NULL DEFAULT 0 COMMENT 'global 固定为 0；user 为 t_user.id；document 为 t_rag_document.id',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value_json` JSON NOT NULL COMMENT '配置值，推荐 JSON 保存',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '配置描述',
    `created_by_user_id` BIGINT DEFAULT NULL COMMENT '创建人用户主键',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rag_config_scope` (`scope_type`, `scope_id`, `config_key`),
    KEY `idx_rag_config_creator` (`created_by_user_id`),
    CONSTRAINT `fk_rag_config_creator` FOREIGN KEY (`created_by_user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 配置表';

CREATE TABLE IF NOT EXISTS `t_rag_benchmark_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '评测任务主键',
    `user_id` BIGINT DEFAULT NULL COMMENT '发起用户主键',
    `dataset_name` VARCHAR(100) DEFAULT NULL COMMENT '评测数据集名称',
    `question_count` INT NOT NULL DEFAULT 0 COMMENT '评测问题数',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
    `config_snapshot_json` JSON DEFAULT NULL COMMENT '评测时配置快照',
    `result_json` JSON DEFAULT NULL COMMENT '评测输出结果',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_benchmark_user` (`user_id`, `created_at`),
    KEY `idx_benchmark_status` (`status`, `updated_at`),
    CONSTRAINT `fk_benchmark_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='RAG 评测任务表';

-- ============================================================
-- Phase 4：Dashboard 快照与 Agent 审计
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_dashboard_summary` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '快照主键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `summary_date` DATE NOT NULL COMMENT '快照日期',
    `summary_type` VARCHAR(20) NOT NULL DEFAULT 'daily' COMMENT 'daily/weekly/monthly',
    `training_days` INT NOT NULL DEFAULT 0 COMMENT '周期内训练天数',
    `total_volume` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '周期总训练量',
    `avg_weight` DECIMAL(5,2) DEFAULT NULL COMMENT '平均体重',
    `weight_change` DECIMAL(5,2) DEFAULT NULL COMMENT '体重变化',
    `fatigue_level` VARCHAR(16) DEFAULT NULL COMMENT '疲劳概况',
    `result_summary_json` JSON DEFAULT NULL COMMENT '最近执行结果摘要',
    `report_content` LONGTEXT DEFAULT NULL COMMENT '报表正文或周报预览',
    `generated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dashboard_summary` (`user_id`, `summary_date`, `summary_type`),
    KEY `idx_dashboard_generated` (`generated_at`),
    CONSTRAINT `fk_dashboard_summary_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Dashboard 聚合快照表';

CREATE TABLE IF NOT EXISTS `t_agent_run` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Agent 运行主键',
    `user_id` BIGINT NOT NULL COMMENT '发起用户主键',
    `chat_session_id` BIGINT DEFAULT NULL COMMENT '关联聊天会话主键，可为空',
    `request_text` LONGTEXT DEFAULT NULL COMMENT '原始任务输入',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
    `result_json` JSON DEFAULT NULL COMMENT '执行结果摘要',
    `error_message` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `bot_msg_id` VARCHAR(64) NOT NULL COMMENT '机器人消息ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_agent_run_user_bot_msg` (`user_id`, `bot_msg_id`),
    KEY `idx_agent_run_user` (`user_id`, `created_at`),
    KEY `idx_agent_run_status` (`status`, `created_at`),
    KEY `idx_agent_run_session` (`chat_session_id`),
    CONSTRAINT `fk_agent_run_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`),
    CONSTRAINT `fk_agent_run_session` FOREIGN KEY (`chat_session_id`) REFERENCES `t_chat_session` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 运行主表';

CREATE TABLE IF NOT EXISTS `t_agent_step` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Agent 步骤主键',
    `agent_run_id` BIGINT NOT NULL COMMENT '所属 Agent 运行主键',
    `step_no` INT NOT NULL DEFAULT 0 COMMENT '步骤序号',
    `step_name` VARCHAR(100) NOT NULL COMMENT '步骤名称',
    `step_status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/running/success/failed',
    `event_type` VARCHAR(64) DEFAULT NULL COMMENT '动态事件类型',
    `tool_name` VARCHAR(100) DEFAULT NULL COMMENT '调用工具名称',
    `tool_call_id` VARCHAR(128) DEFAULT NULL COMMENT '工具调用ID',
    `iteration_no` INT DEFAULT NULL COMMENT 'Agent Loop 迭代轮次',
    `duration_ms` BIGINT DEFAULT NULL COMMENT '事件耗时毫秒数',
    `input_json` JSON DEFAULT NULL COMMENT '步骤输入',
    `output_json` JSON DEFAULT NULL COMMENT '步骤输出',
    `error_message` VARCHAR(500) DEFAULT NULL COMMENT '错误信息',
    `started_at` DATETIME DEFAULT NULL,
    `finished_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_agent_step_run` (`agent_run_id`, `step_no`),
    UNIQUE KEY `uk_agent_step_run_no` (`agent_run_id`, `step_no`),
    CONSTRAINT `fk_agent_step_run` FOREIGN KEY (`agent_run_id`) REFERENCES `t_agent_run` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 步骤表';

SET FOREIGN_KEY_CHECKS = 1;

-- 可选：初始化一个测试账号，便于功能联调
-- INSERT INTO `t_user` (`user_key`, `username`, `nickname`, `status`)
-- VALUES ('test-user-001', 'test_username_001', '测试用户', 1),
-- ('test-user-002', 'test_username_002', '测试用户2', 1);

-- ============================================================
-- Phase 5：LLM Wiki 知识库表
-- ============================================================

-- ============ LLM Wiki 知识库表 ============

CREATE TABLE IF NOT EXISTS `t_wiki_space` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '空间主键',
    `scope_type`      VARCHAR(20)  NOT NULL COMMENT 'GLOBAL / USER',
    `owner_user_id`   BIGINT       NULL     COMMENT 'GLOBAL=null; USER=t_user.id',
    `title`           VARCHAR(255) NOT NULL COMMENT '空间标题',
    `description`     VARCHAR(500) NULL     COMMENT '空间描述',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ARCHIVED',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_scope_owner` (`scope_type`, `owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 空间';

CREATE TABLE IF NOT EXISTS `t_wiki_page` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '页面主键',
    `space_id`         BIGINT       NOT NULL COMMENT '所属空间',
    `page_type`        VARCHAR(30)  NOT NULL COMMENT 'INDEX/ENTITY/CONCEPT/SYNTHESIS/SOURCE_SUMMARY/LOG/PROFILE',
    `title`            VARCHAR(255) NOT NULL COMMENT '页面标题',
    `slug`             VARCHAR(255) NOT NULL COMMENT 'URL 友好标识（空间内唯一）',
    `content_md`       LONGTEXT     NULL     COMMENT 'Markdown 正文',
    `content_hash`     VARCHAR(64)  NULL     COMMENT '内容哈希（变更检测）',
    `frontmatter_json` JSON         NULL     COMMENT 'YAML frontmatter',
    `char_count`       INT          NOT NULL DEFAULT 0,
    `status`           VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT / PUBLISHED',
    `source_doc_id`    BIGINT       NULL     COMMENT '源文档（SOURCE_SUMMARY 用）',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `compiled_at`      DATETIME     NULL     COMMENT '编译时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_space_slug` (`space_id`, `slug`),
    KEY `idx_space_type` (`space_id`, `page_type`),
    KEY `idx_content_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 页面';

CREATE TABLE IF NOT EXISTS `t_wiki_page_link` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `from_page_id`  BIGINT       NOT NULL COMMENT '源页面',
    `to_page_id`    BIGINT       NOT NULL COMMENT '目标页面',
    `link_text`     VARCHAR(255) NULL     COMMENT '链接显示文本',
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_from` (`from_page_id`),
    KEY `idx_to` (`to_page_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 页面 wikilink 关系';

CREATE TABLE IF NOT EXISTS `t_wiki_compile_job` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `space_id`            BIGINT      NOT NULL COMMENT '目标空间',
    `trigger_type`        VARCHAR(20) NOT NULL COMMENT 'DOC_UPLOAD/MANUAL/SCHEDULED/EVENT',
    `source_doc_id`       BIGINT      NULL     COMMENT '源文档',
    `status`              VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    `pages_touched_json`  JSON        NULL     COMMENT '本次触碰页面 ID 列表',
    `error_message`       TEXT        NULL,
    `started_at`          DATETIME    NULL,
    `finished_at`         DATETIME    NULL,
    `created_by_user_id`  BIGINT      NULL,
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_space_status` (`space_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 异步编译任务';

CREATE TABLE IF NOT EXISTS `t_wiki_log` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `space_id`      BIGINT       NOT NULL,
    `entry_type`    VARCHAR(20)  NOT NULL COMMENT 'INGEST/QUERY/LINT/COMPILE',
    `entry_summary` VARCHAR(500) NULL     COMMENT '日志摘要',
    `source_ref`    VARCHAR(255) NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_space_created` (`space_id`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Wiki 操作日志';

-- ============================================================
-- 用户偏好设置
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_user_preference` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '偏好主键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `preferences_json` JSON NOT NULL COMMENT '偏好设置 JSON，如 {"themeMode":"dark","accentColor":"blue"}',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_preference_user` (`user_id`),
    CONSTRAINT `fk_user_preference_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户偏好设置表';
