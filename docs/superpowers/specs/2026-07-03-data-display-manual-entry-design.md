# 数据展示与手动添加设计

> 日期：2026-07-03
> 状态：待审查
> 关联：在 `2026-07-03-data-record-tools-design.md`（数据记录工具）已完成实现基础上，补齐前端展示与手动添加入口，并扩展自动计算项。

## 1. 背景与目标

数据记录工具（record/query ToolExecutor）已实现，模型可通过对话记录训练/身体/心率/饮食数据。但前端存在三处缺口：

1. **展示缺口**：cardio / heart_rate / diet 三类新数据在前端无展示页面，用户无法查看历史。
2. **手动添加缺口**：三类新数据只能通过对话记录，无手动添加入口；BodyMetrics 已有围度字段但前端表单未实现录入。
3. **自动计算不完整**：已实现配速与饮食总热量，但训练日志的总训练量/主肌群、有氧卡路里消耗、BMI/体重变化率/周月汇总等派生指标未实现。

**目标**：
- 扩展现有 Training 页（力量 + 有氧 + 训练营养）与 Metrics 页（身体指标 + 心率），用 Tab 切换。
- 新增 3 个后端 Controller 暴露 REST 接口供前端直接调用。
- 扩展自动计算：训练日志 total_volume + primary_muscle_group、有氧 calories_burned、派生指标（BMI/变化率/汇总）实时计算。

## 2. 范围

**纳入范围**：
- 后端：3 个新 Controller、3 处 Service 自动计算扩展、2 个 summary 接口（派生指标）、2 个硬编码字典文件、身高字段读写
- 前端：Training 页 3-Tab 扩展、Metrics 页 2-Tab 扩展、围度录入、API 层 8 个新函数、失败回退 prompt

**不纳入范围**：
- 图表库引入（暂保持纯文本列表 + aside 数字方块，与现有 DashboardPage 风格一致）
- 数据导出
- 数据编辑/删除（仅 upsert 新增/更新，不提供前端删除入口）
- 历史记录分页（沿用现有 limit 模式）

## 3. 后端架构

### 3.1 新增 Controller（3 个）

每个 Controller 提供 `/log`（POST，手动添加，source="manual"）+ `/recent`（GET，返回 `List<DateSummaryItem>`）两个接口，复用现有 DTO 与 Service。

| Controller | 路径 | 入参 DTO | Service 调用 |
|---|---|---|---|
| CardioController | POST `/cardio/log` | CardioLogRequest | cardioService.logCardio(userId, req, "manual") |
|  | GET `/cardio/recent?limit=N` | - | cardioLogMapper 查询 |
| HeartRateController | POST `/heart-rate/log` | HeartRateLogRequest | heartRateService.logHeartRate(userId, req, "manual") |
|  | GET `/heart-rate/recent?limit=N` | - | heartRateMapper 查询 |
| DietController | POST `/diet/log` | DietLogRequest | dietService.logDiet(userId, req, "manual") |
|  | GET `/diet/recent?limit=N` | - | dietLogMapper + dietItemMapper 查询 |

**recent 接口返回**：复用 `DateSummaryItem`（date + summary），与现有 Training/BodyMetrics recent 接口风格一致。Diet 的 recent 需聚合 items 后生成 summary。

### 3.2 派生指标 summary 接口（2 个）

新增 2 个实时计算接口，返回派生指标，供前端 aside 展示：

**GET `/training/summary`** → `TrainingSummaryDTO`
```json
{
  "weekVolume": 12500.00,
  "monthVolume": 48000.00,
  "weekTrainingDays": 4,
  "monthTrainingDays": 16
}
```
计算方式：查最近 7 天 / 30 天的 t_training_log，Σ total_volume + 计数。

**GET `/body-metrics/summary`** → `BodyMetricsSummaryDTO`
```json
{
  "bmi": 22.9,
  "weightChangeRate": -1.4,
  "latestWeight": 70.5,
  "previousWeight": 71.5
}
```
计算方式：
- 取最近 2 条 body_metrics，latestWeight / previousWeight
- weightChangeRate = (latest - previous) / previous × 100
- bmi = latestWeight / (heightM × heightM)，heightM 从 t_user_preference.preferences_json 的 heightCm 字段读取（/100）
- 若身高未配置，bmi 返回 null（前端显示"未设置身高"）

### 3.3 Service 自动计算扩展

#### TrainingServiceImpl.logTraining 扩展

在现有实现中补充：
1. **total_volume 计算**：遍历 exercises，`Σ (sets × reps × weight)`，写入 `TrainingLog.totalVolume`
2. **primaryMuscleGroup 推断**：遍历 exercises 的 name，匹配肌群字典，取出现频次最高的肌群写入 `TrainingLog.primaryMuscleGroup`

#### CardioServiceImpl.logCardio 扩展

**构造器变更**：现 CardioServiceImpl 仅注入 CardioLogMapper，需追加注入 BodyMetricsMapper（构造器参数从 1 个变 2 个）。对应测试类的 `new CardioServiceImpl(...)` 构造也需同步调整。

