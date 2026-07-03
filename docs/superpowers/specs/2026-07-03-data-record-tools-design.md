# 数据记录工具（record tools）设计文档

- 日期：2026-07-03
- 范围：FitMate-backend / FitMate-api
- 目标：为训练日志、有氧训练、身体指标、心率、饮食五类健康/训练数据，提供 Agent 可调用的"记录（record）"工具，允许用户通过对话记录数据。当前仅支持查询，本次新增"增和改"能力。

## 一、背景与目标

### 1.1 现状

- 项目已有 `TrainingLogQueryToolExecutor` 和 `BodyMetricsQueryToolExecutor` 两个查询工具，仅支持读。
- 业务层 `TrainingService.logTraining` 和 `BodyMetricsService.logBodyMetrics` 已实现 upsert（按 `userId + date` 唯一键自动新增/更新），但仅由 HTTP Controller 调用，Agent 工具未接入写操作。
- 数据库已有 `t_training_log` + `t_training_exercise`、`t_body_metrics` 两类表。

### 1.2 目标

- 新增 5 个 `record` 工具，覆盖五类数据的"增和改"：
  1. `training_log.record`（力量训练，复用现有 Service）
  2. `cardio.record`（有氧训练，新建表 + Service）
  3. `body_metrics.record`（身体指标 + 围度扩展，复用并扩展现有 Service）
  4. `heart_rate.record`（心率，新建表 + Service）
  5. `diet.record`（饮食，新建表 + Service）
- 新增 3 个配套 `query` 工具（cardio/heart_rate/diet），用于更新前获取既有记录合并完整信息（策略 A 全量覆盖的必要配套）。training_log 与 body_metrics 已有 query 工具，不重复建设。
- 用户通过对话即可记录数据，LLM 解析自然语言后调用对应工具。
- 工具内部 upsert，既增又改，符合"通过对话记录数据"的自然语义。

### 1.3 非目标

- 不做删除工具（YAGNI，用户未提）。
- 不做严格的 create/update 分离（统一 record 工具，upsert 模式）。
- 不做写操作前的二次确认（upsert 可覆盖修正，用户说错可再次记录覆盖）。
- 不改造前端，本次仅后端工具能力扩展。

## 二、架构

### 2.1 总体结构

复用现有"接口 + 注册表 + 白名单"工具体系：

```
LLM 决策
  → ToolRouter.execute(toolCall, authenticatedUser)
  → XxxRecordToolExecutor.execute(call, authenticatedUser)
  → 参数校验 + DTO 转换
  → XxxService.logXxx(userId, req, "chat")
  → upsert（按唯一键）
  → ToolResult.ok("已记录...", summary)
  → observation 回灌 LLM
```

### 2.2 设计原则

1. **复用优先**：现有 `TrainingService` / `BodyMetricsService` 的 upsert 逻辑零改动复用，仅扩展 source 参数。
2. **统一 record 语义**：每类数据一个 `record` 工具，upsert 既增又改，LLM 无需判断该 create 还是 update。
3. **后端自动计算**：配速（有氧）、总热量/宏量（饮食）由后端根据明细计算，不让 LLM 填，避免不一致。
4. **source 区分来源**：Agent 写入 `source="chat"`，Controller 写入 `source="manual"`，便于后续分析数据来源。
5. **沿用现有 userId 传递链**：工具从 `execute(call, authenticatedUser)` 第二参数取 userId，与现有查询工具一致。

## 三、数据模型

### 3.1 表清单

| 表 | 类别 | 状态 | 唯一键 |
|---|---|---|---|
| `t_training_log` + `t_training_exercise` | 训练-力量 | 已有 | user_id + training_date |
| `t_cardio_log` | 训练-有氧 | 新增 | user_id + training_date + cardio_type |
| `t_body_metrics` | 身体-综合+围度 | 扩展字段 | user_id + record_date |
| `t_heart_rate` | 身体-心率 | 新增 | user_id + record_date |
| `t_diet_log` + `t_diet_item` | 营养-饮食 | 新增 | user_id + record_date + meal_type |

### 3.2 分类逻辑

