# 数据记录工具（record tools）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为训练日志、有氧训练、身体指标、心率、饮食五类数据新增 Agent 可调用的 record（增改）+ 配套 query（查）工具，允许用户通过对话记录数据。

**Architecture:** 复用现有 `ToolExecutor` 接口 + `@Component` 自动注册 + `application.yml` 白名单机制。5 个 record 工具复用/新建 Service 层 upsert 逻辑（按 userId+date 唯一键），3 个配套 query 工具（cardio/heart_rate/diet）用于更新前获取既有记录。source 字段区分 `manual`（Controller）/ `chat`（Agent）。

**Tech Stack:** Java 21、Spring Boot 3.5.10、MyBatis-Plus 3.5.10.1（BaseMapper + LambdaQueryWrapper，无 IService）、Lombok（@Data/@AllArgsConstructor/@NoArgsConstructor/@ToString）、JUnit 5 + Mockito（`Mockito.mock()` 静态方法风格）、纯 JUnit Jupiter Assertions。

**Spec:** `docs/superpowers/specs/2026-07-03-data-record-tools-design.md`

**Build commands:**
- 后端编译：在 `FitMate-backend/` 目录下 `mvn clean compile`
- 后端测试：在 `FitMate-backend/` 目录下 `mvn -pl FitMate-api test`
- 单个测试类：`mvn -pl FitMate-api -Dtest=ClassName test`

**项目约定提醒:**
- 实体：`@Data @ToString @TableName("t_xxx")` + `@TableField` 显式标注下划线列名
- DTO：`@Data @AllArgsConstructor @NoArgsConstructor`
- Mapper：`extends BaseMapper<XxxEntity>`，无自定义方法
- Service：`@Service @Transactional(rollbackFor = Exception.class)`，`@Resource` 注入 Mapper
- 测试：`@Test` + `snake_case_scenario_outcome` 命名，`Mockito.mock()` 在 `@BeforeEach` 构造，纯 JUnit `assertEquals/verify`
- upsert 模式：`selectOne(LambdaQueryWrapper)` → 存在 `updateById` / 不存在 `insert`；子表先 `delete` 再逐条 `insert`

---

## Phase 1: 数据库与实体基础设施

### Task 1: DDL - 新增 3 张表 + t_body_metrics 扩展字段

**Files:**
- Modify: `FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql`（在文件末尾升级脚本区追加）

- [ ] **Step 1: 追加新表 DDL 与 ALTER 升级脚本**

在 `fitmate_init.sql` 文件末尾（当前最后一行 `-- ALTER TABLE t_wiki_page ...` 之后）追加以下内容：

```sql

-- ========== 数据记录工具扩展（2026-07-03）==========

-- Phase 1.1: 有氧训练日志表
CREATE TABLE IF NOT EXISTS `t_cardio_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '有氧训练ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `training_date` DATE NOT NULL COMMENT '训练日期',
    `cardio_type` VARCHAR(32) NOT NULL COMMENT '类型：running/cycling/swimming/rowing/jump_rope/other',
    `distance_km` DECIMAL(8,2) DEFAULT NULL COMMENT '距离 km',
    `duration_minutes` INT DEFAULT NULL COMMENT '时长 分钟',
    `avg_pace` VARCHAR(16) DEFAULT NULL COMMENT '配速，自动计算，格式 mm:ss/km',
    `avg_heart_rate` INT DEFAULT NULL COMMENT '平均心率',
    `calories_burned` INT DEFAULT NULL COMMENT '消耗卡路里',
    `note` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要展示文本',
    `source` VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chat/import',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_cardio_user_date_type` (`user_id`, `training_date`, `cardio_type`),
    KEY `idx_cardio_user_date` (`user_id`, `training_date`),
    CONSTRAINT `fk_cardio_log_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='有氧训练日志表';

-- Phase 1.2: 心率记录表
CREATE TABLE IF NOT EXISTS `t_heart_rate` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '心率记录ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `record_date` DATE NOT NULL COMMENT '记录日期',
    `resting_hr` INT DEFAULT NULL COMMENT '静息心率',
    `max_hr` INT DEFAULT NULL COMMENT '最大心率',
    `hrv` INT DEFAULT NULL COMMENT '心率变异性 ms',
    `note` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要展示文本',
    `source` VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chat/import',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hr_user_date` (`user_id`, `record_date`),
    KEY `idx_hr_user_date` (`user_id`, `record_date`),
    CONSTRAINT `fk_heart_rate_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心率记录表';

-- Phase 1.3: 饮食日志主表
CREATE TABLE IF NOT EXISTS `t_diet_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '饮食日志ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户主键',
    `record_date` DATE NOT NULL COMMENT '记录日期',
    `meal_type` VARCHAR(16) NOT NULL COMMENT '餐次：breakfast/lunch/dinner/snack',
    `total_calories` INT DEFAULT NULL COMMENT '总热量，自动汇总',
    `total_protein` DECIMAL(8,1) DEFAULT NULL COMMENT '总蛋白质 g',
    `total_carbs` DECIMAL(8,1) DEFAULT NULL COMMENT '总碳水 g',
    `total_fat` DECIMAL(8,1) DEFAULT NULL COMMENT '总脂肪 g',
    `note` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `summary` VARCHAR(500) DEFAULT NULL COMMENT '摘要展示文本',
    `source` VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT '来源：manual/chat/import',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_diet_user_date_meal` (`user_id`, `record_date`, `meal_type`),
    KEY `idx_diet_user_date` (`user_id`, `record_date`),
    CONSTRAINT `fk_diet_log_user` FOREIGN KEY (`user_id`) REFERENCES `t_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='饮食日志主表';

-- Phase 1.4: 饮食明细表
CREATE TABLE IF NOT EXISTS `t_diet_item` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '饮食明细ID',
    `diet_log_id` BIGINT NOT NULL COMMENT '所属饮食日志ID',
    `food_name` VARCHAR(100) NOT NULL COMMENT '食物名称',
    `portion` VARCHAR(50) DEFAULT NULL COMMENT '份量描述',
    `calories` INT DEFAULT NULL COMMENT '热量 kcal',
    `protein` DECIMAL(8,1) DEFAULT NULL COMMENT '蛋白质 g',
    `carbs` DECIMAL(8,1) DEFAULT NULL COMMENT '碳水 g',
    `fat` DECIMAL(8,1) DEFAULT NULL COMMENT '脂肪 g',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_diet_item_log` (`diet_log_id`),
    CONSTRAINT `fk_diet_item_log` FOREIGN KEY (`diet_log_id`) REFERENCES `t_diet_log` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='饮食明细表';

-- ========== 升级脚本（已有库执行）==========
-- 以下 ALTER 用于已存在的数据库升级，新建库无需执行（上方 CREATE TABLE 已含字段）
ALTER TABLE `t_body_metrics`
    ADD COLUMN `chest_girth` DECIMAL(6,1) DEFAULT NULL COMMENT '胸围 cm' AFTER `body_fat`,
    ADD COLUMN `waist_girth` DECIMAL(6,1) DEFAULT NULL COMMENT '腰围 cm' AFTER `chest_girth`,
    ADD COLUMN `hip_girth` DECIMAL(6,1) DEFAULT NULL COMMENT '臀围 cm' AFTER `waist_girth`,
    ADD COLUMN `arm_girth` DECIMAL(6,1) DEFAULT NULL COMMENT '臂围 cm' AFTER `hip_girth`,
    ADD COLUMN `thigh_girth` DECIMAL(6,1) DEFAULT NULL COMMENT '大腿围 cm' AFTER `arm_girth`;
```

- [ ] **Step 2: 提交**

```bash
git add FitMate-backend/FitMate-mcpServer/src/main/resources/sql/fitmate_init.sql
git commit -m "feat: add DDL for cardio/heart_rate/diet tables and body_metrics girth columns"
```

---

### Task 2: CardioLog 实体 + Mapper

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/infrastructure/entity/CardioLog.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/infrastructure/mapper/CardioLogMapper.java`

- [ ] **Step 1: 创建 CardioLog 实体**

```java
package com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 有氧训练日志实体。
 */
