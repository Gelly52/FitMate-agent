# FitMate Wiki Schema

本文件定义 LLM 维护 Wiki 时的结构与工作流约定。

## 页面类型

- `INDEX`：每个 space 唯一的目录页，列出所有页面（链接 + 一句话摘要 + 类别）
- `ENTITY`：实体页（人物/动作/器材/部位等）
- `CONCEPT`：概念页（训练原理/营养学概念等）
- `SYNTHESIS`：综合页（跨多源的主题综合，如"增肌期蛋白质摄入策略"）
- `SOURCE_SUMMARY`：单源摘要页（一篇原始文档的摘要）
- `LOG`：可选，空间操作日志页（与 t_wiki_log 表互补）

## 命名规则

- `slug` 使用小写英文 + 连字符，如 `protein-intake`、`bench-press`
- 标题可中文
- 每个 space 必有 `INDEX` 页（slug 固定为 `index`）

## wikilink 约定

- 页面间用 `[[slug]]` 或 `[[slug|显示文本]]` 互链
- 编译时 LLM 输出的 `links` 字段是目标页面的 slug 列表

## Ingest 工作流

1. 读原始资料
2. 生成/更新 `SOURCE_SUMMARY` 页（一篇文档一个）
3. 更新相关 `ENTITY` / `CONCEPT` / `SYNTHESIS` 页（增量合并，不覆盖原有内容）
4. 更新 `INDEX` 页
5. 追加 `LOG` 条目

## 输出格式（强制 JSON）

LLM 编译时必须输出如下 JSON：

```json
{
  "actions": [
    {"action": "create", "page_type": "SOURCE_SUMMARY", "title": "...", "slug": "...", "content_md": "...", "links": ["slug1", "slug2"]},
    {"action": "update", "slug": "existing-slug", "content_md": "..."},
    {"action": "update_index", "content_md": "..."},
    {"action": "append_log", "entry": "## [YYYY-MM-DD] ingest | 文档标题"}
  ],
  "memory_extraction": [
    {"type": "FACT", "content": "用户目标减脂到15%体脂", "metadata": {"category": "goal", "tags": ["减脂"]}}
  ]
}
```

## 记忆提取（可选）

如果原始资料中明确包含用户本人的训练目标、身体条件、饮食偏好、伤病史、训练历史、
或用户明显关注的训练领域，请在 `memory_extraction` 字段中提取为 FACT 类型。

高阈值：仅在明确涉及用户个人化信息时提取，通用健身知识不要提取。
无个人化信息时返回空数组 `[]`。

每条记忆的 metadata 可包含 category（goal/body_condition/diet/injury/training_history/interest）和 tags。