- **运动心率**（训练时心率）→ 并入 `t_cardio_log.avg_heart_rate`，不重复建表。
- **静息心率/最大心率/HRV**（反映恢复状态）→ 独立 `t_heart_rate` 表，每日一条。
- **围度**（胸/腰/臀/臂/大腿）→ 扩展 `t_body_metrics` 字段，与体重体脂同属"身体测量"，常一起记录。
- **饮食**按"日期 + 餐次"聚合到主表 `t_diet_log`，明细食物写入 `t_diet_item`，避免单表过宽。

### 3.3 新增表 DDL（要点）

#### t_cardio_log

```sql
CREATE TABLE t_cardio_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  training_date DATE NOT NULL,
  cardio_type   VARCHAR(32) NOT NULL COMMENT 'running/cycling/swimming/rowing/jump_rope/other',
  distance_km   DECIMAL(8,2) COMMENT '距离(公里)',
  duration_minutes INT COMMENT '时长(分钟)',
  avg_pace      VARCHAR(16) COMMENT '配速(自动计算, 格式 mm:ss/km)',
  avg_heart_rate INT COMMENT '平均心率',
  calories_burned INT COMMENT '消耗卡路里',
  note          VARCHAR(500),
  summary       VARCHAR(500),
  source        VARCHAR(16) DEFAULT 'manual',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date_type (user_id, training_date, cardio_type),
  KEY idx_user_date (user_id, training_date)
);
```

#### t_heart_rate

```sql
CREATE TABLE t_heart_rate (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  record_date DATE NOT NULL,
  resting_hr  INT COMMENT '静息心率',
  max_hr      INT COMMENT '最大心率',
  hrv         INT COMMENT '心率变异性(ms)',
  note        VARCHAR(500),
  summary     VARCHAR(500),
  source      VARCHAR(16) DEFAULT 'manual',
  created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date (user_id, record_date)
);
```

#### t_diet_log + t_diet_item

```sql
CREATE TABLE t_diet_log (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id       BIGINT NOT NULL,
  record_date   DATE NOT NULL,
  meal_type     VARCHAR(16) NOT NULL COMMENT 'breakfast/lunch/dinner/snack',
  total_calories INT COMMENT '总热量(自动汇总)',
  total_protein DECIMAL(8,1),
  total_carbs   DECIMAL(8,1),
  total_fat     DECIMAL(8,1),
  note          VARCHAR(500),
  summary       VARCHAR(500),
  source        VARCHAR(16) DEFAULT 'manual',
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_date_meal (user_id, record_date, meal_type),
  KEY idx_user_date (user_id, record_date)
);

CREATE TABLE t_diet_item (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  diet_log_id   BIGINT NOT NULL,
  food_name     VARCHAR(100) NOT NULL,
  portion       VARCHAR(50) COMMENT '份量描述',
  calories      INT,
  protein       DECIMAL(8,1),
  carbs         DECIMAL(8,1),
  fat           DECIMAL(8,1),
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_log (diet_log_id)
);
```

### 3.4 t_body_metrics 扩展字段

新增列（`ALTER TABLE` 或写入 init 脚本）：

```sql
ALTER TABLE t_body_metrics
  ADD COLUMN chest_girth  DECIMAL(6,1) COMMENT '胸围(cm)' AFTER body_fat,
  ADD COLUMN waist_girth  DECIMAL(6,1) COMMENT '腰围(cm)' AFTER chest_girth,
  ADD COLUMN hip_girth    DECIMAL(6,1) COMMENT '臀围(cm)' AFTER waist_girth,
  ADD COLUMN arm_girth    DECIMAL(6,1) COMMENT '臂围(cm)' AFTER hip_girth,
  ADD COLUMN thigh_girth  DECIMAL(6,1) COMMENT '大腿围(cm)' AFTER arm_girth;
```

> 心率相关字段（resting_hr / hrv）放入独立的 `t_heart_rate` 表，不再扩展 t_body_metrics。

## 四、工具设计

### 4.1 通用契约

- 所有 record 工具实现 `ToolExecutor` 接口，`@Component` 注解，Spring 自动注册。
- `descriptor().readOnly = false`（写操作元信息准确，便于后续做权限/审计扩展）。
- `execute(call, authenticatedUser)`：
  - 校验 `authenticatedUser.getUserId()` 非空，否则返回 `ToolResult.error("用户上下文为空")`。
  - 校验 `date` 参数必填且格式 `yyyy-MM-dd`，否则返回 `ToolResult.error(...)`。
  - 从 `call.getArguments()` 提取参数，转换为 DTO。
  - 调用 Service 的 `logXxx(userId, dto, "chat")` 方法。
  - 返回 `ToolResult.ok("已记录...", summary)`，summary 含关键摘要供 LLM 引用。