@Data
@ToString
@TableName("t_cardio_log")
public class CardioLog {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("training_date")
    private LocalDate trainingDate;
    @TableField("cardio_type")
    private String cardioType;
    @TableField("distance_km")
    private BigDecimal distanceKm;
    @TableField("duration_minutes")
    private Integer durationMinutes;
    @TableField("avg_pace")
    private String avgPace;
    @TableField("avg_heart_rate")
    private Integer avgHeartRate;
    @TableField("calories_burned")
    private Integer caloriesBurned;
    private String note;
    private String summary;
    private String source;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 CardioLogMapper**

```java
package com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 有氧训练日志 Mapper。
 */
@Mapper
public interface CardioLogMapper extends BaseMapper<CardioLog> {
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-api -am clean compile`（在 `FitMate-backend/` 目录下）
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/
git commit -m "feat: add CardioLog entity and mapper"
```

---

### Task 3: HeartRate 实体 + Mapper

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/infrastructure/entity/HeartRate.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/infrastructure/mapper/HeartRateMapper.java`

- [ ] **Step 1: 创建 HeartRate 实体**

```java
package com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 心率记录实体。
 */
@Data
@ToString
@TableName("t_heart_rate")
public class HeartRate {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("record_date")
    private LocalDate recordDate;
    @TableField("resting_hr")
    private Integer restingHr;
    @TableField("max_hr")
    private Integer maxHr;
    private Integer hrv;
    private String note;
    private String summary;
    private String source;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 HeartRateMapper**

```java
package com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import org.apache.ibatis.annotations.Mapper;

/**
 * 心率记录 Mapper。
 */
@Mapper
public interface HeartRateMapper extends BaseMapper<HeartRate> {
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/
git commit -m "feat: add HeartRate entity and mapper"
```

---

### Task 4: DietLog + DietItem 实体 + Mapper

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/infrastructure/entity/DietLog.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/infrastructure/entity/DietItem.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/infrastructure/mapper/DietLogMapper.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/infrastructure/mapper/DietItemMapper.java`

- [ ] **Step 1: 创建 DietLog 实体**

```java
package com.itgeo.fitmate.api.fitness.diet.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.ToString;

/**
 * 饮食日志主表实体。
 */
@Data
@ToString
@TableName("t_diet_log")
public class DietLog {
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("record_date")
    private LocalDate recordDate;
    @TableField("meal_type")
    private String mealType;
    @TableField("total_calories")
    private Integer totalCalories;
    @TableField("total_protein")
    private BigDecimal totalProtein;
    @TableField("total_carbs")
    private BigDecimal totalCarbs;
    @TableField("total_fat")
    private BigDecimal totalFat;
    private String note;
    private String summary;
    private String source;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 查询时关联的明细列表（非数据库字段）。 */
    @TableField(exist = false)
    private List<DietItem> items;
}
```

- [ ] **Step 2: 创建 DietItem 实体**

```java
package com.itgeo.fitmate.api.fitness.diet.infrastructure.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.ToString;

/**
 * 饮食明细实体。
 */
@Data
@ToString
@TableName("t_diet_item")
public class DietItem {
    private Long id;
    @TableField("diet_log_id")
    private Long dietLogId;
    @TableField("food_name")
    private String foodName;
    private String portion;
    private Integer calories;
    private BigDecimal protein;
    private BigDecimal carbs;
    private BigDecimal fat;
    @TableField("created_at")
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建 DietLogMapper 和 DietItemMapper**

```java
package com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DietLogMapper extends BaseMapper<DietLog> {
}
```

```java
package com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DietItemMapper extends BaseMapper<DietItem> {
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/
git commit -m "feat: add DietLog and DietItem entities and mappers"
```

---

### Task 5: BodyMetrics 实体扩展围度字段 + BodyMetricsLogRequest 扩展

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/infrastructure/entity/BodyMetrics.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/dto/BodyMetricsLogRequest.java`

- [ ] **Step 1: 在 BodyMetrics 实体 bodyFat 字段后追加 5 个围度字段**

在 `BodyMetrics.java` 中，`private BigDecimal bodyFat;` 之后插入：

```java
    @TableField("chest_girth")
    private BigDecimal chestGirth;
    @TableField("waist_girth")
    private BigDecimal waistGirth;
    @TableField("hip_girth")
    private BigDecimal hipGirth;
    @TableField("arm_girth")
    private BigDecimal armGirth;
    @TableField("thigh_girth")
    private BigDecimal thighGirth;
```

- [ ] **Step 2: 在 BodyMetricsLogRequest 的 bodyFat 字段后追加 5 个围度字段**

```java
    /** 胸围 cm。 */
    private BigDecimal chestGirth;
    /** 腰围 cm。 */
    private BigDecimal waistGirth;
    /** 臀围 cm。 */
    private BigDecimal hipGirth;
    /** 臂围 cm。 */
    private BigDecimal armGirth;
    /** 大腿围 cm。 */
    private BigDecimal thighGirth;
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/
git commit -m "feat: add girth fields to BodyMetrics entity and DTO"
```

---

## Phase 2: Service 层

### Task 6: CardioLogRequest DTO + CardioService + Impl（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/dto/CardioLogRequest.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/application/CardioService.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/application/impl/CardioServiceImpl.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/cardio/application/impl/CardioServiceImplTest.java`

- [ ] **Step 1: 创建 CardioLogRequest DTO**

```java
package com.itgeo.fitmate.api.fitness.cardio.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 有氧训练记录请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CardioLogRequest {
    /** 训练日期，格式 yyyy-MM-dd。 */
    private String date;
    /** 类型：running/cycling/swimming/rowing/jump_rope/other。 */
    private String cardioType;
    /** 距离 km。 */
    private BigDecimal distanceKm;
    /** 时长 分钟。 */
    private Integer durationMinutes;
    /** 平均心率。 */
    private Integer avgHeartRate;
    /** 备注。 */
    private String note;
}
```

- [ ] **Step 2: 创建 CardioService 接口**

```java
package com.itgeo.fitmate.api.fitness.cardio.application;

import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;

/**
 * 有氧训练记录服务。
 */
public interface CardioService {

    /**
     * 记录有氧训练（upsert，按 userId + date + cardioType 唯一键）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logCardio(Long userId, CardioLogRequest request, String source);
}
```

- [ ] **Step 3: 写失败测试**

创建 `CardioServiceImplTest.java`：

```java
package com.itgeo.fitmate.api.fitness.cardio.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.cardio.application.impl.CardioServiceImpl;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper.CardioLogMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CardioServiceImplTest {

    private CardioLogMapper cardioLogMapper;
    private CardioServiceImpl service;

    @BeforeEach
    void setUp() {
        cardioLogMapper = mock(CardioLogMapper.class);
        service = new CardioServiceImpl(cardioLogMapper);
    }

    @Test
    void logCardio_newRecord_insertsWithCalculatedPace() {
        when(cardioLogMapper.selectOne(any())).thenReturn(null);

        CardioLogRequest req = new CardioLogRequest(
                "2026-07-03", "running",
                new BigDecimal("5.0"), 30, 150, null);

        service.logCardio(1L, req, "chat");

        ArgumentCaptor<CardioLog> captor = ArgumentCaptor.forClass(CardioLog.class);
        verify(cardioLogMapper).insert(captor.capture());
        CardioLog saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals(LocalDate.of(2026, 7, 3), saved.getTrainingDate());
        assertEquals("running", saved.getCardioType());
        assertEquals("06:00/km", saved.getAvgPace());
        assertEquals("chat", saved.getSource());
        assertNotNull(saved.getSummary());
    }

    @Test
    void logCardio_existingRecord_updatesById() {
        CardioLog existing = new CardioLog();
        existing.setId(99L);
        when(cardioLogMapper.selectOne(any())).thenReturn(existing);

        CardioLogRequest req = new CardioLogRequest(
                "2026-07-03", "running",
                new BigDecimal("10.0"), 60, 155, null);

        service.logCardio(1L, req, "chat");

        verify(cardioLogMapper).updateById(existing);
        verify(cardioLogMapper, never()).insert(any());
        assertEquals("06:00/km", existing.getAvgPace());
        assertEquals("chat", existing.getSource());
    }

    @Test
    void logCardio_noDistanceNoPace_leftNull() {
        when(cardioLogMapper.selectOne(any())).thenReturn(null);

        CardioLogRequest req = new CardioLogRequest(
                "2026-07-03", "other", null, 30, null, null);

        service.logCardio(1L, req, "chat");

        ArgumentCaptor<CardioLog> captor = ArgumentCaptor.forClass(CardioLog.class);
        verify(cardioLogMapper).insert(captor.capture());
        assertNull(captor.getValue().getAvgPace());
    }

    @Test
    void logCardio_nullDistanceAndDuration_throws() {
        CardioLogRequest req = new CardioLogRequest(
                "2026-07-03", "running", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logCardio(1L, req, "chat"));
    }

    @Test
    void logCardio_invalidDate_throws() {
        CardioLogRequest req = new CardioLogRequest(
                "2026/07/03", "running", new BigDecimal("5"), 30, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logCardio(1L, req, "chat"));
    }
}
```

- [ ] **Step 4: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=CardioServiceImplTest test`
Expected: FAIL（CardioServiceImpl 类不存在或方法未实现）

- [ ] **Step 5: 写 CardioServiceImpl 实现**

```java
package com.itgeo.fitmate.api.fitness.cardio.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.fitness.cardio.application.CardioService;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper.CardioLogMapper;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class CardioServiceImpl implements CardioService {

    private static final Map<String, String> TYPE_CN = new LinkedHashMap<>();

    static {
        TYPE_CN.put("running", "跑步");
        TYPE_CN.put("cycling", "骑行");
        TYPE_CN.put("swimming", "游泳");
        TYPE_CN.put("rowing", "划船");
        TYPE_CN.put("jump_rope", "跳绳");
        TYPE_CN.put("other", "其他");
    }

    @Resource
    private CardioLogMapper cardioLogMapper;

    @Override
    public void logCardio(Long userId, CardioLogRequest request, String source) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        LocalDate trainingDate = parseDate(request.getDate());
        if (request.getCardioType() == null || request.getCardioType().isBlank()) {
            throw new IllegalArgumentException("cardioType 不能为空");
        }
        if (request.getDistanceKm() == null && request.getDurationMinutes() == null) {
            throw new IllegalArgumentException("distanceKm 与 durationMinutes 至少需提供一个");
        }

        String avgPace = calculatePace(request.getDistanceKm(), request.getDurationMinutes());
        String summary = buildSummary(request, avgPace);
        String normalizedSource = normalizeSource(source);

        CardioLog existing = cardioLogMapper.selectOne(
                new LambdaQueryWrapper<CardioLog>()
                        .eq(CardioLog::getUserId, userId)
                        .eq(CardioLog::getTrainingDate, trainingDate)
                        .eq(CardioLog::getCardioType, request.getCardioType())
                        .last("limit 1")
        );

        if (existing != null) {
            existing.setDistanceKm(request.getDistanceKm());
            existing.setDurationMinutes(request.getDurationMinutes());
            existing.setAvgPace(avgPace);
            existing.setAvgHeartRate(request.getAvgHeartRate());
            existing.setNote(blankToNull(request.getNote()));
            existing.setSummary(summary);
            existing.setSource(normalizedSource);
            cardioLogMapper.updateById(existing);
        } else {
            CardioLog entity = new CardioLog();
            entity.setUserId(userId);
            entity.setTrainingDate(trainingDate);
            entity.setCardioType(request.getCardioType());
            entity.setDistanceKm(request.getDistanceKm());
            entity.setDurationMinutes(request.getDurationMinutes());
            entity.setAvgPace(avgPace);
            entity.setAvgHeartRate(request.getAvgHeartRate());
            entity.setNote(blankToNull(request.getNote()));
            entity.setSummary(summary);
            entity.setSource(normalizedSource);
            cardioLogMapper.insert(entity);
        }
    }

    private String calculatePace(BigDecimal distanceKm, Integer durationMinutes) {
        if (distanceKm == null || distanceKm.signum() <= 0 || durationMinutes == null) {
            return null;
        }
        double paceSecPerKm = (durationMinutes * 60.0) / distanceKm.doubleValue();
        int mm = (int) (paceSecPerKm / 60);
        int ss = (int) Math.round(paceSecPerKm % 60);
        return String.format("%02d:%02d/km", mm, ss);
    }

    private String buildSummary(CardioLogRequest req, String avgPace) {
        StringBuilder sb = new StringBuilder();
        sb.append(TYPE_CN.getOrDefault(req.getCardioType(), req.getCardioType()));
        if (req.getDistanceKm() != null) {
            sb.append(" ").append(req.getDistanceKm()).append("km");
        }
        if (req.getDurationMinutes() != null) {
            sb.append(" / ").append(req.getDurationMinutes()).append("min");
        }
        if (avgPace != null) {
            sb.append(" / 配速 ").append(avgPace);
        }
        if (req.getAvgHeartRate() != null) {
            sb.append(" / 平均心率 ").append(req.getAvgHeartRate());
        }
        return sb.toString();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date 不能为空");
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date 格式必须为 yyyy-MM-dd");
        }
    }