在现有实现中补充：
1. **caloriesBurned 计算**：`MET × weightKg × durationH`
   - MET 从 CardioMetTable 取（按 cardioType）
   - weightKg 从最近一条 body_metrics.weight 取（用 bodyMetricsMapper 查询，按 record_date 倒序取 1 条）
   - durationH = durationMinutes / 60
   - 若 distanceKm 为 null（如跳绳），仍按公式计算（caloriesBurned 不依赖距离）
   - 若体重未记录，caloriesBurned 为 null（不报错，summary 中不显示卡路里）

### 3.4 硬编码字典文件（2 个）

独立文件，其他地方只导入，不内联硬编码。

**文件 1**：`FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/training/domain/MuscleGroupDictionary.java`
```java
public final class MuscleGroupDictionary {
    private MuscleGroupDictionary() {}
    
    /** 动作名关键词 → 肌群 映射（按匹配优先级排列）。 */
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
    
    /** 根据动作名推断主肌群，返回 null 表示未匹配。 */
    public static String inferMuscleGroup(String exerciseName) { ... }
}
```

**文件 2**：`FitMate-api/src/main/java/com/itgeo/fitmate/api/fitness/cardio/domain/CardioMetTable.java`
```java
public final class CardioMetTable {
    private CardioMetTable() {}
    
    private static final Map<String, Double> MET_BY_TYPE = Map.of(
        "running", 9.8,
        "cycling", 7.5,
        "swimming", 8.0,
        "rowing", 7.0,
        "jump_rope", 12.0,
        "other", 6.0
    );
    
    /** 获取指定有氧类型的 MET 值，未知类型返回 6.0。 */
    public static double getMet(String cardioType) { ... }
}
```

### 3.5 身高字段读写

**存储位置**：`t_user_preference.preferences_json` 中增加 `heightCm` 字段（Number 类型）。

**读取方式**：新增 `UserPreferenceService.getHeightCm(Long userId)` 方法，从 preferences_json 解析。BMI 计算时调用。

**写入方式**：复用现有偏好设置接口（前端 Settings 页已有偏好编辑入口，扩展时加身高字段）。本设计不强制实现身高录入 UI，可由用户在偏好 JSON 中手动配置；若前端时间允许，在 Settings 页加一个身高输入框。

## 4. 前端架构

### 4.1 Training 页扩展（3 Tab）

**Tab 结构**：力量（现有）/ 有氧（新增）/ 训练营养（新增）

**力量 Tab**：现有表单与历史不变。

**有氧 Tab**：
- 表单：日期（默认今天）+ 类型下拉（running/cycling/swimming/rowing/jump_rope/other，中文标签）+ 距离 km + 时长 min + 平均心率（可选）+ 备注
- 历史列表：调用 `getRecentCardio(10)`，展示 date + summary
- 失败回退：`buildCardioPrompt(formData)` → 跳转 /chat

**训练营养 Tab**：
- 表单：日期 + 餐次下拉（breakfast/lunch/dinner/snack，中文标签）+ 食物明细行（动态添加：食物名 + 份量 + 热量 + 蛋白 + 碳水 + 脂肪）+ 备注
- 历史列表：调用 `getRecentDiet(10)`，展示 date + summary
- 失败回退：`buildDietPrompt(formData)` → 跳转 /chat

**aside 区域**：调用 `getTrainingSummary()`，展示 4 个数字方块（周训练量 / 月训练量 / 周训练天数 / 月训练天数）。

### 4.2 Metrics 页扩展（2 Tab）

**Tab 结构**：身体指标（扩展）/ 心率（新增）

**身体指标 Tab**：
- 现有表单（weight/bodyFat/sleep/fatigue/note）+ 新增 5 个围度输入框（chestGirth/waistGirth/hipGirth/armGirth/thighGirth）
- 历史列表不变
- aside 区域：调用 `getBodyMetricsSummary()`，展示 BMI + 体重变化率（若身高未设置，BMI 显示"未设置身高"）

**心率 Tab**：
- 表单：日期 + 静息心率 + 最大心率 + HRV + 备注
- 历史列表：调用 `getRecentHeartRate(10)`，展示 date + summary
- 失败回退：`buildHeartRatePrompt(formData)` → 跳转 /chat

### 4.3 API 层扩展

`doctorApi.ts` 新增 8 个函数：
```ts
export function logCardio(bo) { return instance({ url: "/cardio/log", method: "post", data: bo }); }
export function getRecentCardio(limit) { return instance({ url: "/cardio/recent?limit=" + (limit||10), method: "get" }); }
export function logHeartRate(bo) { return instance({ url: "/heart-rate/log", method: "post", data: bo }); }
export function getRecentHeartRate(limit) { return instance({ url: "/heart-rate/recent?limit=" + (limit||10), method: "get" }); }
export function logDiet(bo) { return instance({ url: "/diet/log", method: "post", data: bo }); }
export function getRecentDiet(limit) { return instance({ url: "/diet/recent?limit=" + (limit||10), method: "get" }); }
export function getTrainingSummary() { return instance({ url: "/training/summary", method: "get" }); }
export function getBodyMetricsSummary() { return instance({ url: "/body-metrics/summary", method: "get" }); }
```