- 工具内异常由 `ToolRouter.safeExecute` 捕获并转为 `ToolResult.error`，无需自己 try-catch。

### 4.2 五个工具的参数 schema

#### training_log.record（复用现有 logTraining）

```json
{
  "type": "object",
  "properties": {
    "date": {"type": "string", "description": "yyyy-MM-dd，必填"},
    "exercises": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": {"type": "string"},
          "sets": {"type": "integer"},
          "reps": {"type": "integer"},
          "weight": {"type": "number"}
        },
        "required": ["name", "sets", "reps", "weight"]
      }
    }
  },
  "required": ["date", "exercises"]
}
```

- 复用 `TrainingLogRequest` DTO（date + exercises）。
- 复用 `TrainingService.logTraining`，扩展 source 参数。

#### cardio.record（新建）

```json
{
  "type": "object",
  "properties": {
    "date": {"type": "string", "description": "yyyy-MM-dd，必填"},
    "cardio_type": {"type": "string", "enum": ["running","cycling","swimming","rowing","jump_rope","other"]},
    "distance_km": {"type": "number"},
    "duration_minutes": {"type": "integer"},
    "avg_heart_rate": {"type": "integer"},
    "note": {"type": "string"}
  },
  "required": ["date", "cardio_type"]
}
```

- 至少提供 `distance_km` 或 `duration_minutes` 之一（Service 校验）。
- `avg_pace` 由后端计算：`duration_minutes / distance_km`，格式 `mm:ss/km`（distance_km > 0 时）。
- `calories_burned` 暂不计算（可选后续按 MET 值估算），LLM 不填则留空。

#### body_metrics.record（扩展）

```json
{
  "type": "object",
  "properties": {
    "date": {"type": "string", "description": "yyyy-MM-dd，必填"},
    "weight": {"type": "number"},
    "body_fat": {"type": "number"},
    "sleep_hours": {"type": "number"},
    "fatigue": {"type": "string", "enum": ["低","中","高"]},
    "chest_girth": {"type": "number"},
    "waist_girth": {"type": "number"},
    "hip_girth": {"type": "number"},
    "arm_girth": {"type": "number"},
    "thigh_girth": {"type": "number"},
    "note": {"type": "string"}
  },
  "required": ["date"]
}
```

- 扩展 `BodyMetricsLogRequest` 增加围度字段。
- 校验规则：`weight` / `body_fat` / 任一围度字段（chest/waist/hip/arm/thigh_girth）至少一个非空，否则 Service 抛 IllegalArgumentException。其余字段（sleep_hours/fatigue/note）可选。

#### heart_rate.record（新建）

```json
{
  "type": "object",
  "properties": {
    "date": {"type": "string", "description": "yyyy-MM-dd，必填"},
    "resting_hr": {"type": "integer"},
    "max_hr": {"type": "integer"},
    "hrv": {"type": "integer"},
    "note": {"type": "string"}
  },
  "required": ["date"]
}
```

- 至少提供 resting_hr / max_hr / hrv 之一（Service 校验）。

#### diet.record（新建）

```json
{
  "type": "object",
  "properties": {
    "date": {"type": "string", "description": "yyyy-MM-dd，必填"},
    "meal_type": {"type": "string", "enum": ["breakfast","lunch","dinner","snack"]},
    "items": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "name": {"type": "string"},
          "portion": {"type": "string"},
          "calories": {"type": "integer"},
          "protein": {"type": "number"},
          "carbs": {"type": "number"},
          "fat": {"type": "number"}
        },
        "required": ["name"]
      }
    },
    "note": {"type": "string"}
  },
  "required": ["date", "meal_type", "items"]
}
```

- `total_calories` / `total_protein` / `total_carbs` / `total_fat` 由 items 明细求和，后端计算。
- `items` 至少 1 条（Service 校验）。

#### 配套 query 工具（cardio / heart_rate / diet）