    private String normalizeSource(String source) {
        if ("manual".equals(source) || "chat".equals(source) || "import".equals(source)) {
            return source;
        }
        return "manual";
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=CardioServiceImplTest test`
Expected: PASS（5 个测试全绿）

- [ ] **Step 7: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/dto/ \
        FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/application/ \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/cardio/
git commit -m "feat: add CardioService with upsert and pace calculation"
```

---

### Task 7: HeartRateLogRequest DTO + HeartRateService + Impl（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/dto/HeartRateLogRequest.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/application/HeartRateService.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/application/impl/HeartRateServiceImpl.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/heartrate/application/impl/HeartRateServiceImplTest.java`

- [ ] **Step 1: 创建 HeartRateLogRequest DTO**

```java
package com.itgeo.fitmate.api.fitness.heartrate.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 心率记录请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class HeartRateLogRequest {
    /** 记录日期，格式 yyyy-MM-dd。 */
    private String date;
    /** 静息心率。 */
    private Integer restingHr;
    /** 最大心率。 */
    private Integer maxHr;
    /** 心率变异性 ms。 */
    private Integer hrv;
    /** 备注。 */
    private String note;
}
```

- [ ] **Step 2: 创建 HeartRateService 接口**

```java
package com.itgeo.fitmate.api.fitness.heartrate.application;

import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;

/**
 * 心率记录服务。
 */
public interface HeartRateService {

    /**
     * 记录心率（upsert，按 userId + date 唯一键）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logHeartRate(Long userId, HeartRateLogRequest request, String source);
}
```

- [ ] **Step 3: 写失败测试**

```java
package com.itgeo.fitmate.api.fitness.heartrate.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.heartrate.application.impl.HeartRateServiceImpl;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper.HeartRateMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HeartRateServiceImplTest {

    private HeartRateMapper heartRateMapper;
    private HeartRateServiceImpl service;

    @BeforeEach
    void setUp() {
        heartRateMapper = mock(HeartRateMapper.class);
        service = new HeartRateServiceImpl(heartRateMapper);
    }

    @Test
    void logHeartRate_newRecord_inserts() {
        when(heartRateMapper.selectOne(any())).thenReturn(null);

        HeartRateLogRequest req = new HeartRateLogRequest("2026-07-03", 60, 180, 50, null);

        service.logHeartRate(1L, req, "chat");

        ArgumentCaptor<HeartRate> captor = ArgumentCaptor.forClass(HeartRate.class);
        verify(heartRateMapper).insert(captor.capture());
        HeartRate saved = captor.getValue();
        assertEquals(1L, saved.getUserId());
        assertEquals(LocalDate.of(2026, 7, 3), saved.getRecordDate());
        assertEquals(60, saved.getRestingHr());
        assertEquals("chat", saved.getSource());
        assertNotNull(saved.getSummary());
    }

    @Test
    void logHeartRate_existingRecord_updatesById() {
        HeartRate existing = new HeartRate();
        existing.setId(88L);
        when(heartRateMapper.selectOne(any())).thenReturn(existing);

        HeartRateLogRequest req = new HeartRateLogRequest("2026-07-03", 62, null, null, null);

        service.logHeartRate(1L, req, "chat");

        verify(heartRateMapper).updateById(existing);
        verify(heartRateMapper, never()).insert(any());
        assertEquals(62, existing.getRestingHr());
        assertEquals("chat", existing.getSource());
    }

    @Test
    void logHeartRate_allNull_throws() {
        HeartRateLogRequest req = new HeartRateLogRequest("2026-07-03", null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logHeartRate(1L, req, "chat"));
    }

    @Test
    void logHeartRate_invalidDate_throws() {
        HeartRateLogRequest req = new HeartRateLogRequest("07-03-2026", 60, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logHeartRate(1L, req, "chat"));
    }
}
```

- [ ] **Step 4: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=HeartRateServiceImplTest test`
Expected: FAIL

- [ ] **Step 5: 写 HeartRateServiceImpl 实现**

```java
package com.itgeo.fitmate.api.fitness.heartrate.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.fitness.heartrate.application.HeartRateService;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper.HeartRateMapper;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class HeartRateServiceImpl implements HeartRateService {

    @Resource
    private HeartRateMapper heartRateMapper;

    @Override
    public void logHeartRate(Long userId, HeartRateLogRequest request, String source) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        LocalDate recordDate = parseDate(request.getDate());
        if (request.getRestingHr() == null && request.getMaxHr() == null && request.getHrv() == null) {
            throw new IllegalArgumentException("restingHr / maxHr / hrv 至少需提供一个");
        }

        String summary = buildSummary(request);
        String normalizedSource = normalizeSource(source);

        HeartRate existing = heartRateMapper.selectOne(
                new LambdaQueryWrapper<HeartRate>()
                        .eq(HeartRate::getUserId, userId)
                        .eq(HeartRate::getRecordDate, recordDate)
                        .last("limit 1")
        );

        if (existing != null) {
            existing.setRestingHr(request.getRestingHr());
            existing.setMaxHr(request.getMaxHr());
            existing.setHrv(request.getHrv());
            existing.setNote(blankToNull(request.getNote()));
            existing.setSummary(summary);
            existing.setSource(normalizedSource);
            heartRateMapper.updateById(existing);
        } else {
            HeartRate entity = new HeartRate();
            entity.setUserId(userId);
            entity.setRecordDate(recordDate);
            entity.setRestingHr(request.getRestingHr());
            entity.setMaxHr(request.getMaxHr());
            entity.setHrv(request.getHrv());
            entity.setNote(blankToNull(request.getNote()));
            entity.setSummary(summary);
            entity.setSource(normalizedSource);
            heartRateMapper.insert(entity);
        }
    }

    private String buildSummary(HeartRateLogRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getRestingHr() != null) {
            sb.append("静息心率 ").append(req.getRestingHr());
        }
        if (req.getMaxHr() != null) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append("最大心率 ").append(req.getMaxHr());
        }
        if (req.getHrv() != null) {
            if (sb.length() > 0) sb.append(" / ");
            sb.append("HRV ").append(req.getHrv()).append("ms");
        }
        return sb.toString();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date 不能为空");
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date 格式必须为 yyyy-MM-dd");
        }
    }

    private String normalizeSource(String source) {
        if ("manual".equals(source) || "chat".equals(source) || "import".equals(source)) {
            return source;
        }
        return "manual";
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=HeartRateServiceImplTest test`
Expected: PASS（4 个测试全绿）

- [ ] **Step 7: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/dto/ \
        FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/application/ \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/heartrate/
git commit -m "feat: add HeartRateService with upsert"
```

---

### Task 8: DietLogRequest + DietItemDTO + DietService + Impl（TDD，含热量汇总）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/dto/DietItemDTO.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/dto/DietLogRequest.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/application/DietService.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/application/impl/DietServiceImpl.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/diet/application/impl/DietServiceImplTest.java`

- [ ] **Step 1: 创建 DietItemDTO**

```java
package com.itgeo.fitmate.api.fitness.diet.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 饮食明细 DTO。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DietItemDTO {
    /** 食物名称。 */
    private String name;
    /** 份量描述。 */
    private String portion;
    /** 热量 kcal。 */
    private Integer calories;
    /** 蛋白质 g。 */
    private BigDecimal protein;
    /** 碳水 g。 */
    private BigDecimal carbs;
    /** 脂肪 g。 */
    private BigDecimal fat;
}
```

- [ ] **Step 2: 创建 DietLogRequest**

```java
package com.itgeo.fitmate.api.fitness.diet.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 饮食日志记录请求。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DietLogRequest {
    /** 记录日期，格式 yyyy-MM-dd。 */
    private String date;
    /** 餐次：breakfast/lunch/dinner/snack。 */
    private String mealType;
    /** 食物明细列表。 */
    private List<DietItemDTO> items;
    /** 备注。 */
    private String note;
}
```

- [ ] **Step 3: 创建 DietService 接口**

```java
package com.itgeo.fitmate.api.fitness.diet.application;

import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;

/**
 * 饮食记录服务。
 */
public interface DietService {

    /**
     * 记录饮食（upsert，按 userId + date + mealType 唯一键）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logDiet(Long userId, DietLogRequest request, String source);
}
```

- [ ] **Step 4: 写失败测试**

```java
package com.itgeo.fitmate.api.fitness.diet.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.diet.application.impl.DietServiceImpl;
import com.itgeo.fitmate.api.fitness.diet.dto.DietItemDTO;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietItemMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietLogMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DietServiceImplTest {

    private DietLogMapper dietLogMapper;
    private DietItemMapper dietItemMapper;
    private DietServiceImpl service;

    @BeforeEach
    void setUp() {
        dietLogMapper = mock(DietLogMapper.class);
        dietItemMapper = mock(DietItemMapper.class);
        service = new DietServiceImpl(dietLogMapper, dietItemMapper);
    }

    @Test
    void logDiet_newRecord_insertsMainAndItemsWithTotals() {
        when(dietLogMapper.selectOne(any())).thenReturn(null);

        DietLogRequest req = new DietLogRequest("2026-07-03", "breakfast",
                List.of(
                        new DietItemDTO("鸡蛋", "2个", 140, new BigDecimal("12"), new BigDecimal("1"), new BigDecimal("10")),
                        new DietItemDTO("牛奶", "250ml", 150, new BigDecimal("8"), new BigDecimal("12"), new BigDecimal("8"))
                ), null);

        service.logDiet(1L, req, "chat");

        ArgumentCaptor<DietLog> logCaptor = ArgumentCaptor.forClass(DietLog.class);
        verify(dietLogMapper).insert(logCaptor.capture());
        DietLog savedLog = logCaptor.getValue();
        assertEquals(1L, savedLog.getUserId());
        assertEquals(LocalDate.of(2026, 7, 3), savedLog.getRecordDate());
        assertEquals("breakfast", savedLog.getMealType());
        assertEquals(290, savedLog.getTotalCalories());
        assertEquals(new BigDecimal("20.0"), savedLog.getTotalProtein());
        assertEquals(new BigDecimal("13.0"), savedLog.getTotalCarbs());
        assertEquals(new BigDecimal("18.0"), savedLog.getTotalFat());
        assertEquals("chat", savedLog.getSource());

        verify(dietItemMapper, times(2)).insert(any(DietItem.class));
    }

    @Test
    void logDiet_existingRecord_updatesAndRebuildsItems() {
        DietLog existing = new DietLog();
        existing.setId(77L);
        when(dietLogMapper.selectOne(any())).thenReturn(existing);

        DietLogRequest req = new DietLogRequest("2026-07-03", "lunch",
                List.of(new DietItemDTO("鸡胸肉", "150g", 240, new BigDecimal("50"), new BigDecimal("0"), new BigDecimal("5"))),
                null);

        service.logDiet(1L, req, "chat");

        verify(dietItemMapper).delete(any());
        verify(dietLogMapper).updateById(existing);
        verify(dietLogMapper, never()).insert(any());
        assertEquals(240, existing.getTotalCalories());
        assertEquals("chat", existing.getSource());
        verify(dietItemMapper, times(1)).insert(any(DietItem.class));
    }

    @Test
    void logDiet_emptyItems_throws() {
        DietLogRequest req = new DietLogRequest("2026-07-03", "breakfast", List.of(), null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logDiet(1L, req, "chat"));
    }

    @Test
    void logDiet_invalidDate_throws() {
        DietLogRequest req = new DietLogRequest("bad", "breakfast",
                List.of(new DietItemDTO("x", null, 100, null, null, null)), null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logDiet(1L, req, "chat"));
    }
}
```

- [ ] **Step 5: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=DietServiceImplTest test`
Expected: FAIL

- [ ] **Step 6: 写 DietServiceImpl 实现**

```java
package com.itgeo.fitmate.api.fitness.diet.application.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.itgeo.fitmate.api.fitness.diet.application.DietService;
import com.itgeo.fitmate.api.fitness.diet.dto.DietItemDTO;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietItemMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietLogMapper;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class DietServiceImpl implements DietService {

    private static final Map<String, String> MEAL_CN = new LinkedHashMap<>();

    static {
        MEAL_CN.put("breakfast", "早餐");
        MEAL_CN.put("lunch", "午餐");
        MEAL_CN.put("dinner", "晚餐");
        MEAL_CN.put("snack", "加餐");
    }

    @Resource
    private DietLogMapper dietLogMapper;

    @Resource
    private DietItemMapper dietItemMapper;

    @Override
    public void logDiet(Long userId, DietLogRequest request, String source) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        LocalDate recordDate = parseDate(request.getDate());
        if (request.getMealType() == null || request.getMealType().isBlank()) {
            throw new IllegalArgumentException("mealType 不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("items 至少需 1 条");
        }

        int totalCalories = 0;
        BigDecimal totalProtein = BigDecimal.ZERO;
        BigDecimal totalCarbs = BigDecimal.ZERO;
        BigDecimal totalFat = BigDecimal.ZERO;
        for (DietItemDTO item : request.getItems()) {
            if (item.getName() == null || item.getName().isBlank()) {
                throw new IllegalArgumentException("item.name 不能为空");
            }
            if (item.getCalories() != null) totalCalories += item.getCalories();
            if (item.getProtein() != null) totalProtein = totalProtein.add(item.getProtein());
            if (item.getCarbs() != null) totalCarbs = totalCarbs.add(item.getCarbs());
            if (item.getFat() != null) totalFat = totalFat.add(item.getFat());
        }

        String summary = buildSummary(request.getMealType(), totalCalories, totalProtein, totalCarbs, totalFat);
        String normalizedSource = normalizeSource(source);

        DietLog existing = dietLogMapper.selectOne(
                new LambdaQueryWrapper<DietLog>()
                        .eq(DietLog::getUserId, userId)
                        .eq(DietLog::getRecordDate, recordDate)
                        .eq(DietLog::getMealType, request.getMealType())
                        .last("limit 1")
        );

        Long dietLogId;
        if (existing != null) {
            existing.setTotalCalories(totalCalories);
            existing.setTotalProtein(totalProtein);
            existing.setTotalCarbs(totalCarbs);
            existing.setTotalFat(totalFat);
            existing.setNote(blankToNull(request.getNote()));
            existing.setSummary(summary);
            existing.setSource(normalizedSource);
            dietLogMapper.updateById(existing);
            dietLogId = existing.getId();
            dietItemMapper.delete(
                    new LambdaQueryWrapper<DietItem>()
                            .eq(DietItem::getDietLogId, dietLogId)
            );
        } else {
            DietLog entity = new DietLog();
            entity.setUserId(userId);
            entity.setRecordDate(recordDate);
            entity.setMealType(request.getMealType());
            entity.setTotalCalories(totalCalories);
            entity.setTotalProtein(totalProtein);
            entity.setTotalCarbs(totalCarbs);
            entity.setTotalFat(totalFat);
            entity.setNote(blankToNull(request.getNote()));
            entity.setSummary(summary);
            entity.setSource(normalizedSource);
            dietLogMapper.insert(entity);
            dietLogId = entity.getId();
        }

        for (DietItemDTO item : request.getItems()) {
            DietItem entity = new DietItem();
            entity.setDietLogId(dietLogId);
            entity.setFoodName(item.getName().trim());
            entity.setPortion(blankToNull(item.getPortion()));
            entity.setCalories(item.getCalories());
            entity.setProtein(item.getProtein());
            entity.setCarbs(item.getCarbs());
            entity.setFat(item.getFat());
            dietItemMapper.insert(entity);
        }
    }

    private String buildSummary(String mealType, int totalCalories,
                                BigDecimal protein, BigDecimal carbs, BigDecimal fat) {
        return String.format("%s 共 %dkcal (蛋白%sg/碳水%sg/脂肪%sg)",
                MEAL_CN.getOrDefault(mealType, mealType),
                totalCalories,
                protein.stripTrailingZeros().toPlainString(),
                carbs.stripTrailingZeros().toPlainString(),
                fat.stripTrailingZeros().toPlainString());
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            throw new IllegalArgumentException("date 不能为空");
        }
        try {
            return LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("date 格式必须为 yyyy-MM-dd");
        }
    }

    private String normalizeSource(String source) {
        if ("manual".equals(source) || "chat".equals(source) || "import".equals(source)) {
            return source;
        }
        return "manual";
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
```

- [ ] **Step 7: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=DietServiceImplTest test`
Expected: PASS（4 个测试全绿）

- [ ] **Step 8: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/dto/ \
        FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/application/ \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/diet/
git commit -m "feat: add DietService with upsert and calorie aggregation"
```

---

### Task 9: TrainingService 扩展 source 重载（TDD）

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/application/TrainingService.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/application/impl/TrainingServiceImpl.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/training/application/impl/TrainingServiceImplTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.fitness.training.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.training.application.impl.TrainingServiceImpl;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingExerciseItem;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingLogRequest;
import com.itgeo.fitmate.api.fitness.training.infrastructure.entity.TrainingLog;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingExerciseMapper;
import com.itgeo.fitmate.api.fitness.training.infrastructure.mapper.TrainingLogMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TrainingServiceImplTest {

    private TrainingLogMapper trainingLogMapper;
    private TrainingExerciseMapper trainingExerciseMapper;
    private TrainingServiceImpl service;

    @BeforeEach
    void setUp() {
        trainingLogMapper = mock(TrainingLogMapper.class);
        trainingExerciseMapper = mock(TrainingExerciseMapper.class);
        service = new TrainingServiceImpl(trainingLogMapper, trainingExerciseMapper);
    }

    @Test
    void logTraining_withChatSource_persistsSource() {
        when(trainingLogMapper.selectOne(any())).thenReturn(null);

        TrainingLogRequest req = new TrainingLogRequest("2026-07-03",
                List.of(new TrainingExerciseItem("卧推", 3, 10, new BigDecimal("60"))));

        service.logTraining(1L, req, "chat");

        ArgumentCaptor<TrainingLog> captor = ArgumentCaptor.forClass(TrainingLog.class);
        verify(trainingLogMapper).insert(captor.capture());
        assertEquals("chat", captor.getValue().getSource());
    }

    @Test
    void logTraining_legacyOverload_defaultsToManual() {
        when(trainingLogMapper.selectOne(any())).thenReturn(null);

        TrainingLogRequest req = new TrainingLogRequest("2026-07-03",
                List.of(new TrainingExerciseItem("卧推", 3, 10, new BigDecimal("60"))));

        service.logTraining(1L, req);

        ArgumentCaptor<TrainingLog> captor = ArgumentCaptor.forClass(TrainingLog.class);
        verify(trainingLogMapper).insert(captor.capture());
        assertEquals("manual", captor.getValue().getSource());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=TrainingServiceImplTest test`
Expected: FAIL（无 source 重载方法 / 构造器不匹配）

- [ ] **Step 3: 修改 TrainingService 接口追加重载**

在 `TrainingService.java` 接口中，现有 `void logTraining(Long userId, TrainingLogRequest request);` 之后追加：

```java
    /**
     * 记录训练日志（指定来源）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logTraining(Long userId, TrainingLogRequest request, String source);
```

- [ ] **Step 4: 修改 TrainingServiceImpl，提取 source 参数 + 旧方法委托**

在 `TrainingServiceImpl.java` 中：

1. 把现有 `public void logTraining(Long userId, TrainingLogRequest request)` 方法签名改为带 source 参数：`public void logTraining(Long userId, TrainingLogRequest request, String source)`，方法体内所有 `existing.setSource("manual")` 和 `log.setSource("manual")` 改为 `existing.setSource(source)` / `log.setSource(source)`。

2. 追加旧签名的委托方法：

```java
    @Override
    public void logTraining(Long userId, TrainingLogRequest request) {
        logTraining(userId, request, "manual");
    }
```

> 注意：实现内部原有的 `setSource("manual")` 必须全部替换为 `setSource(source)`，否则测试会失败。

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=TrainingServiceImplTest test`
Expected: PASS（2 个测试全绿）

- [ ] **Step 6: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/application/ \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/training/
git commit -m "feat: add source parameter overload to TrainingService.logTraining"
```

---

### Task 10: BodyMetricsService 扩展 source 重载 + 围度字段处理（TDD）

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/application/BodyMetricsService.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/application/impl/BodyMetricsServiceImpl.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/metrics/application/impl/BodyMetricsServiceImplTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.fitness.metrics.application.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.fitness.metrics.application.impl.BodyMetricsServiceImpl;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BodyMetricsServiceImplTest {

    private BodyMetricsMapper bodyMetricsMapper;
    private BodyMetricsServiceImpl service;

    @BeforeEach
    void setUp() {
        bodyMetricsMapper = mock(BodyMetricsMapper.class);
        service = new BodyMetricsServiceImpl(bodyMetricsMapper);
    }

    @Test
    void logBodyMetrics_withChatSource_persistsSource() {
        when(bodyMetricsMapper.selectOne(any())).thenReturn(null);

        BodyMetricsLogRequest req = new BodyMetricsLogRequest(
                "2026-07-03", new BigDecimal("70.5"), null, null, null, null);

        service.logBodyMetrics(1L, req, "chat");

        ArgumentCaptor<BodyMetrics> captor = ArgumentCaptor.forClass(BodyMetrics.class);
        verify(bodyMetricsMapper).insert(captor.capture());
        assertEquals("chat", captor.getValue().getSource());
    }

    @Test
    void logBodyMetrics_girthOnly_insertsSuccessfully() {
        when(bodyMetricsMapper.selectOne(any())).thenReturn(null);

        BodyMetricsLogRequest req = new BodyMetricsLogRequest(
                "2026-07-03", null, null, null, null, null);
        req.setWaistGirth(new BigDecimal("80"));

        service.logBodyMetrics(1L, req, "chat");

        ArgumentCaptor<BodyMetrics> captor = ArgumentCaptor.forClass(BodyMetrics.class);
        verify(bodyMetricsMapper).insert(captor.capture());
        assertEquals(new BigDecimal("80"), captor.getValue().getWaistGirth());
    }

    @Test
    void logBodyMetrics_allNull_throws() {
        BodyMetricsLogRequest req = new BodyMetricsLogRequest(
                "2026-07-03", null, null, null, null, null);
        assertThrows(IllegalArgumentException.class,
                () -> service.logBodyMetrics(1L, req, "chat"));
    }

    @Test
    void logBodyMetrics_legacyOverload_defaultsToManual() {
        when(bodyMetricsMapper.selectOne(any())).thenReturn(null);

        BodyMetricsLogRequest req = new BodyMetricsLogRequest(
                "2026-07-03", new BigDecimal("70"), null, null, null, null);

        service.logBodyMetrics(1L, req);

        ArgumentCaptor<BodyMetrics> captor = ArgumentCaptor.forClass(BodyMetrics.class);
        verify(bodyMetricsMapper).insert(captor.capture());
        assertEquals("manual", captor.getValue().getSource());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=BodyMetricsServiceImplTest test`
Expected: FAIL

- [ ] **Step 3: 修改 BodyMetricsService 接口追加重载**

在 `BodyMetricsService.java` 中，现有 `void logBodyMetrics(Long userId, BodyMetricsLogRequest request);` 之后追加：

```java
    /**
     * 记录身体指标（指定来源）。
     *
     * @param userId 用户ID
     * @param request 请求
     * @param source 来源：manual/chat/import
     */
    void logBodyMetrics(Long userId, BodyMetricsLogRequest request, String source);
```

- [ ] **Step 4: 修改 BodyMetricsServiceImpl，提取 source + 围度字段 + 放宽校验**

在 `BodyMetricsServiceImpl.java` 中：

1. 把现有 `logTraining(Long, BodyMetricsLogRequest)` 方法签名改为带 source：`logBodyMetrics(Long userId, BodyMetricsLogRequest request, String source)`。

2. 把原有的"weight/bodyFat 至少一个非空"校验改为"weight/bodyFat/任一围度字段至少一个非空"：

```java
        if (request.getWeight() == null && request.getBodyFat() == null
                && request.getChestGirth() == null && request.getWaistGirth() == null
                && request.getHipGirth() == null && request.getArmGirth() == null
                && request.getThighGirth() == null) {
            throw new IllegalArgumentException("weight / bodyFat / 任一围度字段至少需提供一个");
        }
```

3. 在 existing 与新建分支中，补充围度字段的 set 调用（与 weight/bodyFat 同位置）：

```java
        existing.setChestGirth(request.getChestGirth());
        existing.setWaistGirth(request.getWaistGirth());
        existing.setHipGirth(request.getHipGirth());
        existing.setArmGirth(request.getArmGirth());
        existing.setThighGirth(request.getThighGirth());
```

（existing 分支与新建 entity 分支都要加，两处）

4. 把所有 `setSource("manual")` 改为 `setSource(source)`。

5. 追加旧签名的委托方法：

```java
    @Override
    public void logBodyMetrics(Long userId, BodyMetricsLogRequest request) {
        logBodyMetrics(userId, request, "manual");
    }
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=BodyMetricsServiceImplTest test`
Expected: PASS（4 个测试全绿）

- [ ] **Step 6: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/application/ \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/metrics/
git commit -m "feat: add source overload and girth fields to BodyMetricsService"
```

---

## Phase 3: 工具层 - record 工具

### Task 11: TrainingLogRecordToolExecutor（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/TrainingLogRecordToolExecutor.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/TrainingLogRecordToolExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.training.application.TrainingService;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingLogRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrainingLogRecordToolExecutorTest {

    private TrainingService trainingService;
    private TrainingLogRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        trainingService = mock(TrainingService.class);
        executor = new TrainingLogRecordToolExecutor(trainingService);
    }

    @Test
    void execute_validInput_returnsOk() {
        Map<String, Object> args = new HashMap<>();
        args.put("date", "2026-07-03");
        args.put("exercises", List.of(Map.of(
                "name", "卧推", "sets", 3, "reps", 10, "weight", 60)));
        ToolCall call = new ToolCall("call-1", "training_log.record", args);

        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(trainingService).logTraining(eq(1L), any(TrainingLogRequest.class), eq("chat"));
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("call-1", "training_log.record", Map.of("date", "2026-07-03"));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("call-1", "training_log.record", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=TrainingLogRecordToolExecutorTest test`
Expected: FAIL（类不存在）

- [ ] **Step 3: 写实现**

```java
package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.training.application.TrainingService;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingExerciseItem;
import com.itgeo.fitmate.api.fitness.training.dto.TrainingLogRequest;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 训练日志记录工具（增/改，upsert）。
 */
@Component
public class TrainingLogRecordToolExecutor implements ToolExecutor {

    @Resource
    private TrainingService trainingService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "training_log.record",
                "记录当前用户当日训练日志（增/改，按日期 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"exercises\":[{\"name\",\"sets\",\"reps\",\"weight\"}]}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"exercises\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"sets\":{\"type\":\"integer\"},\"reps\":{\"type\":\"integer\"},\"weight\":{\"type\":\"number\"}},\"required\":[\"name\",\"sets\",\"reps\",\"weight\"]}}},\"required\":[\"date\",\"exercises\"]}",
                false
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        Object dateObj = args == null ? null : args.get("date");
        if (!(dateObj instanceof String date) || date.isBlank()) {
            return ToolResult.error("date 参数必填且为 yyyy-MM-dd 字符串");
        }
        Object exercisesObj = args.get("exercises");
        if (!(exercisesObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return ToolResult.error("exercises 参数必填且至少 1 条");
        }
        List<TrainingExerciseItem> exercises = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                return ToolResult.error("exercises 元素必须是对象");
            }
            TrainingExerciseItem ex = new TrainingExerciseItem();
            ex.setName(asString(map.get("name")));
            ex.setSets(asInt(map.get("sets")));
            ex.setReps(asInt(map.get("reps")));
            ex.setWeight(asBigDecimal(map.get("weight")));
            exercises.add(ex);
        }
        TrainingLogRequest request = new TrainingLogRequest(date, exercises);
        try {
            trainingService.logTraining(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录训练日志 " + date + "，共 " + exercises.size() + " 个动作",
                    Map.of("date", date, "exerciseCount", exercises.size()));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private BigDecimal asBigDecimal(Object o) {
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o instanceof String s) {
            try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=TrainingLogRecordToolExecutorTest test`
Expected: PASS（3 个测试全绿）

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/TrainingLogRecordToolExecutor.java \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/TrainingLogRecordToolExecutorTest.java
git commit -m "feat: add training_log.record tool"
```

---

### Task 12: CardioRecordToolExecutor（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/CardioRecordToolExecutor.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/CardioRecordToolExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.cardio.application.CardioService;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CardioRecordToolExecutorTest {

    private CardioService cardioService;
    private CardioRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        cardioService = mock(CardioService.class);
        executor = new CardioRecordToolExecutor(cardioService);
    }

    @Test
    void execute_validInput_returnsOk() {
        ToolCall call = new ToolCall("c1", "cardio.record", Map.of(
                "date", "2026-07-03",
                "cardio_type", "running",
                "distance_km", 5.0,
                "duration_minutes", 30,
                "avg_heart_rate", 150));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(cardioService).logCardio(eq(1L), any(CardioLogRequest.class), eq("chat"));
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("c1", "cardio.record", Map.of("date", "2026-07-03", "cardio_type", "running"));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("c1", "cardio.record", Map.of("cardio_type", "running"));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=CardioRecordToolExecutorTest test`
Expected: FAIL

- [ ] **Step 3: 写实现**

```java
package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.cardio.application.CardioService;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 有氧训练记录工具（增/改，upsert）。
 */
@Component
public class CardioRecordToolExecutor implements ToolExecutor {

    @Resource
    private CardioService cardioService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "cardio.record",
                "记录当前用户当日有氧训练（增/改，按日期+类型 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"cardio_type\":\"running/cycling/swimming/rowing/jump_rope/other\",\"distance_km\":number,\"duration_minutes\":int,\"avg_heart_rate\":int,\"note\":\"\"}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"cardio_type\":{\"type\":\"string\",\"enum\":[\"running\",\"cycling\",\"swimming\",\"rowing\",\"jump_rope\",\"other\"]},\"distance_km\":{\"type\":\"number\"},\"duration_minutes\":{\"type\":\"integer\"},\"avg_heart_rate\":{\"type\":\"integer\"},\"note\":{\"type\":\"string\"}},\"required\":[\"date\",\"cardio_type\"]}",
                false
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        if (args == null) {
            return ToolResult.error("参数不能为空");
        }
        Object dateObj = args.get("date");
        if (!(dateObj instanceof String date) || date.isBlank()) {
            return ToolResult.error("date 参数必填且为 yyyy-MM-dd 字符串");
        }
        Object typeObj = args.get("cardio_type");
        if (!(typeObj instanceof String cardioType) || cardioType.isBlank()) {
            return ToolResult.error("cardio_type 参数必填");
        }
        CardioLogRequest request = new CardioLogRequest();
        request.setDate(date);
        request.setCardioType(cardioType);
        request.setDistanceKm(asBigDecimal(args.get("distance_km")));
        request.setDurationMinutes(asInt(args.get("duration_minutes")));
        request.setAvgHeartRate(asInt(args.get("avg_heart_rate")));
        request.setNote(asString(args.get("note")));
        try {
            cardioService.logCardio(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录有氧训练 " + cardioType + " " + date, Map.of("date", date, "cardio_type", cardioType));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private BigDecimal asBigDecimal(Object o) {
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o instanceof String s) {
            try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=CardioRecordToolExecutorTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/CardioRecordToolExecutor.java \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/CardioRecordToolExecutorTest.java
git commit -m "feat: add cardio.record tool"
```

---

### Task 13: BodyMetricsRecordToolExecutor（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/BodyMetricsRecordToolExecutor.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/BodyMetricsRecordToolExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.metrics.application.BodyMetricsService;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BodyMetricsRecordToolExecutorTest {

    private BodyMetricsService bodyMetricsService;
    private BodyMetricsRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        bodyMetricsService = mock(BodyMetricsService.class);
        executor = new BodyMetricsRecordToolExecutor(bodyMetricsService);
    }

    @Test
    void execute_validInput_returnsOk() {
        ToolCall call = new ToolCall("b1", "body_metrics.record", Map.of(
                "date", "2026-07-03", "weight", 70.5, "body_fat", 18));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(bodyMetricsService).logBodyMetrics(eq(1L), any(BodyMetricsLogRequest.class), eq("chat"));
    }

    @Test
    void execute_girthOnly_returnsOk() {
        ToolCall call = new ToolCall("b1", "body_metrics.record", Map.of(
                "date", "2026-07-03", "waist_girth", 80));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("b1", "body_metrics.record", Map.of("date", "2026-07-03"));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("b1", "body_metrics.record", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=BodyMetricsRecordToolExecutorTest test`
Expected: FAIL

- [ ] **Step 3: 写实现**

```java
package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.metrics.application.BodyMetricsService;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsLogRequest;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 身体指标记录工具（增/改，upsert）。
 */
@Component
public class BodyMetricsRecordToolExecutor implements ToolExecutor {

    @Resource
    private BodyMetricsService bodyMetricsService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "body_metrics.record",
                "记录当前用户当日身体指标（增/改，按日期 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"weight\":number,\"body_fat\":number,\"sleep_hours\":number,\"fatigue\":\"低/中/高\",\"chest_girth\":number,\"waist_girth\":number,\"hip_girth\":number,\"arm_girth\":number,\"thigh_girth\":number,\"note\":\"\"}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"weight\":{\"type\":\"number\"},\"body_fat\":{\"type\":\"number\"},\"sleep_hours\":{\"type\":\"number\"},\"fatigue\":{\"type\":\"string\",\"enum\":[\"低\",\"中\",\"高\"]},\"chest_girth\":{\"type\":\"number\"},\"waist_girth\":{\"type\":\"number\"},\"hip_girth\":{\"type\":\"number\"},\"arm_girth\":{\"type\":\"number\"},\"thigh_girth\":{\"type\":\"number\"},\"note\":{\"type\":\"string\"}},\"required\":[\"date\"]}",
                false
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        if (args == null) {
            return ToolResult.error("参数不能为空");
        }
        Object dateObj = args.get("date");
        if (!(dateObj instanceof String date) || date.isBlank()) {
            return ToolResult.error("date 参数必填且为 yyyy-MM-dd 字符串");
        }
        BodyMetricsLogRequest request = new BodyMetricsLogRequest();
        request.setDate(date);
        request.setWeight(asBigDecimal(args.get("weight")));
        request.setBodyFat(asBigDecimal(args.get("body_fat")));
        request.setSleep(asBigDecimal(args.get("sleep_hours")));
        request.setFatigue(asString(args.get("fatigue")));
        request.setNote(asString(args.get("note")));
        // 围度字段（在 DTO 中新增）
        request.setChestGirth(asBigDecimal(args.get("chest_girth")));
        request.setWaistGirth(asBigDecimal(args.get("waist_girth")));
        request.setHipGirth(asBigDecimal(args.get("hip_girth")));
        request.setArmGirth(asBigDecimal(args.get("arm_girth")));
        request.setThighGirth(asBigDecimal(args.get("thigh_girth")));
        try {
            bodyMetricsService.logBodyMetrics(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录身体指标 " + date, Map.of("date", date));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private BigDecimal asBigDecimal(Object o) {
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o instanceof String s) {
            try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=BodyMetricsRecordToolExecutorTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/BodyMetricsRecordToolExecutor.java \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/BodyMetricsRecordToolExecutorTest.java
git commit -m "feat: add body_metrics.record tool"
```

---

### Task 14: HeartRateRecordToolExecutor（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/HeartRateRecordToolExecutor.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/HeartRateRecordToolExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.heartrate.application.HeartRateService;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeartRateRecordToolExecutorTest {

    private HeartRateService heartRateService;
    private HeartRateRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        heartRateService = mock(HeartRateService.class);
        executor = new HeartRateRecordToolExecutor(heartRateService);
    }

    @Test
    void execute_validInput_returnsOk() {
        ToolCall call = new ToolCall("h1", "heart_rate.record", Map.of(
                "date", "2026-07-03", "resting_hr", 60, "max_hr", 180, "hrv", 50));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(heartRateService).logHeartRate(eq(1L), any(HeartRateLogRequest.class), eq("chat"));
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("h1", "heart_rate.record", Map.of("date", "2026-07-03"));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("h1", "heart_rate.record", Map.of("resting_hr", 60));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=HeartRateRecordToolExecutorTest test`
Expected: FAIL

- [ ] **Step 3: 写实现**

```java
package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.heartrate.application.HeartRateService;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
import jakarta.annotation.Resource;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 心率记录工具（增/改，upsert）。
 */
@Component
public class HeartRateRecordToolExecutor implements ToolExecutor {

    @Resource
    private HeartRateService heartRateService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "heart_rate.record",
                "记录当前用户当日心率数据（增/改，按日期 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"resting_hr\":int,\"max_hr\":int,\"hrv\":int,\"note\":\"\"}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"resting_hr\":{\"type\":\"integer\"},\"max_hr\":{\"type\":\"integer\"},\"hrv\":{\"type\":\"integer\"},\"note\":{\"type\":\"string\"}},\"required\":[\"date\"]}",
                false
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        if (args == null) {
            return ToolResult.error("参数不能为空");
        }
        Object dateObj = args.get("date");
        if (!(dateObj instanceof String date) || date.isBlank()) {
            return ToolResult.error("date 参数必填且为 yyyy-MM-dd 字符串");
        }
        HeartRateLogRequest request = new HeartRateLogRequest();
        request.setDate(date);
        request.setRestingHr(asInt(args.get("resting_hr")));
        request.setMaxHr(asInt(args.get("max_hr")));
        request.setHrv(asInt(args.get("hrv")));
        request.setNote(asString(args.get("note")));
        try {
            heartRateService.logHeartRate(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录心率数据 " + date, Map.of("date", date));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=HeartRateRecordToolExecutorTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/HeartRateRecordToolExecutor.java \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/HeartRateRecordToolExecutorTest.java
git commit -m "feat: add heart_rate.record tool"
```

---

### Task 15: DietRecordToolExecutor（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/DietRecordToolExecutor.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/DietRecordToolExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.diet.application.DietService;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DietRecordToolExecutorTest {

    private DietService dietService;
    private DietRecordToolExecutor executor;

    @BeforeEach
    void setUp() {
        dietService = mock(DietService.class);
        executor = new DietRecordToolExecutor(dietService);
    }

    @Test
    void execute_validInput_returnsOk() {
        ToolCall call = new ToolCall("d1", "diet.record", Map.of(
                "date", "2026-07-03",
                "meal_type", "breakfast",
                "items", List.of(Map.of(
                        "name", "鸡蛋", "calories", 140, "protein", 12))));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
        verify(dietService).logDiet(eq(1L), any(DietLogRequest.class), eq("chat"));
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("d1", "diet.record", Map.of(
                "date", "2026-07-03", "meal_type", "breakfast", "items", List.of()));
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }

    @Test
    void execute_missingDate_returnsError() {
        ToolCall call = new ToolCall("d1", "diet.record", Map.of(
                "meal_type", "breakfast", "items", List.of(Map.of("name", "x"))));
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();
        ToolResult result = executor.execute(call, user);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=DietRecordToolExecutorTest test`
Expected: FAIL

- [ ] **Step 3: 写实现**

```java
package com.itgeo.fitmate.api.agent.tool;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.diet.application.DietService;
import com.itgeo.fitmate.api.fitness.diet.dto.DietItemDTO;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 饮食记录工具（增/改，upsert）。
 */
@Component
public class DietRecordToolExecutor implements ToolExecutor {

    @Resource
    private DietService dietService;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "diet.record",
                "记录当前用户当日饮食（增/改，按日期+餐次 upsert）。参数: {\"date\":\"yyyy-MM-dd\",\"meal_type\":\"breakfast/lunch/dinner/snack\",\"items\":[{\"name\",\"portion\",\"calories\":int,\"protein\":number,\"carbs\":number,\"fat\":number}],\"note\":\"\"}",
                "{\"type\":\"object\",\"properties\":{\"date\":{\"type\":\"string\",\"description\":\"yyyy-MM-dd 必填\"},\"meal_type\":{\"type\":\"string\",\"enum\":[\"breakfast\",\"lunch\",\"dinner\",\"snack\"]},\"items\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},\"portion\":{\"type\":\"string\"},\"calories\":{\"type\":\"integer\"},\"protein\":{\"type\":\"number\"},\"carbs\":{\"type\":\"number\"},\"fat\":{\"type\":\"number\"}},\"required\":[\"name\"]}},\"note\":{\"type\":\"string\"}},\"required\":[\"date\",\"meal_type\",\"items\"]}",
                false
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        if (args == null) {
            return ToolResult.error("参数不能为空");
        }
        Object dateObj = args.get("date");
        if (!(dateObj instanceof String date) || date.isBlank()) {
            return ToolResult.error("date 参数必填且为 yyyy-MM-dd 字符串");
        }
        Object mealObj = args.get("meal_type");
        if (!(mealObj instanceof String mealType) || mealType.isBlank()) {
            return ToolResult.error("meal_type 参数必填");
        }
        Object itemsObj = args.get("items");
        if (!(itemsObj instanceof List<?> rawList) || rawList.isEmpty()) {
            return ToolResult.error("items 参数必填且至少 1 条");
        }
        List<DietItemDTO> items = new ArrayList<>();
        for (Object item : rawList) {
            if (!(item instanceof Map<?, ?> map)) {
                return ToolResult.error("items 元素必须是对象");
            }
            DietItemDTO dto = new DietItemDTO();
            dto.setName(asString(map.get("name")));
            dto.setPortion(asString(map.get("portion")));
            dto.setCalories(asInt(map.get("calories")));
            dto.setProtein(asBigDecimal(map.get("protein")));
            dto.setCarbs(asBigDecimal(map.get("carbs")));
            dto.setFat(asBigDecimal(map.get("fat")));
            items.add(dto);
        }
        DietLogRequest request = new DietLogRequest(date, mealType, items, asString(args.get("note")));
        try {
            dietService.logDiet(authenticatedUser.getUserId(), request, "chat");
            return ToolResult.ok("已记录饮食 " + mealType + " " + date + "，共 " + items.size() + " 项食物",
                    Map.of("date", date, "meal_type", mealType, "itemCount", items.size()));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        }
    }

    private String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private Integer asInt(Object o) {
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private BigDecimal asBigDecimal(Object o) {
        if (o instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        if (o instanceof String s) {
            try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=DietRecordToolExecutorTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/DietRecordToolExecutor.java \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/DietRecordToolExecutorTest.java
git commit -m "feat: add diet.record tool"
```

---

## Phase 4: 工具层 - 配套 query 工具

### Task 16: CardioQueryToolExecutor（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/CardioQueryToolExecutor.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/CardioQueryToolExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper.CardioLogMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CardioQueryToolExecutorTest {

    private CardioLogMapper cardioLogMapper;
    private CardioQueryToolExecutor executor;

    @BeforeEach
    void setUp() {
        cardioLogMapper = mock(CardioLogMapper.class);
        executor = new CardioQueryToolExecutor(cardioLogMapper);
    }

    @Test
    void execute_hasRecords_returnsOkWithList() {
        CardioLog log = new CardioLog();
        log.setId(1L);
        log.setUserId(1L);
        log.setTrainingDate(LocalDate.of(2026, 7, 3));
        log.setCardioType("running");
        when(cardioLogMapper.selectList(any())).thenReturn(List.of(log));

        ToolCall call = new ToolCall("q1", "cardio.query", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_noRecords_returnsOkWithEmptyMessage() {
        when(cardioLogMapper.selectList(any())).thenReturn(List.of());

        ToolCall call = new ToolCall("q1", "cardio.query", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("q1", "cardio.query", Map.of());
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=CardioQueryToolExecutorTest test`
Expected: FAIL

- [ ] **Step 3: 写实现**

> 参照现有 `TrainingLogQueryToolExecutor` 的模式（直接用 Mapper + LambdaQueryWrapper）。

```java
package com.itgeo.fitmate.api.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.entity.CardioLog;
import com.itgeo.fitmate.api.fitness.cardio.infrastructure.mapper.CardioLogMapper;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 有氧训练查询工具（只读）。
 */
@Component
public class CardioQueryToolExecutor implements ToolExecutor {

    @Resource
    private CardioLogMapper cardioLogMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "cardio.query",
                "查询当前用户最近有氧训练记录，参数: {\"days\": 1-180, \"limit\": 1-50}",
                "{\"type\":\"object\",\"properties\":{\"days\":{\"type\":\"integer\"},\"limit\":{\"type\":\"integer\"}}}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        int days = normalizeNumber(args == null ? null : args.get("days"), 30, 180);
        int limit = normalizeNumber(args == null ? null : args.get("limit"), 20, 50);
        QueryWrapper<CardioLog> query = new QueryWrapper<>();
        query.eq("user_id", authenticatedUser.getUserId())
                .ge("training_date", LocalDate.now().minusDays(days))
                .orderByDesc("training_date")
                .last("LIMIT " + limit);
        List<CardioLog> logs = cardioLogMapper.selectList(query);
        return ToolResult.ok(logs.isEmpty() ? "未查询到有氧训练记录" : "已查询到有氧训练记录 " + logs.size() + " 条", logs);
    }

    private int normalizeNumber(Object value, int defaultValue, int maxValue) {
        int result = defaultValue;
        if (value instanceof Number n) {
            result = n.intValue();
        } else if (value instanceof String s) {
            try { result = Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        if (result < 1) result = 1;
        if (result > maxValue) result = maxValue;
        return result;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=CardioQueryToolExecutorTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/CardioQueryToolExecutor.java \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/CardioQueryToolExecutorTest.java
git commit -m "feat: add cardio.query tool"
```

---

### Task 17: HeartRateQueryToolExecutor（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/HeartRateQueryToolExecutor.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/HeartRateQueryToolExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper.HeartRateMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HeartRateQueryToolExecutorTest {

    private HeartRateMapper heartRateMapper;
    private HeartRateQueryToolExecutor executor;

    @BeforeEach
    void setUp() {
        heartRateMapper = mock(HeartRateMapper.class);
        executor = new HeartRateQueryToolExecutor(heartRateMapper);
    }

    @Test
    void execute_hasRecords_returnsOk() {
        HeartRate hr = new HeartRate();
        hr.setId(1L);
        hr.setUserId(1L);
        hr.setRecordDate(LocalDate.of(2026, 7, 3));
        when(heartRateMapper.selectList(any())).thenReturn(List.of(hr));

        ToolCall call = new ToolCall("q1", "heart_rate.query", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("q1", "heart_rate.query", Map.of());
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=HeartRateQueryToolExecutorTest test`
Expected: FAIL

- [ ] **Step 3: 写实现**

```java
package com.itgeo.fitmate.api.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate;
import com.itgeo.fitmate.api.fitness.heartrate.infrastructure.mapper.HeartRateMapper;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 心率查询工具（只读）。
 */
@Component
public class HeartRateQueryToolExecutor implements ToolExecutor {

    @Resource
    private HeartRateMapper heartRateMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "heart_rate.query",
                "查询当前用户最近心率记录，参数: {\"days\": 1-180, \"limit\": 1-50}",
                "{\"type\":\"object\",\"properties\":{\"days\":{\"type\":\"integer\"},\"limit\":{\"type\":\"integer\"}}}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        int days = normalizeNumber(args == null ? null : args.get("days"), 30, 180);
        int limit = normalizeNumber(args == null ? null : args.get("limit"), 20, 50);
        QueryWrapper<HeartRate> query = new QueryWrapper<>();
        query.eq("user_id", authenticatedUser.getUserId())
                .ge("record_date", LocalDate.now().minusDays(days))
                .orderByDesc("record_date")
                .last("LIMIT " + limit);
        List<HeartRate> logs = heartRateMapper.selectList(query);
        return ToolResult.ok(logs.isEmpty() ? "未查询到心率记录" : "已查询到心率记录 " + logs.size() + " 条", logs);
    }

    private int normalizeNumber(Object value, int defaultValue, int maxValue) {
        int result = defaultValue;
        if (value instanceof Number n) {
            result = n.intValue();
        } else if (value instanceof String s) {
            try { result = Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        if (result < 1) result = 1;
        if (result > maxValue) result = maxValue;
        return result;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=HeartRateQueryToolExecutorTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/HeartRateQueryToolExecutor.java \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/HeartRateQueryToolExecutorTest.java
git commit -m "feat: add heart_rate.query tool"
```

---

### Task 18: DietQueryToolExecutor（TDD）

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/DietQueryToolExecutor.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/DietQueryToolExecutorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.agent.tool;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietItemMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietLogMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DietQueryToolExecutorTest {

    private DietLogMapper dietLogMapper;
    private DietItemMapper dietItemMapper;
    private DietQueryToolExecutor executor;

    @BeforeEach
    void setUp() {
        dietLogMapper = mock(DietLogMapper.class);
        dietItemMapper = mock(DietItemMapper.class);
        executor = new DietQueryToolExecutor(dietLogMapper, dietItemMapper);
    }

    @Test
    void execute_hasRecords_returnsOkWithItems() {
        DietLog log = new DietLog();
        log.setId(1L);
        log.setUserId(1L);
        log.setRecordDate(LocalDate.of(2026, 7, 3));
        when(dietLogMapper.selectList(any())).thenReturn(List.of(log));
        when(dietItemMapper.selectList(any())).thenReturn(List.of(new DietItem()));

        ToolCall call = new ToolCall("q1", "diet.query", Map.of());
        AuthenticatedUserContext user = AuthenticatedUserContext.builder().userId(1L).build();

        ToolResult result = executor.execute(call, user);

        assertTrue(result.isSuccess());
    }

    @Test
    void execute_nullUser_returnsError() {
        ToolCall call = new ToolCall("q1", "diet.query", Map.of());
        ToolResult result = executor.execute(call, null);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=DietQueryToolExecutorTest test`
Expected: FAIL

- [ ] **Step 3: 写实现**

> diet.query 查主表后，按 diet_log_id 批量查明细并挂到 items 字段。

```java
package com.itgeo.fitmate.api.agent.tool;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itgeo.fitmate.api.agent.dto.AuthenticatedUserContext;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietItem;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietItemMapper;
import com.itgeo.fitmate.api.fitness.diet.infrastructure.mapper.DietLogMapper;
import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 饮食查询工具（只读，含明细）。
 */
@Component
public class DietQueryToolExecutor implements ToolExecutor {

    @Resource
    private DietLogMapper dietLogMapper;

    @Resource
    private DietItemMapper dietItemMapper;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "diet.query",
                "查询当前用户最近饮食记录（含食物明细），参数: {\"days\": 1-180, \"limit\": 1-50}",
                "{\"type\":\"object\",\"properties\":{\"days\":{\"type\":\"integer\"},\"limit\":{\"type\":\"integer\"}}}",
                true
        );
    }

    @Override
    public ToolResult execute(ToolCall call, AuthenticatedUserContext authenticatedUser) {
        if (authenticatedUser == null || authenticatedUser.getUserId() == null) {
            return ToolResult.error("用户上下文为空");
        }
        Map<String, Object> args = call.getArguments();
        int days = normalizeNumber(args == null ? null : args.get("days"), 30, 180);
        int limit = normalizeNumber(args == null ? null : args.get("limit"), 20, 50);
        QueryWrapper<DietLog> query = new QueryWrapper<>();
        query.eq("user_id", authenticatedUser.getUserId())
                .ge("record_date", LocalDate.now().minusDays(days))
                .orderByDesc("record_date")
                .last("LIMIT " + limit);
        List<DietLog> logs = dietLogMapper.selectList(query);
        if (logs.isEmpty()) {
            return ToolResult.ok("未查询到饮食记录", logs);
        }
        // 批量查明细
        List<Long> logIds = logs.stream().map(DietLog::getId).collect(Collectors.toList());
        List<DietItem> items = dietItemMapper.selectList(
                new QueryWrapper<DietItem>().in("diet_log_id", logIds));
        Map<Long, List<DietItem>> itemMap = items.stream()
                .collect(Collectors.groupingBy(DietItem::getDietLogId));
        for (DietLog log : logs) {
            log.setItems(itemMap.getOrDefault(log.getId(), new ArrayList<>()));
        }
        return ToolResult.ok("已查询到饮食记录 " + logs.size() + " 条", logs);
    }

    private int normalizeNumber(Object value, int defaultValue, int maxValue) {
        int result = defaultValue;
        if (value instanceof Number n) {
            result = n.intValue();
        } else if (value instanceof String s) {
            try { result = Integer.parseInt(s); } catch (NumberFormatException ignored) { }
        }
        if (result < 1) result = 1;
        if (result > maxValue) result = maxValue;
        return result;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=DietQueryToolExecutorTest test`
Expected: PASS

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/agent/tool/DietQueryToolExecutor.java \
        FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/agent/tool/DietQueryToolExecutorTest.java
git commit -m "feat: add diet.query tool with item aggregation"
```

---

## Phase 5: 配置与 Prompt

### Task 19: application.yml enabled-tools 追加 + agent-system.md 补充引导

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/resources/application.yml`（第 103-111 行 enabled-tools 列表）
- Modify: `FitMate-backend/FitMate-api/src/main/resources/prompts/agent-system.md`（追加"数据记录规则"节）

- [ ] **Step 1: 修改 application.yml，在 enabled-tools 列表追加 8 个工具**

在 `application.yml` 第 111 行（`      - memory.search`）之后追加：

```yaml
      - training_log.record
      - cardio.record
      - cardio.query
      - body_metrics.record
      - heart_rate.record
      - heart_rate.query
      - diet.record
      - diet.query
```

最终 enabled-tools 应为：

```yaml
    enabled-tools:
      - date.now
      - kb.search
      - rag.search
      - body_metrics.query
      - training_log.query
      - web.search
      - web.fetch
      - memory.search
      - training_log.record
      - cardio.record
      - cardio.query
      - body_metrics.record
      - heart_rate.record
      - heart_rate.query
      - diet.record
      - diet.query
```

- [ ] **Step 2: 修改 agent-system.md，在文件末尾追加"数据记录规则"节**

在 `agent-system.md` 末尾追加：

```markdown

## 数据记录规则

1. 用户通过对话提供训练/身体/饮食等数据时，调用对应的 record 工具记录（upsert，按日期自动新增或更新）。
2. 可用 record 工具：`training_log.record`（力量训练）、`cardio.record`（有氧训练）、`body_metrics.record`（身体指标含围度）、`heart_rate.record`（心率）、`diet.record`（饮食）。
3. **更新已有记录时，必须先调用对应的 query 工具获取既有记录，合并完整信息后再调 record**。record 采用全量覆盖策略，未传字段会被清空。
4. 配速（有氧）与总热量/宏量（饮食）由后端自动计算，无需在参数中提供。
5. cardio_type 用英文枚举值（running/cycling/swimming/rowing/jump_rope/other），meal_type 用英文枚举值（breakfast/lunch/dinner/snack）。
6. date 参数格式必须为 yyyy-MM-dd；如用户说"今天"且不知日期，先调 `date.now` 获取。
7. 记录成功后，用自然语言向用户确认记录内容（如"已记录今天的跑步 5km/30min"）。
```

- [ ] **Step 3: 全量编译验证**

Run: `mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 全量测试验证**

Run: `mvn -pl FitMate-api test`
Expected: BUILD SUCCESS，所有测试通过

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/resources/application.yml \
        FitMate-backend/FitMate-api/src/main/resources/prompts/agent-system.md
git commit -m "feat: enable record/query tools in whitelist and add prompt guidance"
```

---

## 完成后手动验证清单

- [ ] 在已有数据库执行 `fitmate_init.sql` 末尾的 ALTER 升级脚本（t_body_metrics 增加 5 个围度字段）
- [ ] 确认新表（t_cardio_log / t_heart_rate / t_diet_log / t_diet_item）已由 CREATE TABLE IF NOT EXISTS 创建（新建库自动，已有库需手动执行）
- [ ] 启动后端服务
- [ ] 通过对话测试每个 record 工具：
  - "今天做了卧推 3 组 10 次 60kg" → 触发 training_log.record
  - "今天跑了 5 公里 30 分钟" → 触发 cardio.record
  - "今天体重 70.5kg 体脂 18%" → 触发 body_metrics.record
  - "今天静息心率 60" → 触发 heart_rate.record
  - "今天早餐吃了两个鸡蛋" → 触发 diet.record
- [ ] 验证同日重复记录触发 upsert 更新而非新增
- [ ] 验证 query 工具能查到 record 工具写入的数据
- [ ] 验证更新流程：先 query 再 record 合并修改