### 4.4 Tab 状态管理

- Tab 切换状态存组件 data（`activeTrainingTab` / `activeMetricsTab`），默认第一个 Tab。
- 每个 Tab 的历史数据在 Tab 首次激活时懒加载（mounted + tab 切换时 fetch）。
- 不引入 Pinia，沿用现有 ChatLogicBase 继承模式。

## 5. 自动计算项落点汇总

| # | 计算项 | 落点 | 时机 | 依赖 | 状态 |
|---|---|---|---|---|---|
| 1 | 有氧配速 | t_cardio_log.avg_pace | record 时 | distance + duration | 已实现 |
| 2 | 饮食总热量/宏量 | t_diet_log.total_* | record 时 | items 汇总 | 已实现 |
| 3 | 单日总训练量 | t_training_log.total_volume | record 时 | exercises | 需实现 |
| 4 | 主肌群推断 | t_training_log.primary_muscle_group | record 时 | exercises + MuscleGroupDictionary | 需实现 |
| 5 | 有氧卡路里消耗 | t_cardio_log.calories_burned | record 时 | duration + CardioMetTable + 最近 weight | 需实现 |
| 6 | BMI | 展示层（summary 接口） | query 时 | latestWeight + heightCm | 需实现 |
| 7 | 体重变化率 | 展示层（summary 接口） | query 时 | 最近 2 条 weight | 需实现 |
| 8 | 周/月训练量汇总 | 展示层（summary 接口） | query 时 | 7d/30d total_volume | 需实现 |

## 6. Schema 改动

**零 DDL 改动**。所有需要的字段已存在：
- t_training_log.primary_muscle_group / total_volume（原有）
- t_cardio_log.calories_burned（Task 1 已加）
- t_body_metrics 5 个围度字段 + source（Task 1 + Task 10 已加）
- t_user_preference.preferences_json（原有 JSON 字段，存 heightCm）

## 7. 测试策略

### 后端 TDD

**新增测试类**：
- `MuscleGroupDictionaryTest`：关键词匹配、未匹配、多关键词优先级
- `CardioMetTableTest`：已知类型 MET、未知类型默认值
- `TrainingServiceImplTest` 扩展：total_volume 计算、primaryMuscleGroup 推断（在现有 2 个测试基础上加 2-3 个）
- `CardioServiceImplTest` 扩展：caloriesBurned 计算（有体重/无体重两种场景，在现有 5 个测试基础上加 2 个）
- `TrainingSummaryControllerTest` / `BodyMetricsSummaryControllerTest`：summary 接口计算逻辑

**测试约定**：沿用项目现有约定（JUnit 5 + Mockito.mock() 静态方法 + 构造器注入）。

### 前端验证

- `npm run build` 通过
- 手动验证：Tab 切换、表单提交、历史加载、aside 派生指标展示、失败回退到 Chat

## 8. 验收标准

1. **后端**：`mvn -pl FitMate-api -am clean compile` + `mvn -pl FitMate-api test` 全绿
2. **前端**：`npm run build` 通过
3. **手动验证**：
   - Training 页 3 Tab 切换正常，各有氧/饮食表单可提交，历史列表显示
   - Metrics 页 2 Tab 切换正常，围度字段可录入，心率表单可提交
   - aside 派生指标正确展示（周/月训练量、BMI、体重变化率）
   - 提交力量训练后，t_training_log.total_volume 与 primary_muscle_group 自动填充
   - 提交有氧训练后，t_cardio_log.calories_burned 自动填充（需有最近体重记录）
   - 失败回退到 Chat 正常工作

## 9. 文件清单

### 后端新增
- `fitness/training/domain/MuscleGroupDictionary.java`
- `fitness/cardio/domain/CardioMetTable.java`
- `fitness/cardio/controller/CardioController.java`
- `fitness/heartrate/controller/HeartRateController.java`
- `fitness/diet/controller/DietController.java`
- `fitness/training/dto/TrainingSummaryDTO.java`
- `fitness/metrics/dto/BodyMetricsSummaryDTO.java`
- 对应测试类

### 后端修改
- `fitness/training/application/impl/TrainingServiceImpl.java`（加 total_volume + primaryMuscleGroup 计算）
- `fitness/cardio/application/impl/CardioServiceImpl.java`（加 caloriesBurned 计算，注入 BodyMetricsMapper）
- `fitness/training/controller/TrainingController.java`（加 /summary 接口）
- `fitness/metrics/controller/BodyMetricsController.java`（加 /summary 接口）
- `auth/.../UserPreferenceService.java` 或等价服务（加 getHeightCm 方法）

### 前端修改
- `pages/training/TrainingPage.vue`（3 Tab + 有氧表单 + 饮食表单 + aside summary）
- `pages/metrics/MetricsPage.vue`（2 Tab + 围度录入 + 心率表单 + aside summary）
- `services/doctorApi.ts`（8 个新函数）
- `pages/chat/ChatLogicBase.vue`（新增 recentCardio/recentHeartRate/recentDiet/trainingSummary/bodyMetricsSummary 数据与 fetch 方法）