三个 query 工具统一沿用现有 `TrainingLogQueryToolExecutor` / `BodyMetricsQueryToolExecutor` 的模式（按 days + limit 查最近记录，readOnly=true）：

| 工具名 | 入参 | 说明 |
|---|---|---|
| `cardio.query` | `{days: 1-180, limit: 1-50}` | 查当前用户最近有氧训练，按 training_date 倒序 |
| `heart_rate.query` | `{days: 1-180, limit: 1-50}` | 查当前用户最近心率记录，按 record_date 倒序 |
| `diet.query` | `{days: 1-180, limit: 1-50}` | 查当前用户最近饮食记录（含 items 明细），按 record_date 倒序 |

- 三个 query 工具的 `readOnly = true`，descriptor 描述格式与现有 query 工具一致。
- 内部直接走 Mapper 查询，无需新增 Service 查询方法（如需可加 `getRecentCardio` 等，但工具内可直接用 Mapper 简化，与现有 query 工具一致）。
- 白名单需追加这 3 个工具名。

### 4.3 source 字段处理

现有 `TrainingServiceImpl.logTraining` 与 `BodyMetricsServiceImpl.logBodyMetrics` 写死 `source = "manual"`。改造方式：

- Service 接口新增重载方法 `logXxx(Long userId, XxxRequest req, String source)`。
- 原方法保留，内部调用新方法并传 `"manual"`（保持 Controller 行为不变，向后兼容）。
- 新建 Service（Cardio / HeartRate / Diet）的 `logXxx` 方法签名直接含 source 参数。
- Agent 工具调用时传 `"chat"`。

> source 取值约束：`manual` / `chat` / `import`。Service 内可校验，非法值默认回落到 `manual`。

## 五、数据流（典型场景）

### 5.1 场景：记录有氧训练

1. 用户："今天跑了 5 公里 30 分钟，平均心率 150"
2. LLM 解析 →（可选先调 `date.now` 获取今天）→ 调 `cardio.record`，args = `{date:"2026-07-03", cardio_type:"running", distance_km:5.0, duration_minutes:30, avg_heart_rate:150}`
3. `CardioRecordToolExecutor.execute`：
   - 校验 userId 非空、date 格式
   - 转 `CardioLogRequest`
   - 调 `cardioService.logCardio(userId, req, "chat")`
4. `CardioServiceImpl.logCardio`：
   - 计算 `avg_pace = "06:00/km"`（30/5）
   - 按 `user_id + training_date + cardio_type` 查既有：存在则更新，不存在则新增
   - 生成 summary = "跑步 5.0km / 30min / 配速 06:00/km / 平均心率 150"
5. 返回 `ToolResult.ok("已记录有氧训练", summary)`
6. LLM 收到 observation → 回复："已帮你记录今天的跑步：5 公里，30 分钟，配速 6:00/km，平均心率 150。"

### 5.2 场景：更新已有记录

1. 用户："昨天那条跑步改成 35 分钟"
2. LLM 应先调 `cardio.query`（或对应查询工具）获取昨天跑步记录 → 合并 duration_minutes=35（其余字段沿用查询结果）→ 调 `cardio.record`，args 含完整字段
3. upsert 命中既有记录 → 全量覆盖 + 重算 avg_pace → 返回已更新
4. 按 §7.1 策略 A：未传字段会被清空，故 LLM 必须先 query 合并完整信息再 record。此引导写入 `agent-system.md`。

## 六、错误处理

| 场景 | 行为 |
|---|---|
| authenticatedUser 为空 / userId 为空 | `ToolResult.error("用户上下文为空")` |
| date 参数缺失或格式非 yyyy-MM-dd | `ToolResult.error("date 参数必填且格式为 yyyy-MM-dd")` |
| 业务校验失败（exercises 空 / items 空 / 至少一个指标非空等） | Service 抛 IllegalArgumentException，被 ToolRouter.safeExecute 捕获转 error |
| 唯一键冲突（并发） | 依赖 upsert 的"先查后写"，并发概率低；若发生则捕获异常转 error |
| 字段类型转换失败（如 LLM 传了字符串"abc"给 number 字段） | 工具内做类型转换，转换失败返回 error 提示具体字段 |

## 七、实现备注

### 7.1 "未传字段是否更新"决策

upsert 更新已有记录时，LLM 可能只传部分字段（如场景 5.2 只传 duration_minutes）。两种策略：

