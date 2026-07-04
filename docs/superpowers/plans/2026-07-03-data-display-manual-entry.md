# 数据展示、手动添加与自动计算扩展 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 扩展前端 Training/Metrics 页支持 Tab 切换的手动添加 cardio/heart_rate/diet/围度数据，新增 3 个后端 Controller，并完善自动计算（训练量/肌群/卡路里/BMI/变化率/汇总）。

**Architecture:** 后端补 3 个 Controller + 2 个 summary 接口 + 2 个硬编码字典文件 + Service 自动计算扩展；前端 Training 页扩 3 Tab、Metrics 页扩 2 Tab，沿用 ChatLogicBase 继承模式与现有 Tailwind+M3 风格，不引入图表库。

**Tech Stack:** Spring Boot + MyBatis-Plus + JUnit 5 + Mockito；Vue 3 Options API（extends ChatLogicBase）+ Tailwind CSS + Material Symbols。

**Spec:** `docs/superpowers/specs/2026-07-03-data-display-manual-entry-design.md`

---

## 文件结构

### 后端新增
- `fitness/training/domain/MuscleGroupDictionary.java` — 肌群关键词字典（独立硬编码文件）
- `fitness/cardio/domain/CardioMetTable.java` — 有氧 MET 值表（独立硬编码文件）
- `fitness/cardio/controller/CardioController.java` — 有氧训练 REST 接口
- `fitness/heartrate/controller/HeartRateController.java` — 心率 REST 接口
- `fitness/diet/controller/DietController.java` — 饮食 REST 接口
- `fitness/training/dto/TrainingSummaryDTO.java` — 训练派生指标 DTO
- `fitness/metrics/dto/BodyMetricsSummaryDTO.java` — 身体派生指标 DTO
- 对应测试类（5 个）

### 后端修改
- `fitness/training/application/impl/TrainingServiceImpl.java` — 补 primaryMuscleGroup 推断 + getTrainingSummary 方法
- `fitness/training/application/TrainingService.java` — 接口加 getTrainingSummary
- `fitness/cardio/application/impl/CardioServiceImpl.java` — 注入 BodyMetricsMapper + caloriesBurned 计算 + getRecentCardio 方法
- `fitness/cardio/application/CardioService.java` — 接口加 getRecentCardio
- `fitness/heartrate/application/impl/HeartRateServiceImpl.java` — 加 getRecentHeartRate 方法
- `fitness/heartrate/application/HeartRateService.java` — 接口加 getRecentHeartRate
- `fitness/diet/application/impl/DietServiceImpl.java` — 加 getRecentDiet 方法
- `fitness/diet/application/DietService.java` — 接口加 getRecentDiet
- `fitness/metrics/application/impl/BodyMetricsServiceImpl.java` — 加 getBodyMetricsSummary 方法
- `fitness/metrics/application/BodyMetricsService.java` — 接口加 getBodyMetricsSummary
- `fitness/training/controller/TrainingController.java` — 加 /summary 接口
- `fitness/metrics/controller/BodyMetricsController.java` — 加 /summary 接口
- `auth/application/UserPreferenceService.java` — 加 getHeightCm 方法

### 前端修改
- `pages/training/TrainingPage.vue` — 3 Tab + 有氧表单 + 饮食表单 + aside summary
- `pages/metrics/MetricsPage.vue` — 2 Tab + 围度录入 + 心率表单 + aside summary
- `services/doctorApi.ts` — 8 个新函数
- `pages/chat/ChatLogicBase.vue` — 新增数据字段与 fetch 方法

---