- **策略 A（推荐）：全量覆盖**。LLM 调 record 工具时应提供完整信息，未传字段清空。简单、无歧义。
- 策略 B：部分更新。未传字段保留原值。复杂，需要区分"未传"与"显式置空"。

本次采用**策略 A：全量覆盖**。LLM 在更新前应先调 query 工具获取既有数据，合并后再调 record。这避免了部分更新的复杂语义。实现时 Service 的 update 分支直接用新 DTO 覆盖全部字段。

> 对应 prompt 引导：在 `agent-system.md` 中补充说明"调用 record 工具更新记录时，请先查询既有记录并合并完整信息后再记录"。

### 7.2 summary 生成

- training_log：沿用现有逻辑（取前 3 个动作拼字符串）。
- cardio：`{类型中文名} {distance}km / {duration}min / 配速 {pace} / 平均心率 {hr}`。
- body_metrics：列出非空字段，如 `体重 70.5kg / 体脂 18% / 腰围 80cm`。
- heart_rate：`静息心率 60 / 最大心率 180 / HRV 50ms`。
- diet：`{餐次中文名} 共 {total_calories}kcal (蛋白{p}g/碳水{c}g/脂肪{f}g)`。

### 7.3 配速计算

```
if (distance_km > 0 && duration_minutes != null) {
    double paceSecPerKm = (duration_minutes * 60.0) / distance_km;
    int mm = (int) (paceSecPerKm / 60);
    int ss = (int) Math.round(paceSecPerKm % 60);
    avg_pace = String.format("%02d:%02d/km", mm, ss);
}
```

### 7.4 cardio_type 与 meal_type 的中文名映射

- cardio_type：running→跑步, cycling→骑行, swimming→游泳, rowing→划船, jump_rope→跳绳, other→其他
- meal_type：breakfast→早餐, lunch→午餐, dinner→晚餐, snack→加餐

LLM 传英文枚举值，summary/回复用中文名。

## 八、白名单配置

`application.yml` 的 `fitmate.agent.enabled-tools` 追加：

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

## 九、测试策略

### 9.1 单元测试（每个工具）

- 正常 record：参数合法 → 调用 Service → 返回 ok + summary
- date 缺失 / 格式错 → error
- authenticatedUser 为空 / userId 为空 → error
- 业务校验失败（exercises 空 / items 空 / 指标全空）→ error
- 字段类型转换失败 → error

### 9.2 Service 层测试

- 新增：当日无既有记录 → INSERT
- 更新：当日有既有记录 → UPDATE（全量覆盖）
- source 参数正确写入
- 自动计算字段（配速、热量汇总）正确

### 9.3 集成验证（手动）

- 启动后端，通过对话触发每个工具，验证数据落库
- 同一日重复记录，验证 upsert 更新而非新增
- query 工具能查到 record 工具写入的数据

## 十、文件清单（预估）

### 新增

- 实体：`CardioLog`, `HeartRate`, `DietLog`, `DietItem`
- Mapper：`CardioLogMapper`, `HeartRateMapper`, `DietLogMapper`, `DietItemMapper`
- DTO：`CardioLogRequest`, `HeartRateLogRequest`, `DietLogRequest`, `DietItemDTO`
- Service：`CardioService` + Impl, `HeartRateService` + Impl, `DietService` + Impl
- 工具：`TrainingLogRecordToolExecutor`, `CardioRecordToolExecutor`, `CardioQueryToolExecutor`, `BodyMetricsRecordToolExecutor`, `HeartRateRecordToolExecutor`, `HeartRateQueryToolExecutor`, `DietRecordToolExecutor`, `DietQueryToolExecutor`
- DDL：`fitmate_init.sql` 追加 3 张新表 + t_body_metrics 扩展字段

### 修改

- `TrainingService` / `TrainingServiceImpl`：新增带 source 参数的 logTraining 重载
- `BodyMetricsService` / `BodyMetricsServiceImpl`：新增带 source 参数的 logBodyMetrics 重载 + 围度字段处理
- `BodyMetricsLogRequest`：增加围度字段
- `BodyMetrics` 实体：增加围度字段
- `application.yml`：enabled-tools 追加 5 个工具
- `agent-system.md`：补充 record 工具使用引导（更新前先 query 合并）