## Task 1: MuscleGroupDictionary + 测试

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/domain/MuscleGroupDictionary.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/training/domain/MuscleGroupDictionaryTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.fitness.training.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MuscleGroupDictionaryTest {

    @Test
    void inferMuscleGroup_benchPress_returnsChest() {
        assertEquals("胸肌", MuscleGroupDictionary.inferMuscleGroup("杠铃卧推"));
    }

    @Test
    void inferMuscleGroup_squat_returnsQuads() {
        assertEquals("股四头肌", MuscleGroupDictionary.inferMuscleGroup("杠铃深蹲"));
    }

    @Test
    void inferMuscleGroup_deadlift_returnsBack() {
        assertEquals("背部", MuscleGroupDictionary.inferMuscleGroup("传统硬拉"));
    }

    @Test
    void inferMuscleGroup_pullUp_returnsLats() {
        assertEquals("背阔肌", MuscleGroupDictionary.inferMuscleGroup("引体向上"));
    }

    @Test
    void inferMuscleGroup_bicepCurl_returnsBiceps() {
        assertEquals("肱二头肌", MuscleGroupDictionary.inferMuscleGroup("哑铃弯举"));
    }

    @Test
    void inferMuscleGroup_unknownMovement_returnsNull() {
        assertNull(MuscleGroupDictionary.inferMuscleGroup("未知动作"));
    }

    @Test
    void inferMuscleGroup_nullOrBlank_returnsNull() {
        assertNull(MuscleGroupDictionary.inferMuscleGroup(null));
        assertNull(MuscleGroupDictionary.inferMuscleGroup(""));
        assertNull(MuscleGroupDictionary.inferMuscleGroup("   "));
    }

    @Test
    void inferMuscleGroup_priorityMultipleMatches_returnsFirstMatch() {
        // "卧推" 优先于 "推举"，应返回胸肌
        assertEquals("胸肌", MuscleGroupDictionary.inferMuscleGroup("上斜卧推"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=MuscleGroupDictionaryTest test`
Expected: FAIL（类不存在，编译错误）

- [ ] **Step 3: 写实现**

```java
package com.itgeo.fitmate.api.fitness.training.domain;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 训练动作关键词到主肌群的映射字典。
 * 其他类只导入此文件，不内联硬编码。
 */
public final class MuscleGroupDictionary {

    private MuscleGroupDictionary() {
    }

    /** 动作名关键词 → 肌群映射，按匹配优先级排列（靠前优先）。 */
    private static final LinkedHashMap<String, String> KEYWORD_TO_GROUP = new LinkedHashMap<>();

    static {
        KEYWORD_TO_GROUP.put("卧推", "胸肌");
        KEYWORD_TO_GROUP.put("胸推", "胸肌");
        KEYWORD_TO_GROUP.put("飞鸟", "胸肌");
        KEYWORD_TO_GROUP.put("夹胸", "胸肌");
        KEYWORD_TO_GROUP.put("深蹲", "股四头肌");
        KEYWORD_TO_GROUP.put("腿举", "股四头肌");
        KEYWORD_TO_GROUP.put("腿屈伸", "股四头肌");
        KEYWORD_TO_GROUP.put("箭步蹲", "股四头肌");
        KEYWORD_TO_GROUP.put("腿弯举", "腘绳肌");
        KEYWORD_TO_GROUP.put("硬拉", "背部");
        KEYWORD_TO_GROUP.put("划船", "背部");
        KEYWORD_TO_GROUP.put("引体", "背阔肌");
        KEYWORD_TO_GROUP.put("高位下拉", "背阔肌");
        KEYWORD_TO_GROUP.put("推举", "肩部");
        KEYWORD_TO_GROUP.put("侧平举", "肩部");
        KEYWORD_TO_GROUP.put("前平举", "肩部");
        KEYWORD_TO_GROUP.put("弯举", "肱二头肌");
        KEYWORD_TO_GROUP.put("臂屈伸", "肱三头肌");
        KEYWORD_TO_GROUP.put("屈臂", "肱二头肌");
    }

    /**
     * 根据动作名推断主肌群。
     *
     * @param exerciseName 动作名称
     * @return 肌群中文名，未匹配返回 null
     */
    public static String inferMuscleGroup(String exerciseName) {
        if (exerciseName == null || exerciseName.isBlank()) {
            return null;
        }
        for (Map.Entry<String, String> entry : KEYWORD_TO_GROUP.entrySet()) {
            if (exerciseName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=MuscleGroupDictionaryTest test`
Expected: PASS（8 个测试）

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/domain/MuscleGroupDictionary.java FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/training/domain/MuscleGroupDictionaryTest.java
git commit -m "feat: add MuscleGroupDictionary for muscle group inference"
```

---

## Task 2: CardioMetTable + 测试

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/domain/CardioMetTable.java`
- Test: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/cardio/domain/CardioMetTableTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.itgeo.fitmate.api.fitness.cardio.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardioMetTableTest {

    @Test
    void getMet_running_returns9_8() {
        assertEquals(9.8, CardioMetTable.getMet("running"));
    }

    @Test
    void getMet_cycling_returns7_5() {
        assertEquals(7.5, CardioMetTable.getMet("cycling"));
    }

    @Test
    void getMet_swimming_returns8_0() {
        assertEquals(8.0, CardioMetTable.getMet("swimming"));
    }

    @Test
    void getMet_rowing_returns7_0() {
        assertEquals(7.0, CardioMetTable.getMet("rowing"));
    }

    @Test
    void getMet_jumpRope_returns12_0() {
        assertEquals(12.0, CardioMetTable.getMet("jump_rope"));
    }

    @Test
    void getMet_other_returns6_0() {
        assertEquals(6.0, CardioMetTable.getMet("other"));
    }

    @Test
    void getMet_unknownType_returnsDefault6_0() {
        assertEquals(6.0, CardioMetTable.getMet("unknown_type"));
    }

    @Test
    void getMet_nullType_returnsDefault6_0() {
        assertEquals(6.0, CardioMetTable.getMet(null));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=CardioMetTableTest test`
Expected: FAIL（类不存在）

- [ ] **Step 3: 写实现**

```java
package com.itgeo.fitmate.api.fitness.cardio.domain;

import java.util.Map;

/**
 * 有氧运动类型到 MET（代谢当量）值的映射表。
 * 其他类只导入此文件，不内联硬编码。
 */
public final class CardioMetTable {

    private CardioMetTable() {
    }

    private static final Map<String, Double> MET_BY_TYPE = Map.of(
            "running", 9.8,
            "cycling", 7.5,
            "swimming", 8.0,
            "rowing", 7.0,
            "jump_rope", 12.0,
            "other", 6.0
    );

    private static final double DEFAULT_MET = 6.0;

    /**
     * 获取指定有氧类型的 MET 值。
     *
     * @param cardioType 有氧类型（running/cycling/swimming/rowing/jump_rope/other）
     * @return MET 值，未知类型或 null 返回 6.0
     */
    public static double getMet(String cardioType) {
        if (cardioType == null) {
            return DEFAULT_MET;
        }
        return MET_BY_TYPE.getOrDefault(cardioType, DEFAULT_MET);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=CardioMetTableTest test`
Expected: PASS（8 个测试）

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/domain/CardioMetTable.java FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/cardio/domain/CardioMetTableTest.java
git commit -m "feat: add CardioMetTable for MET lookup"
```

---

## Task 3: TrainingServiceImpl 补 primaryMuscleGroup 推断 + 测试扩展

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/application/impl/TrainingServiceImpl.java`
- Modify: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/training/application/impl/TrainingServiceImplTest.java`

**说明**：total_volume 已实现（见 TrainingServiceImpl.java:73-81），本任务只补 primaryMuscleGroup 推断。

- [ ] **Step 1: 写失败测试（追加到现有测试类）**

在 `TrainingServiceImplTest.java` 末尾追加 2 个测试方法（保留现有 2 个测试不变）：

```java
    @Test
    void logTraining_withBenchPress_setsPrimaryMuscleGroupChest() {
        // 准备：卧推动作
        TrainingLogRequest request = new TrainingLogRequest();
        request.setDate("2026-07-03");
        TrainingExerciseItem item = new TrainingExerciseItem();
        item.setName("杠铃卧推");
        item.setSets(3);
        item.setReps(10);
        item.setWeight(new BigDecimal("60"));
        request.setExercises(java.util.Collections.singletonList(item));

        // 执行
        trainingService.logTraining(1L, request, "chat");

        // 验证：primaryMuscleGroup 被推断为胸肌
        ArgumentCaptor<TrainingLog> captor = ArgumentCaptor.forClass(TrainingLog.class);
        verify(trainingLogMapper).insert(captor.capture());
        TrainingLog saved = captor.getValue();
        assertEquals("胸肌", saved.getPrimaryMuscleGroup());
    }

    @Test
    void logTraining_withMultipleMovements_setsMostFrequentMuscleGroup() {
        // 准备：2 个胸肌动作 + 1 个肩部动作，应推断为胸肌
        TrainingLogRequest request = new TrainingLogRequest();
        request.setDate("2026-07-03");
        TrainingExerciseItem benchPress = new TrainingExerciseItem();
        benchPress.setName("卧推");
        benchPress.setSets(3);
        benchPress.setReps(10);
        benchPress.setWeight(new BigDecimal("60"));
        TrainingExerciseItem inclinePress = new TrainingExerciseItem();
        inclinePress.setName("上斜卧推");
        inclinePress.setSets(3);
        inclinePress.setReps(10);
        inclinePress.setWeight(new BigDecimal("50"));
        TrainingExerciseItem shoulderPress = new TrainingExerciseItem();
        shoulderPress.setName("推举");
        shoulderPress.setSets(3);
        shoulderPress.setReps(10);
        shoulderPress.setWeight(new BigDecimal("40"));
        request.setExercises(java.util.Arrays.asList(benchPress, inclinePress, shoulderPress));

        // 执行
        trainingService.logTraining(1L, request, "chat");

        // 验证：胸肌出现 2 次，肩部 1 次，推断为胸肌
        ArgumentCaptor<TrainingLog> captor = ArgumentCaptor.forClass(TrainingLog.class);
        verify(trainingLogMapper).insert(captor.capture());
        TrainingLog saved = captor.getValue();
        assertEquals("胸肌", saved.getPrimaryMuscleGroup());
    }
```

注意：若现有测试类未 import `ArgumentCaptor`，需补充 `import org.mockito.ArgumentCaptor;`。

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=TrainingServiceImplTest test`
Expected: 2 个新测试 FAIL（primaryMuscleGroup 为 null，断言失败）

- [ ] **Step 3: 修改 TrainingServiceImpl**

在 `TrainingServiceImpl.java` 中：

1. 添加 import：
```java
import com.itgeo.fitmate.api.fitness.training.domain.MuscleGroupDictionary;
import java.util.HashMap;
import java.util.Map;
```

2. 在 `logTraining(Long userId, TrainingLogRequest request, String source)` 方法中，找到计算 totalVolume 的循环之后（约第 81 行），追加肌群推断逻辑：

```java
        // 推断主肌群：统计每个动作匹配的肌群频次，取最高
        Map<String, Integer> muscleGroupCount = new HashMap<>();
        for (TrainingExerciseItem item : validExercises) {
            String group = MuscleGroupDictionary.inferMuscleGroup(item.getName());
            if (group != null) {
                muscleGroupCount.merge(group, 1, Integer::sum);
            }
        }
        String primaryMuscleGroup = muscleGroupCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
```

3. 在 existing 分支（约第 93-95 行之间，setSource 之前）追加：
```java
            existing.setPrimaryMuscleGroup(primaryMuscleGroup);
```

4. 在 new 分支（约第 107 行，`log.setPrimaryMuscleGroup(null);` 改为）：
```java
            log.setPrimaryMuscleGroup(primaryMuscleGroup);
```

- [ ] **Step 4: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=TrainingServiceImplTest test`
Expected: PASS（4 个测试，含原有 2 个 + 新增 2 个）

- [ ] **Step 5: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/application/impl/TrainingServiceImpl.java FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/training/application/impl/TrainingServiceImplTest.java
git commit -m "feat: infer primaryMuscleGroup in TrainingServiceImpl"
```

---

## Task 4: CardioServiceImpl 补 caloriesBurned + getRecentCardio + 测试扩展

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/application/CardioService.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/application/impl/CardioServiceImpl.java`
- Modify: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/cardio/application/impl/CardioServiceImplTest.java`

- [ ] **Step 1: 写失败测试（追加到现有测试类）**

```java
    @Test
    void logCardio_withWeight_calculatesCaloriesBurned() {
        // 准备：有最近体重记录 70kg
        com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics bm =
                new com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics();
        bm.setWeight(new BigDecimal("70"));
        when(bodyMetricsMapper.selectOne(any())).thenReturn(bm);

        CardioLogRequest request = new CardioLogRequest();
        request.setDate("2026-07-03");
        request.setCardioType("running");
        request.setDistanceKm(new BigDecimal("5"));
        request.setDurationMinutes(30);

        // 执行
        cardioService.logCardio(1L, request, "manual");

        // 验证：caloriesBurned = 9.8 × 70 × 0.5 = 343
        ArgumentCaptor<CardioLog> captor = ArgumentCaptor.forClass(CardioLog.class);
        verify(cardioLogMapper).insert(captor.capture());
        CardioLog saved = captor.getValue();
        assertEquals(343, saved.getCaloriesBurned());
    }

    @Test
    void logCardio_noWeightRecord_caloriesBurnedNull() {
        // 准备：无体重记录
        when(bodyMetricsMapper.selectOne(any())).thenReturn(null);

        CardioLogRequest request = new CardioLogRequest();
        request.setDate("2026-07-03");
        request.setCardioType("running");
        request.setDistanceKm(new BigDecimal("5"));
        request.setDurationMinutes(30);

        // 执行
        cardioService.logCardio(1L, request, "manual");

        // 验证：caloriesBurned 为 null（不报错）
        ArgumentCaptor<CardioLog> captor = ArgumentCaptor.forClass(CardioLog.class);
        verify(cardioLogMapper).insert(captor.capture());
        CardioLog saved = captor.getValue();
        assertNull(saved.getCaloriesBurned());
    }
```

注意：需补充 import `import static org.junit.jupiter.api.Assertions.assertNull;` 和 `import org.mockito.ArgumentCaptor;`。

同时需要修改现有测试的 setup：由于构造器从 1 个参数变 2 个，所有 `new CardioServiceImpl(cardioLogMapper)` 都要改为 `new CardioServiceImpl(cardioLogMapper, bodyMetricsMapper)`，并在 `@BeforeEach` 中添加 `bodyMetricsMapper = Mockito.mock(BodyMetricsMapper.class);`。

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=CardioServiceImplTest test`
Expected: FAIL（编译错误：构造器参数不匹配）

- [ ] **Step 3: 修改 CardioService 接口**

在 `CardioService.java` 追加：

```java
    /**
     * 查询最近有氧训练摘要。
     *
     * @param userId 用户ID
     * @param limit  返回条数
     * @return 日期摘要列表
     */
    java.util.List<com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem> getRecentCardio(Long userId, Integer limit);
```

- [ ] **Step 4: 修改 CardioServiceImpl**

1. 添加 import：
```java
import com.itgeo.fitmate.api.fitness.cardio.domain.CardioMetTable;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.entity.BodyMetrics;
import com.itgeo.fitmate.api.fitness.metrics.infrastructure.mapper.BodyMetricsMapper;
import com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem;
import java.util.List;
import java.util.stream.Collectors;
```

2. 修改构造器与字段（替换现有构造器）：
```java
    private final CardioLogMapper cardioLogMapper;
    private final BodyMetricsMapper bodyMetricsMapper;

    public CardioServiceImpl(CardioLogMapper cardioLogMapper, BodyMetricsMapper bodyMetricsMapper) {
        this.cardioLogMapper = cardioLogMapper;
        this.bodyMetricsMapper = bodyMetricsMapper;
    }
```

3. 在 `logCardio` 方法中，`String normalizedSource = normalizeSource(source);` 之后追加 caloriesBurned 计算：
```java
        Integer caloriesBurned = calculateCaloriesBurned(request.getCardioType(), request.getDurationMinutes(), userId);
```

4. 在 existing 分支（`existing.setSource(normalizedSource);` 之前）追加：
```java
            existing.setCaloriesBurned(caloriesBurned);
```

5. 在 new 分支（`entity.setSource(normalizedSource);` 之前）追加：
```java
            entity.setCaloriesBurned(caloriesBurned);
```

6. 在 buildSummary 方法中，avgHeartRate 之后追加卡路里展示（可选）：
```java
        if (req.getCardioType() != null) {
            // caloriesBurned 在调用方设置后，summary 由调用时构造，此处不重复
        }
```
实际上 buildSummary 在 caloriesBurned 计算前调用，需要调整顺序。改为先计算 caloriesBurned 再 buildSummary，并在 buildSummary 签名中传入 caloriesBurned。

**调整后的 logCardio 关键片段**：
```java
        String avgPace = calculatePace(request.getDistanceKm(), request.getDurationMinutes());
        Integer caloriesBurned = calculateCaloriesBurned(request.getCardioType(), request.getDurationMinutes(), userId);
        String summary = buildSummary(request, avgPace, caloriesBurned);
        String normalizedSource = normalizeSource(source);
```

**修改 buildSummary 签名**：
```java
    private String buildSummary(CardioLogRequest req, String avgPace, Integer caloriesBurned) {
        // ... 现有逻辑不变 ...
        // 末尾追加：
        if (caloriesBurned != null) {
            sb.append(" / 消耗 ").append(caloriesBurned).append("kcal");
        }
        return sb.toString();
    }
```

7. 添加 calculateCaloriesBurned 私有方法：
```java
    private Integer calculateCaloriesBurned(String cardioType, Integer durationMinutes, Long userId) {
        if (durationMinutes == null || durationMinutes <= 0) {
            return null;
        }
        BodyMetrics latest = bodyMetricsMapper.selectOne(
                new LambdaQueryWrapper<BodyMetrics>()
                        .eq(BodyMetrics::getUserId, userId)
                        .isNotNull(BodyMetrics::getWeight)
                        .orderByDesc(BodyMetrics::getRecordDate)
                        .last("limit 1")
        );
        if (latest == null || latest.getWeight() == null) {
            return null;
        }
        double met = CardioMetTable.getMet(cardioType);
        double weightKg = latest.getWeight().doubleValue();
        double durationH = durationMinutes / 60.0;
        return (int) Math.round(met * weightKg * durationH);
    }
```

8. 添加 getRecentCardio 方法实现：
```java
    @Override
    public List<DateSummaryItem> getRecentCardio(Long userId, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        int safeLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        List<CardioLog> logs = cardioLogMapper.selectList(
                new LambdaQueryWrapper<CardioLog>()
                        .eq(CardioLog::getUserId, userId)
                        .orderByDesc(CardioLog::getTrainingDate)
                        .last("limit " + safeLimit)
        );
        return logs.stream()
                .map(log -> new DateSummaryItem(
                        log.getTrainingDate() == null ? null : log.getTrainingDate().toString(),
                        log.getSummary()))
                .collect(Collectors.toList());
    }
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=CardioServiceImplTest test`
Expected: PASS（7 个测试，含原有 5 个 + 新增 2 个）

- [ ] **Step 6: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/
git commit -m "feat: add caloriesBurned calculation and getRecentCardio to CardioService"
```

---

## Task 5: HeartRateService 加 getRecentHeartRate

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/application/HeartRateService.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/application/impl/HeartRateServiceImpl.java`
- Modify: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/heartrate/application/impl/HeartRateServiceImplTest.java`

- [ ] **Step 1: 写失败测试（追加）**

```java
    @Test
    void getRecentHeartRate_returnsDateSummaryList() {
        com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate hr1 =
                new com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate();
        hr1.setRecordDate(java.time.LocalDate.of(2026, 7, 3));
        hr1.setSummary("静息心率 60");
        com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate hr2 =
                new com.itgeo.fitmate.api.fitness.heartrate.infrastructure.entity.HeartRate();
        hr2.setRecordDate(java.time.LocalDate.of(2026, 7, 2));
        hr2.setSummary("静息心率 62");
        when(heartRateMapper.selectList(any())).thenReturn(java.util.Arrays.asList(hr1, hr2));

        java.util.List<com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem> result =
                heartRateService.getRecentHeartRate(1L, 10);

        assertEquals(2, result.size());
        assertEquals("2026-07-03", result.get(0).getDate());
        assertEquals("静息心率 60", result.get(0).getSummary());
    }
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=HeartRateServiceImplTest test`
Expected: FAIL（方法不存在）

- [ ] **Step 3: 修改接口**

在 `HeartRateService.java` 追加：
```java
    java.util.List<com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem> getRecentHeartRate(Long userId, Integer limit);
```

- [ ] **Step 4: 修改实现**

在 `HeartRateServiceImpl.java` 添加 import 和方法实现：

```java
import com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem;
import java.util.List;
import java.util.stream.Collectors;
```

方法实现：
```java
    @Override
    public List<DateSummaryItem> getRecentHeartRate(Long userId, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        int safeLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        List<HeartRate> logs = heartRateMapper.selectList(
                new LambdaQueryWrapper<HeartRate>()
                        .eq(HeartRate::getUserId, userId)
                        .orderByDesc(HeartRate::getRecordDate)
                        .last("limit " + safeLimit)
        );
        return logs.stream()
                .map(log -> new DateSummaryItem(
                        log.getRecordDate() == null ? null : log.getRecordDate().toString(),
                        log.getSummary()))
                .collect(Collectors.toList());
    }
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=HeartRateServiceImplTest test`
Expected: PASS（5 个测试）

- [ ] **Step 6: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/
git commit -m "feat: add getRecentHeartRate to HeartRateService"
```

---

## Task 6: DietService 加 getRecentDiet

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/application/DietService.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/application/impl/DietServiceImpl.java`
- Modify: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/diet/application/impl/DietServiceImplTest.java`

- [ ] **Step 1: 写失败测试（追加）**

```java
    @Test
    void getRecentDiet_returnsDateSummaryList() {
        com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog log1 =
                new com.itgeo.fitmate.api.fitness.diet.infrastructure.entity.DietLog();
        log1.setRecordDate(java.time.LocalDate.of(2026, 7, 3));
        log1.setSummary("早餐 共 290kcal");
        when(dietLogMapper.selectList(any())).thenReturn(java.util.Collections.singletonList(log1));

        java.util.List<com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem> result =
                dietService.getRecentDiet(1L, 10);

        assertEquals(1, result.size());
        assertEquals("2026-07-03", result.get(0).getDate());
        assertEquals("早餐 共 290kcal", result.get(0).getSummary());
    }
```

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=DietServiceImplTest test`
Expected: FAIL（方法不存在）

- [ ] **Step 3: 修改接口**

在 `DietService.java` 追加：
```java
    java.util.List<com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem> getRecentDiet(Long userId, Integer limit);
```

- [ ] **Step 4: 修改实现**

在 `DietServiceImpl.java` 添加 import 和方法实现：

```java
import com.itgeo.fitmate.api.fitness.training.dto.DateSummaryItem;
import java.util.List;
import java.util.stream.Collectors;
```

方法实现：
```java
    @Override
    public List<DateSummaryItem> getRecentDiet(Long userId, Integer limit) {
        if (userId == null) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        int safeLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);
        List<DietLog> logs = dietLogMapper.selectList(
                new LambdaQueryWrapper<DietLog>()
                        .eq(DietLog::getUserId, userId)
                        .orderByDesc(DietLog::getRecordDate)
                        .last("limit " + safeLimit)
        );
        return logs.stream()
                .map(log -> new DateSummaryItem(
                        log.getRecordDate() == null ? null : log.getRecordDate().toString(),
                        log.getSummary()))
                .collect(Collectors.toList());
    }
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=DietServiceImplTest test`
Expected: PASS（5 个测试）

- [ ] **Step 6: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/
git commit -m "feat: add getRecentDiet to DietService"
```

---

## Task 7: UserPreferenceService 加 getHeightCm

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/application/UserPreferenceService.java`（或等价位置，需先确认现有文件）
- Test: 先读现有 UserPreferenceService 确认位置与测试类

**说明**：此任务需先读取现有 UserPreferenceService 代码确认结构。若项目中已有 `UserPreferenceService`，在其追加 `getHeightCm`；若无，新建一个。

- [ ] **Step 1: 先读取现有代码确认位置**

Run: 用 Glob 搜索 `**/UserPreferenceService.java` 或 `**/PreferenceService.java`，确认文件路径。

- [ ] **Step 2: 写失败测试**

根据现有结构写测试（假设文件在 `auth/application/UserPreferenceService.java`，用 Jackson 解析 preferences_json）：

```java
@Test
void getHeightCm_withHeightConfigured_returnsValue() throws Exception {
    // 准备：preferences_json = {"themeMode":"dark","heightCm":175}
    UserPreference pref = new UserPreference();
    pref.setPreferencesJson("{\"themeMode\":\"dark\",\"heightCm\":175}");
    when(userPreferenceMapper.selectOne(any())).thenReturn(pref);

    Integer height = preferenceService.getHeightCm(1L);

    assertEquals(175, height);
}

@Test
void getHeightCm_notConfigured_returnsNull() {
    UserPreference pref = new UserPreference();
    pref.setPreferencesJson("{\"themeMode\":\"dark\"}");
    when(userPreferenceMapper.selectOne(any())).thenReturn(pref);

    Integer height = preferenceService.getHeightCm(1L);

    assertNull(height);
}

@Test
void getHeightCm_noPreferenceRecord_returnsNull() {
    when(userPreferenceMapper.selectOne(any())).thenReturn(null);

    Integer height = preferenceService.getHeightCm(1L);

    assertNull(height);
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=UserPreferenceServiceTest test`（或现有测试类名）
Expected: FAIL（方法不存在）

- [ ] **Step 4: 实现 getHeightCm**

在 `UserPreferenceService.java` 追加：

```java
    /**
     * 获取用户身高（cm）。
     *
     * @param userId 用户ID
     * @return 身高 cm，未配置返回 null
     */
    public Integer getHeightCm(Long userId) {
        UserPreference pref = userPreferenceMapper.selectOne(
                new LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, userId)
                        .last("limit 1")
        );
        if (pref == null || pref.getPreferencesJson() == null) {
            return null;
        }
        try {
            ObjectNode node = new ObjectMapper().readValue(pref.getPreferencesJson(), ObjectNode.class);
            if (node.has("heightCm") && node.get("heightCm").isNumber()) {
                return node.get("heightCm").asInt();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
```

注意：需补充对应 import（`com.fasterxml.jackson.databind.ObjectMapper`、`com.fasterxml.jackson.databind.node.ObjectNode`、`com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper`、UserPreference 实体与 Mapper）。具体 import 路径需根据现有代码确认。

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=UserPreferenceServiceTest test`
Expected: PASS

- [ ] **Step 6: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/auth/
git commit -m "feat: add getHeightCm to UserPreferenceService"
```

---

## Task 8: TrainingSummaryDTO + BodyMetricsSummaryDTO

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/dto/TrainingSummaryDTO.java`
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/dto/BodyMetricsSummaryDTO.java`

- [ ] **Step 1: 创建 TrainingSummaryDTO**

```java
package com.itgeo.fitmate.api.fitness.training.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 训练派生指标 DTO，供前端 aside 展示。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TrainingSummaryDTO {
    /** 最近 7 天总训练量。 */
    private BigDecimal weekVolume;
    /** 最近 30 天总训练量。 */
    private BigDecimal monthVolume;
    /** 最近 7 天训练天数。 */
    private Integer weekTrainingDays;
    /** 最近 30 天训练天数。 */
    private Integer monthTrainingDays;
}
```

- [ ] **Step 2: 创建 BodyMetricsSummaryDTO**

```java
package com.itgeo.fitmate.api.fitness.metrics.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 身体指标派生指标 DTO，供前端 aside 展示。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BodyMetricsSummaryDTO {
    /** BMI，身高未配置时为 null。 */
    private BigDecimal bmi;
    /** 体重变化率（%），最新体重相对上一次的变化率。 */
    private BigDecimal weightChangeRate;
    /** 最新体重。 */
    private BigDecimal latestWeight;
    /** 上一次体重。 */
    private BigDecimal previousWeight;
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/dto/TrainingSummaryDTO.java FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/dto/BodyMetricsSummaryDTO.java
git commit -m "feat: add TrainingSummaryDTO and BodyMetricsSummaryDTO"
```

---

## Task 9: TrainingService 加 getTrainingSummary + 实现

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/application/TrainingService.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/application/impl/TrainingServiceImpl.java`
- Modify: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/training/application/impl/TrainingServiceImplTest.java`

- [ ] **Step 1: 写失败测试（追加）**

```java
    @Test
    void getTrainingSummary_calculatesWeekAndMonthAggregates() {
        // 准备：构造 3 条最近 7 天内 + 1 条 30 天内 7 天外的训练记录
        TrainingLog recent1 = new TrainingLog();
        recent1.setTotalVolume(new BigDecimal("5000"));
        recent1.setTrainingDate(java.time.LocalDate.now());
        TrainingLog recent2 = new TrainingLog();
        recent2.setTotalVolume(new BigDecimal("3000"));
        recent2.setTrainingDate(java.time.LocalDate.now().minusDays(3));
        TrainingLog old = new TrainingLog();
        old.setTotalVolume(new BigDecimal("8000"));
        old.setTrainingDate(java.time.LocalDate.now().minusDays(20));
        when(trainingLogMapper.selectList(any())).thenReturn(java.util.Arrays.asList(recent1, recent2, old));

        // 执行
        TrainingSummaryDTO summary = trainingService.getTrainingSummary(1L);

        // 验证
        assertEquals(new BigDecimal("8000"), summary.getWeekVolume());
        assertEquals(new BigDecimal("16000"), summary.getMonthVolume());
        assertEquals(2, summary.getWeekTrainingDays());
        assertEquals(3, summary.getMonthTrainingDays());
    }
```

注意：需 import `TrainingSummaryDTO`。

- [ ] **Step 2: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=TrainingServiceImplTest test`
Expected: FAIL（方法不存在）

- [ ] **Step 3: 修改接口**

在 `TrainingService.java` 追加：
```java
    com.itgeo.fitmate.api.fitness.training.dto.TrainingSummaryDTO getTrainingSummary(Long userId);
```

- [ ] **Step 4: 修改实现**

在 `TrainingServiceImpl.java` 添加 import 和方法实现：

```java
import com.itgeo.fitmate.api.fitness.training.dto.TrainingSummaryDTO;
import java.time.LocalDate;
```

方法实现：
```java
    @Override
    public TrainingSummaryDTO getTrainingSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        LocalDate now = LocalDate.now();
        LocalDate weekAgo = now.minusDays(7);
        LocalDate monthAgo = now.minusDays(30);

        List<TrainingLog> logs = trainingLogMapper.selectList(
                new LambdaQueryWrapper<TrainingLog>()
                        .eq(TrainingLog::getUserId, userId)
                        .ge(TrainingLog::getTrainingDate, monthAgo)
        );

        BigDecimal weekVolume = BigDecimal.ZERO;
        BigDecimal monthVolume = BigDecimal.ZERO;
        int weekDays = 0;
        int monthDays = 0;
        for (TrainingLog log : logs) {
            monthVolume = monthVolume.add(log.getTotalVolume() == null ? BigDecimal.ZERO : log.getTotalVolume());
            monthDays++;
            if (log.getTrainingDate() != null && !log.getTrainingDate().isBefore(weekAgo)) {
                weekVolume = weekVolume.add(log.getTotalVolume() == null ? BigDecimal.ZERO : log.getTotalVolume());
                weekDays++;
            }
        }
        return new TrainingSummaryDTO(weekVolume, monthVolume, weekDays, monthDays);
    }
```

- [ ] **Step 5: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=TrainingServiceImplTest test`
Expected: PASS（5 个测试）

- [ ] **Step 6: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/
git commit -m "feat: add getTrainingSummary with week/month aggregates"
```

---

## Task 10: BodyMetricsService 加 getBodyMetricsSummary + 实现

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/application/BodyMetricsService.java`
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/application/impl/BodyMetricsServiceImpl.java`
- Modify: `FitMate-backend/FitMate-api/src/test/java/com/itgeo/fitmate/api/fitness/metrics/application/impl/BodyMetricsServiceImplTest.java`

**说明**：BodyMetricsServiceImpl 需注入 UserPreferenceService 以读取身高。需先读取现有 BodyMetricsServiceImpl 确认构造器。

- [ ] **Step 1: 读取现有 BodyMetricsServiceImpl**

Run: Read `BodyMetricsServiceImpl.java` 确认现有构造器与注入方式。

- [ ] **Step 2: 写失败测试（追加）**

```java
    @Test
    void getBodyMetricsSummary_withHeight_calculatesBmiAndChangeRate() {
        // 准备：最新体重 70.5，上一次 71.5，身高 175cm
        BodyMetrics latest = new BodyMetrics();
        latest.setWeight(new BigDecimal("70.5"));
        latest.setRecordDate(java.time.LocalDate.now());
        BodyMetrics previous = new BodyMetrics();
        previous.setWeight(new BigDecimal("71.5"));
        previous.setRecordDate(java.time.LocalDate.now().minusDays(7));
        when(bodyMetricsMapper.selectList(any())).thenReturn(java.util.Arrays.asList(latest, previous));
        when(userPreferenceService.getHeightCm(1L)).thenReturn(175);

        // 执行
        BodyMetricsSummaryDTO summary = bodyMetricsService.getBodyMetricsSummary(1L);

        // 验证：BMI = 70.5 / (1.75)^2 = 23.02
        assertEquals(new BigDecimal("23.02"), summary.getBmi());
        assertEquals(new BigDecimal("-1.40"), summary.getWeightChangeRate());
        assertEquals(new BigDecimal("70.5"), summary.getLatestWeight());
        assertEquals(new BigDecimal("71.5"), summary.getPreviousWeight());
    }

    @Test
    void getBodyMetricsSummary_noHeight_bmiNull() {
        BodyMetrics latest = new BodyMetrics();
        latest.setWeight(new BigDecimal("70.5"));
        when(bodyMetricsMapper.selectList(any())).thenReturn(java.util.Collections.singletonList(latest));
        when(userPreferenceService.getHeightCm(1L)).thenReturn(null);

        BodyMetricsSummaryDTO summary = bodyMetricsService.getBodyMetricsSummary(1L);

        assertNull(summary.getBmi());
        assertEquals(new BigDecimal("70.5"), summary.getLatestWeight());
        assertNull(summary.getPreviousWeight());
        assertNull(summary.getWeightChangeRate());
    }
```

注意：BMI 计算需用 `setScale(2, RoundingMode.HALF_UP)`，变化率用 `setScale(2, RoundingMode.HALF_UP)`。

- [ ] **Step 3: 运行测试验证失败**

Run: `mvn -pl FitMate-api -Dtest=BodyMetricsServiceImplTest test`
Expected: FAIL（方法不存在 + 构造器变更）

- [ ] **Step 4: 修改接口**

在 `BodyMetricsService.java` 追加：
```java
    com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsSummaryDTO getBodyMetricsSummary(Long userId);
```

- [ ] **Step 5: 修改实现**

1. 添加 import：
```java
import com.itgeo.fitmate.api.auth.application.UserPreferenceService;
import com.itgeo.fitmate.api.fitness.metrics.dto.BodyMetricsSummaryDTO;
import java.math.RoundingMode;
```

2. 修改构造器（追加注入 UserPreferenceService）：
```java
    private final BodyMetricsMapper bodyMetricsMapper;
    private final UserPreferenceService userPreferenceService;

    public BodyMetricsServiceImpl(BodyMetricsMapper bodyMetricsMapper, UserPreferenceService userPreferenceService) {
        this.bodyMetricsMapper = bodyMetricsMapper;
        this.userPreferenceService = userPreferenceService;
    }
```

注意：现有测试类的 `new BodyMetricsServiceImpl(bodyMetricsMapper)` 需改为 `new BodyMetricsServiceImpl(bodyMetricsMapper, userPreferenceService)`，并在 setup 中 mock userPreferenceService。

3. 添加方法实现：
```java
    @Override
    public BodyMetricsSummaryDTO getBodyMetricsSummary(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("用户未登录");
        }
        List<BodyMetrics> logs = bodyMetricsMapper.selectList(
                new LambdaQueryWrapper<BodyMetrics>()
                        .eq(BodyMetrics::getUserId, userId)
                        .orderByDesc(BodyMetrics::getRecordDate)
                        .last("limit 2")
        );
        if (logs.isEmpty()) {
            return new BodyMetricsSummaryDTO(null, null, null, null);
        }
        BodyMetrics latest = logs.get(0);
        BodyMetrics previous = logs.size() > 1 ? logs.get(1) : null;

        BigDecimal latestWeight = latest.getWeight();
        BigDecimal previousWeight = previous == null ? null : previous.getWeight();
        BigDecimal changeRate = null;
        if (latestWeight != null && previousWeight != null && previousWeight.signum() != 0) {
            changeRate = latestWeight.subtract(previousWeight)
                    .divide(previousWeight, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal bmi = null;
        Integer heightCm = userPreferenceService.getHeightCm(userId);
        if (latestWeight != null && heightCm != null && heightCm > 0) {
            double heightM = heightCm / 100.0;
            bmi = latestWeight.divide(new BigDecimal(heightM * heightM), 2, RoundingMode.HALF_UP);
        }

        return new BodyMetricsSummaryDTO(bmi, changeRate, latestWeight, previousWeight);
    }
```

- [ ] **Step 6: 运行测试验证通过**

Run: `mvn -pl FitMate-api -Dtest=BodyMetricsServiceImplTest test`
Expected: PASS（6 个测试）

- [ ] **Step 7: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/
git commit -m "feat: add getBodyMetricsSummary with BMI and weight change rate"
```

---

## Task 11: CardioController

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/controller/CardioController.java`

- [ ] **Step 1: 创建 Controller**

```java
package com.itgeo.fitmate.api.fitness.cardio.controller;

import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.fitness.cardio.application.CardioService;
import com.itgeo.fitmate.api.fitness.cardio.dto.CardioLogRequest;
import com.itgeo.fitmate.common.response.LeeResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 有氧训练相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/cardio")
public class CardioController {

    @Resource
    private CardioService cardioService;

    @PostMapping("/log")
    public LeeResult logCardio(@RequestBody CardioLogRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            cardioService.logCardio(userId, request, "manual");
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存有氧训练记录失败", e);
            return LeeResult.errorException("保存有氧训练记录失败");
        }
    }

    @GetMapping("/recent")
    public LeeResult getRecentCardio(@RequestParam(required = false) Integer limit) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(cardioService.getRecentCardio(userId, limit));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询最近有氧训练记录失败", e);
            return LeeResult.errorException("查询最近有氧训练记录失败");
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/controller/CardioController.java
git commit -m "feat: add CardioController with log and recent endpoints"
```

---

## Task 12: HeartRateController

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/controller/HeartRateController.java`

- [ ] **Step 1: 创建 Controller**

```java
package com.itgeo.fitmate.api.fitness.heartrate.controller;

import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.fitness.heartrate.application.HeartRateService;
import com.itgeo.fitmate.api.fitness.heartrate.dto.HeartRateLogRequest;
import com.itgeo.fitmate.common.response.LeeResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 心率记录相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/heart-rate")
public class HeartRateController {

    @Resource
    private HeartRateService heartRateService;

    @PostMapping("/log")
    public LeeResult logHeartRate(@RequestBody HeartRateLogRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            heartRateService.logHeartRate(userId, request, "manual");
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存心率记录失败", e);
            return LeeResult.errorException("保存心率记录失败");
        }
    }

    @GetMapping("/recent")
    public LeeResult getRecentHeartRate(@RequestParam(required = false) Integer limit) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(heartRateService.getRecentHeartRate(userId, limit));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询最近心率记录失败", e);
            return LeeResult.errorException("查询最近心率记录失败");
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/heartrate/controller/HeartRateController.java
git commit -m "feat: add HeartRateController with log and recent endpoints"
```

---

## Task 13: DietController

**Files:**
- Create: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/controller/DietController.java`

- [ ] **Step 1: 创建 Controller**

```java
package com.itgeo.fitmate.api.fitness.diet.controller;

import com.itgeo.fitmate.api.auth.application.UserContextHolder;
import com.itgeo.fitmate.api.fitness.diet.application.DietService;
import com.itgeo.fitmate.api.fitness.diet.dto.DietLogRequest;
import com.itgeo.fitmate.common.response.LeeResult;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 饮食记录相关接口。
 */
@Slf4j
@RestController
@RequestMapping("/diet")
public class DietController {

    @Resource
    private DietService dietService;

    @PostMapping("/log")
    public LeeResult logDiet(@RequestBody DietLogRequest request) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            dietService.logDiet(userId, request, "manual");
            return LeeResult.ok();
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("保存饮食记录失败", e);
            return LeeResult.errorException("保存饮食记录失败");
        }
    }

    @GetMapping("/recent")
    public LeeResult getRecentDiet(@RequestParam(required = false) Integer limit) {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(dietService.getRecentDiet(userId, limit));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询最近饮食记录失败", e);
            return LeeResult.errorException("查询最近饮食记录失败");
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/diet/controller/DietController.java
git commit -m "feat: add DietController with log and recent endpoints"
```

---

## Task 14: TrainingController 加 /summary 接口

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/controller/TrainingController.java`

- [ ] **Step 1: 追加 /summary 接口**

在 `TrainingController.java` 末尾追加：

```java
    /**
     * 查询当前登录用户的训练派生指标（周/月训练量与天数）。
     */
    @GetMapping("/summary")
    public LeeResult getTrainingSummary() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(trainingService.getTrainingSummary(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询训练汇总失败", e);
            return LeeResult.errorException("查询训练汇总失败");
        }
    }
```

- [ ] **Step 2: 编译验证**

Run: `mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/controller/TrainingController.java
git commit -m "feat: add /training/summary endpoint"
```

---

## Task 15: BodyMetricsController 加 /summary 接口

**Files:**
- Modify: `FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/controller/BodyMetricsController.java`

- [ ] **Step 1: 追加 /summary 接口**

在 `BodyMetricsController.java` 末尾追加：

```java
    /**
     * 查询当前登录用户的身体指标派生指标（BMI、体重变化率）。
     */
    @GetMapping("/summary")
    public LeeResult getBodyMetricsSummary() {
        try {
            Long userId = UserContextHolder.getRequired().getUserId();
            return LeeResult.ok(bodyMetricsService.getBodyMetricsSummary(userId));
        } catch (IllegalArgumentException e) {
            return LeeResult.errorMsg(e.getMessage());
        } catch (Exception e) {
            log.error("查询身体指标汇总失败", e);
            return LeeResult.errorException("查询身体指标汇总失败");
        }
    }
```

- [ ] **Step 2: 编译验证 + 全量测试**

Run: `mvn -pl FitMate-api -am clean compile`
Run: `mvn -pl FitMate-api test`
Expected: BUILD SUCCESS + 全部测试通过

- [ ] **Step 3: 提交**

```bash
git add FitMate-backend/FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/metrics/controller/BodyMetricsController.java
git commit -m "feat: add /body-metrics/summary endpoint"
```

---

## Task 16: 前端 doctorApi.ts 扩展

**Files:**
- Modify: `FitMate-frontend/src/services/doctorApi.ts`

- [ ] **Step 1: 在 doctorApi.ts 末尾追加 8 个函数**

```ts
// ========== 有氧训练 ==========
export function logCardio(bo) {
  return instance({ url: "/cardio/log", method: "post", data: bo });
}
export function getRecentCardio(limit) {
  return instance({ url: "/cardio/recent?limit=" + (limit || 10), method: "get" });
}

// ========== 心率 ==========
export function logHeartRate(bo) {
  return instance({ url: "/heart-rate/log", method: "post", data: bo });
}
export function getRecentHeartRate(limit) {
  return instance({ url: "/heart-rate/recent?limit=" + (limit || 10), method: "get" });
}

// ========== 饮食 ==========
export function logDiet(bo) {
  return instance({ url: "/diet/log", method: "post", data: bo });
}
export function getRecentDiet(limit) {
  return instance({ url: "/diet/recent?limit=" + (limit || 10), method: "get" });
}

// ========== 派生指标 ==========
export function getTrainingSummary() {
  return instance({ url: "/training/summary", method: "get" });
}
export function getBodyMetricsSummary() {
  return instance({ url: "/body-metrics/summary", method: "get" });
}
```

- [ ] **Step 2: 构建验证**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add FitMate-frontend/src/services/doctorApi.ts
git commit -m "feat: add 8 API functions for cardio/heart-rate/diet/summary"
```

---

## Task 17: ChatLogicBase.vue 扩展数据与 fetch 方法

**Files:**
- Modify: `FitMate-frontend/src/pages/chat/ChatLogicBase.vue`

- [ ] **Step 1: 在 data() 中追加新字段**

找到 `data()` 返回对象，在 `recentMetrics` 之后追加：

```js
      recentCardio: [],
      recentHeartRate: [],
      recentDiet: [],
      trainingSummary: null,
      bodyMetricsSummary: null,
```

- [ ] **Step 2: 追加 fetch 方法**

在 methods 中追加（与现有 fetchRecentTraining / fetchRecentMetrics 同风格）：

```js
    fetchRecentCardio: function () {
      var me = this;
      doctorApi.getRecentCardio(10).then(function (res) {
        me.recentCardio = (res && res.data) || [];
      }).catch(function () { me.recentCardio = []; });
    },
    fetchRecentHeartRate: function () {
      var me = this;
      doctorApi.getRecentHeartRate(10).then(function (res) {
        me.recentHeartRate = (res && res.data) || [];
      }).catch(function () { me.recentHeartRate = []; });
    },
    fetchRecentDiet: function () {
      var me = this;
      doctorApi.getRecentDiet(10).then(function (res) {
        me.recentDiet = (res && res.data) || [];
      }).catch(function () { me.recentDiet = []; });
    },
    fetchTrainingSummary: function () {
      var me = this;
      doctorApi.getTrainingSummary().then(function (res) {
        me.trainingSummary = (res && res.data) || null;
      }).catch(function () { me.trainingSummary = null; });
    },
    fetchBodyMetricsSummary: function () {
      var me = this;
      doctorApi.getBodyMetricsSummary().then(function (res) {
        me.bodyMetricsSummary = (res && res.data) || null;
      }).catch(function () { me.bodyMetricsSummary = null; });
    },
```

- [ ] **Step 3: 构建验证**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-frontend/src/pages/chat/ChatLogicBase.vue
git commit -m "feat: add data fields and fetch methods for cardio/heart-rate/diet/summary"
```

---

## Task 18: TrainingPage.vue 扩展 3 Tab

**Files:**
- Modify: `FitMate-frontend/src/pages/training/TrainingPage.vue`

**说明**：此任务较复杂，先 Read 现有 TrainingPage.vue 确认结构，再扩展。需保留现有力量训练表单不变，外层加 Tab 切换。

- [ ] **Step 1: 读取现有 TrainingPage.vue**

Run: Read 文件确认现有 template / data / methods 结构。

- [ ] **Step 2: 修改 data() 追加 Tab 状态与表单数据**

在 data() 追加：

```js
      activeTrainingTab: 'strength',
      cardioForm: {
        date: new Date().toISOString().slice(0, 10),
        cardioType: 'running',
        distanceKm: null,
        durationMinutes: null,
        avgHeartRate: null,
        note: ''
      },
      cardioTypeOptions: [
        { value: 'running', label: '跑步' },
        { value: 'cycling', label: '骑行' },
        { value: 'swimming', label: '游泳' },
        { value: 'rowing', label: '划船' },
        { value: 'jump_rope', label: '跳绳' },
        { value: 'other', label: '其他' }
      ],
      dietForm: {
        date: new Date().toISOString().slice(0, 10),
        mealType: 'breakfast',
        items: [{ foodName: '', portion: '', calories: null, protein: null, carbs: null, fat: null }],
        note: ''
      },
      mealTypeOptions: [
        { value: 'breakfast', label: '早餐' },
        { value: 'lunch', label: '午餐' },
        { value: 'dinner', label: '晚餐' },
        { value: 'snack', label: '加餐' }
      ],
```

- [ ] **Step 3: 在 template 顶部加 Tab 切换 UI**

在现有 training-page 容器内最顶部加：

```vue
      <div class="tab-bar">
        <button
          v-for="tab in [
            { key: 'strength', label: '力量' },
            { key: 'cardio', label: '有氧' },
            { key: 'diet', label: '训练营养' }
          ]"
          :key="tab.key"
          class="tab-btn"
          :class="{ 'tab-btn-active': activeTrainingTab === tab.key }"
          @click="switchTrainingTab(tab.key)"
        >{{ tab.label }}</button>
      </div>
```

- [ ] **Step 4: 用 v-if 包裹现有力量训练表单与历史**

现有力量训练区块用 `<div v-if="activeTrainingTab === 'strength'">...</div>` 包裹。

- [ ] **Step 5: 追加有氧 Tab 与饮食 Tab 模板**

```vue
      <div v-if="activeTrainingTab === 'cardio'" class="cardio-section">
        <div class="form-block">
          <div class="form-label">DATE</div>
          <input v-model="cardioForm.date" class="metric-input" type="date" />
        </div>
        <div class="form-block">
          <div class="form-label">TYPE</div>
          <select v-model="cardioForm.cardioType" class="metric-input">
            <option v-for="opt in cardioTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div class="form-row">
          <div class="form-block">
            <div class="form-label">DISTANCE (KM)</div>
            <input v-model.number="cardioForm.distanceKm" class="metric-input" type="number" step="0.1" placeholder="0.0" />
          </div>
          <div class="form-block">
            <div class="form-label">DURATION (MIN)</div>
            <input v-model.number="cardioForm.durationMinutes" class="metric-input" type="number" placeholder="0" />
          </div>
        </div>
        <div class="form-block">
          <div class="form-label">AVG HEART RATE</div>
          <input v-model.number="cardioForm.avgHeartRate" class="metric-input" type="number" placeholder="optional" />
        </div>
        <div class="form-block">
          <div class="form-label">NOTE</div>
          <textarea v-model="cardioForm.note" class="metric-input" rows="2"></textarea>
        </div>
        <button class="form-submit-btn" @click="submitCardio">COMMIT LOG</button>

        <div class="history-section">
          <div class="section-title">RECENT · 有氧</div>
          <div v-for="(record, idx) in recentCardio" :key="idx" class="history-item">
            <span class="history-date">{{ record.date }}</span>
            <span class="history-detail">{{ record.summary }}</span>
          </div>
        </div>
      </div>

      <div v-if="activeTrainingTab === 'diet'" class="diet-section">
        <div class="form-block">
          <div class="form-label">DATE</div>
          <input v-model="dietForm.date" class="metric-input" type="date" />
        </div>
        <div class="form-block">
          <div class="form-label">MEAL</div>
          <select v-model="dietForm.mealType" class="metric-input">
            <option v-for="opt in mealTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
          </select>
        </div>
        <div class="form-block">
          <div class="form-label">FOOD ITEMS</div>
          <div v-for="(item, idx) in dietForm.items" :key="idx" class="diet-item-row">
            <input v-model="item.foodName" class="metric-input" placeholder="食物名" />
            <input v-model="item.portion" class="metric-input" placeholder="份量" />
            <input v-model.number="item.calories" class="metric-input" type="number" placeholder="kcal" />
            <input v-model.number="item.protein" class="metric-input" type="number" placeholder="蛋白g" />
            <input v-model.number="item.carbs" class="metric-input" type="number" placeholder="碳水g" />
            <input v-model.number="item.fat" class="metric-input" type="number" placeholder="脂肪g" />
            <button class="remove-item-btn" @click="dietForm.items.splice(idx, 1)">×</button>
          </div>
          <button class="add-item-btn" @click="dietForm.items.push({ foodName: '', portion: '', calories: null, protein: null, carbs: null, fat: null })">ADD ITEM</button>
        </div>
        <div class="form-block">
          <div class="form-label">NOTE</div>
          <textarea v-model="dietForm.note" class="metric-input" rows="2"></textarea>
        </div>
        <button class="form-submit-btn" @click="submitDiet">COMMIT LOG</button>

        <div class="history-section">
          <div class="section-title">RECENT · 训练营养</div>
          <div v-for="(record, idx) in recentDiet" :key="idx" class="history-item">
            <span class="history-date">{{ record.date }}</span>
            <span class="history-detail">{{ record.summary }}</span>
          </div>
        </div>
      </div>
```

- [ ] **Step 6: 追加 methods（switchTrainingTab / submitCardio / submitDiet / buildCardioPrompt / buildDietPrompt）**

```js
    switchTrainingTab: function (tab) {
      this.activeTrainingTab = tab;
      if (tab === 'cardio' && this.recentCardio.length === 0) this.fetchRecentCardio();
      if (tab === 'diet' && this.recentDiet.length === 0) this.fetchRecentDiet();
    },
    submitCardio: function () {
      var me = this;
      var formData = {
        date: this.cardioForm.date,
        cardioType: this.cardioForm.cardioType,
        distanceKm: this.cardioForm.distanceKm,
        durationMinutes: this.cardioForm.durationMinutes,
        avgHeartRate: this.cardioForm.avgHeartRate,
        note: this.cardioForm.note
      };
      doctorApi.logCardio(formData).then(function () {
        me.fetchRecentCardio();
        me.fetchTrainingSummary();
      }).catch(function () {
        window.sessionStorage.setItem("fitmate:pending-draft", me.buildCardioPrompt(formData));
        me.$router.push("/chat");
      });
    },
    submitDiet: function () {
      var me = this;
      var validItems = this.dietForm.items.filter(function (it) { return (it.foodName || '').trim(); });
      var formData = {
        date: this.dietForm.date,
        mealType: this.dietForm.mealType,
        items: validItems,
        note: this.dietForm.note
      };
      doctorApi.logDiet(formData).then(function () {
        me.fetchRecentDiet();
      }).catch(function () {
        window.sessionStorage.setItem("fitmate:pending-draft", me.buildDietPrompt(formData));
        me.$router.push("/chat");
      });
    },
    buildCardioPrompt: function (data) {
      var typeMap = { running: '跑步', cycling: '骑行', swimming: '游泳', rowing: '划船', jump_rope: '跳绳', other: '其他' };
      var parts = [data.date, typeMap[data.cardioType] || data.cardioType];
      if (data.distanceKm) parts.push(data.distanceKm + 'km');
      if (data.durationMinutes) parts.push(data.durationMinutes + 'min');
      return '我今天做了' + parts.join(' ') + '，请帮我记录';
    },
    buildDietPrompt: function (data) {
      var mealMap = { breakfast: '早餐', lunch: '午餐', dinner: '晚餐', snack: '加餐' };
      var items = data.items.map(function (it) {
        return it.foodName + (it.portion ? '(' + it.portion + ')' : '') + (it.calories ? ' ' + it.calories + 'kcal' : '');
      }).join('、');
      return '我今天' + (mealMap[data.mealType] || data.mealType) + '吃了：' + items + '，请帮我记录';
    },
```

- [ ] **Step 7: 追加 Tab 与 aside 样式**

在 `<style>` 中追加（沿用现有 M3 tokens 风格）：

```css
.tab-bar { display: flex; gap: 4px; margin-bottom: 16px; }
.tab-btn {
  padding: 6px 16px; border: none; background: transparent;
  color: var(--color-on-surface-variant); cursor: pointer;
  border-radius: 999px; font-size: 13px; letter-spacing: 0.04em;
}
.tab-btn-active {
  background: var(--color-primary); color: var(--color-on-primary);
}
.diet-item-row { display: flex; gap: 6px; margin-bottom: 6px; flex-wrap: wrap; }
.diet-item-row .metric-input { flex: 1; min-width: 80px; }
.remove-item-btn {
  background: transparent; border: none; color: var(--color-error);
  cursor: pointer; font-size: 18px; padding: 0 4px;
}
.add-item-btn {
  background: transparent; border: 1px dashed var(--color-outline);
  color: var(--color-on-surface-variant); padding: 6px 12px;
  border-radius: 999px; cursor: pointer; font-size: 11px;
  letter-spacing: 0.08em; text-transform: uppercase;
}
.form-row { display: flex; gap: 12px; }
.form-row .form-block { flex: 1; }
.history-section { margin-top: 24px; }
.section-title {
  font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase;
  color: var(--color-on-surface-variant); margin-bottom: 8px;
}
```

- [ ] **Step 8: 在 mounted 中追加 fetchTrainingSummary 调用**

在 mounted() 末尾追加 `this.fetchTrainingSummary();`

- [ ] **Step 9: 构建验证**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 10: 提交**

```bash
git add FitMate-frontend/src/pages/training/TrainingPage.vue
git commit -m "feat: extend TrainingPage with 3 tabs (strength/cardio/diet)"
```

---

## Task 19: MetricsPage.vue 扩展 2 Tab + 围度录入

**Files:**
- Modify: `FitMate-frontend/src/pages/metrics/MetricsPage.vue`

- [ ] **Step 1: 读取现有 MetricsPage.vue**

Run: Read 文件确认现有 template / data / methods 结构。

- [ ] **Step 2: 修改 data() 追加 Tab 状态、围度字段、心率表单**

```js
      activeMetricsTab: 'body',
      heartRateForm: {
        date: new Date().toISOString().slice(0, 10),
        restingHr: null,
        maxHr: null,
        hrv: null,
        note: ''
      },
```

在现有 form 对象中追加围度字段：
```js
        chestGirth: null,
        waistGirth: null,
        hipGirth: null,
        armGirth: null,
        thighGirth: null,
```

- [ ] **Step 3: 在 template 顶部加 Tab 切换**

```vue
      <div class="tab-bar">
        <button
          v-for="tab in [
            { key: 'body', label: '身体指标' },
            { key: 'heart-rate', label: '心率' }
          ]"
          :key="tab.key"
          class="tab-btn"
          :class="{ 'tab-btn-active': activeMetricsTab === tab.key }"
          @click="switchMetricsTab(tab.key)"
        >{{ tab.label }}</button>
      </div>
```

- [ ] **Step 4: 用 v-if 包裹现有身体指标表单，追加围度录入**

现有身体指标区块用 `<div v-if="activeMetricsTab === 'body'">...</div>` 包裹，并在 bodyFat 输入框之后追加：

```vue
        <div class="form-block">
          <div class="form-label">GIRTH (CM)</div>
          <div class="form-row">
            <div class="girth-item">
              <input v-model.number="form.chestGirth" class="metric-input" type="number" step="0.1" placeholder="胸围" />
            </div>
            <div class="girth-item">
              <input v-model.number="form.waistGirth" class="metric-input" type="number" step="0.1" placeholder="腰围" />
            </div>
            <div class="girth-item">
              <input v-model.number="form.hipGirth" class="metric-input" type="number" step="0.1" placeholder="臀围" />
            </div>
            <div class="girth-item">
              <input v-model.number="form.armGirth" class="metric-input" type="number" step="0.1" placeholder="臂围" />
            </div>
            <div class="girth-item">
              <input v-model.number="form.thighGirth" class="metric-input" type="number" step="0.1" placeholder="大腿围" />
            </div>
          </div>
        </div>
```

- [ ] **Step 5: 修改 submitMetrics 提交时带围度字段**

在 formData 构造中追加：
```js
        chestGirth: this.form.chestGirth,
        waistGirth: this.form.waistGirth,
        hipGirth: this.form.hipGirth,
        armGirth: this.form.armGirth,
        thighGirth: this.form.thighGirth,
```

- [ ] **Step 6: 追加心率 Tab 模板**

```vue
      <div v-if="activeMetricsTab === 'heart-rate'" class="heart-rate-section">
        <div class="form-block">
          <div class="form-label">DATE</div>
          <input v-model="heartRateForm.date" class="metric-input" type="date" />
        </div>
        <div class="form-row">
          <div class="form-block">
            <div class="form-label">RESTING HR</div>
            <input v-model.number="heartRateForm.restingHr" class="metric-input" type="number" placeholder="bpm" />
          </div>
          <div class="form-block">
            <div class="form-label">MAX HR</div>
            <input v-model.number="heartRateForm.maxHr" class="metric-input" type="number" placeholder="bpm" />
          </div>
        </div>
        <div class="form-block">
          <div class="form-label">HRV (MS)</div>
          <input v-model.number="heartRateForm.hrv" class="metric-input" type="number" placeholder="ms" />
        </div>
        <div class="form-block">
          <div class="form-label">NOTE</div>
          <textarea v-model="heartRateForm.note" class="metric-input" rows="2"></textarea>
        </div>
        <button class="form-submit-btn" @click="submitHeartRate">COMMIT LOG</button>

        <div class="history-section">
          <div class="section-title">RECENT · 心率</div>
          <div v-for="(record, idx) in recentHeartRate" :key="idx" class="history-item">
            <span class="history-date">{{ record.date }}</span>
            <span class="history-detail">{{ record.summary }}</span>
          </div>
        </div>
      </div>
```

- [ ] **Step 7: 追加 methods（switchMetricsTab / submitHeartRate / buildHeartRatePrompt）**

```js
    switchMetricsTab: function (tab) {
      this.activeMetricsTab = tab;
      if (tab === 'heart-rate' && this.recentHeartRate.length === 0) this.fetchRecentHeartRate();
    },
    submitHeartRate: function () {
      var me = this;
      var formData = {
        date: this.heartRateForm.date,
        restingHr: this.heartRateForm.restingHr,
        maxHr: this.heartRateForm.maxHr,
        hrv: this.heartRateForm.hrv,
        note: this.heartRateForm.note
      };
      doctorApi.logHeartRate(formData).then(function () {
        me.fetchRecentHeartRate();
      }).catch(function () {
        window.sessionStorage.setItem("fitmate:pending-draft", me.buildHeartRatePrompt(formData));
        me.$router.push("/chat");
      });
    },
    buildHeartRatePrompt: function (data) {
      var parts = [];
      if (data.restingHr) parts.push('静息心率' + data.restingHr);
      if (data.maxHr) parts.push('最大心率' + data.maxHr);
      if (data.hrv) parts.push('HRV ' + data.hrv + 'ms');
      return '我今天测了' + parts.join('，') + '，请帮我记录';
    },
```

- [ ] **Step 8: 追加 aside 派生指标展示**

在现有 aside 区域（若有）追加 BMI 与体重变化率展示：

```vue
        <div v-if="bodyMetricsSummary" class="summary-block">
          <div class="summary-item">
            <div class="summary-label">BMI</div>
            <div class="summary-value">{{ bodyMetricsSummary.bmi || '未设置身高' }}</div>
          </div>
          <div class="summary-item">
            <div class="summary-label">WEIGHT CHANGE</div>
            <div class="summary-value">{{ bodyMetricsSummary.weightChangeRate ? bodyMetricsSummary.weightChangeRate + '%' : '-' }}</div>
          </div>
        </div>
```

- [ ] **Step 9: 追加样式（与 TrainingPage 一致的 tab-bar 等）**

复用 TrainingPage 中的 `.tab-bar / .tab-btn / .tab-btn-active / .form-row / .history-section / .section-title` 样式。`.girth-item { flex: 1; min-width: 60px; }`。

- [ ] **Step 10: 在 mounted 中追加 fetchBodyMetricsSummary**

在 mounted() 末尾追加 `this.fetchBodyMetricsSummary();`

- [ ] **Step 11: 构建验证**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 12: 提交**

```bash
git add FitMate-frontend/src/pages/metrics/MetricsPage.vue
git commit -m "feat: extend MetricsPage with 2 tabs (body/heart-rate) and girth inputs"
```

---

## Task 20: TrainingPage aside 派生指标展示

**Files:**
- Modify: `FitMate-frontend/src/pages/training/TrainingPage.vue`

- [ ] **Step 1: 在 TrainingPage 现有 aside 区域追加派生指标展示**

找到现有 aside（显示 Sessions/Total Volume/Trend 的区域），追加 4 个数字方块：

```vue
        <div v-if="trainingSummary" class="summary-grid">
          <div class="summary-card">
            <div class="summary-label">WEEK VOLUME</div>
            <div class="summary-value tabular-nums">{{ trainingSummary.weekVolume || 0 }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">MONTH VOLUME</div>
            <div class="summary-value tabular-nums">{{ trainingSummary.monthVolume || 0 }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">WEEK DAYS</div>
            <div class="summary-value tabular-nums">{{ trainingSummary.weekTrainingDays || 0 }}</div>
          </div>
          <div class="summary-card">
            <div class="summary-label">MONTH DAYS</div>
            <div class="summary-value tabular-nums">{{ trainingSummary.monthTrainingDays || 0 }}</div>
          </div>
        </div>
```

- [ ] **Step 2: 追加样式**

```css
.summary-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 12px;
}
.summary-card {
  background: var(--color-surface-container);
  padding: 10px 12px; border-radius: 8px;
}
.summary-label {
  font-size: 10px; letter-spacing: 0.08em; text-transform: uppercase;
  color: var(--color-on-surface-variant); margin-bottom: 4px;
}
.summary-value {
  font-size: 18px; font-weight: 600; color: var(--color-on-surface);
}
```

- [ ] **Step 3: 构建验证**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add FitMate-frontend/src/pages/training/TrainingPage.vue
git commit -m "feat: add training summary aside with week/month volume and days"
```

---

## Task 21: 全量编译 + 测试 + 手动验证清单

- [ ] **Step 1: 后端全量编译**

Run: `cd FitMate-backend && mvn -pl FitMate-api -am clean compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: 后端全量测试**

Run: `cd FitMate-backend && mvn -pl FitMate-api test`
Expected: BUILD SUCCESS，所有测试通过

- [ ] **Step 3: 前端构建**

Run: `cd FitMate-frontend && npm run build`
Expected: BUILD SUCCESS

- [ ] **Step 4: 手动验证清单（不提交，仅记录）**

1. 启动后端服务
2. 启动前端 dev server
3. Training 页 3 Tab 切换正常
4. 力量 Tab：提交训练记录，aside 显示周/月训练量
5. 有氧 Tab：提交有氧记录（需有最近体重），历史列表显示
6. 训练营养 Tab：提交饮食记录，历史列表显示
7. Metrics 页 2 Tab 切换正常
8. 身体指标 Tab：录入围度字段，aside 显示 BMI（需配身高）与体重变化率
9. 心率 Tab：提交心率记录，历史列表显示
10. 失败回退：断开后端，提交表单，应跳转 /chat 并填充 prompt

- [ ] **Step 5: 最终提交（若有未提交的修复）**

```bash
git status
# 若有未提交改动
git add -A
git commit -m "chore: final adjustments after manual verification"
```

---

## 自审清单

- [x] Spec §1 背景目标 → Task 1-21 全覆盖
- [x] Spec §2 范围 → 不纳入项（图表/导出/删除/分页）均未在 Task 中
- [x] Spec §3.1 新增 3 Controller → Task 11/12/13
- [x] Spec §3.2 派生指标 summary 接口 → Task 14/15
- [x] Spec §3.3 Service 自动计算扩展 → Task 3/4
- [x] Spec §3.4 硬编码字典文件 → Task 1/2
- [x] Spec §3.5 身高字段读写 → Task 7
- [x] Spec §4.1 Training 页 3 Tab → Task 18/20
- [x] Spec §4.2 Metrics 页 2 Tab → Task 19
- [x] Spec §4.3 API 层 8 函数 → Task 16
- [x] Spec §4.4 Tab 状态管理 → Task 18/19
- [x] Spec §5 自动计算项落点 → 8 项全部覆盖
- [x] Spec §6 Schema 零改动 → 无 Task 涉及 DDL
- [x] Spec §7 测试策略 → Task 1/2/3/4/5/6/7/9/10 均含 TDD
- [x] 无 TBD/TODO 占位符
- [x] 类型一致性：TrainingSummaryDTO / BodyMetricsSummaryDTO 字段名前后端一致
