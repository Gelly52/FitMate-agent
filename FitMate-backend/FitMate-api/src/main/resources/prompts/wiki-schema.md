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

## create vs update 规则（强制）

- `create`：用于 slug **不存在**的新页面，必须返回 `page_type` / `title` / `slug` / `content_md`
- `update`：仅用于 slug **已存在**的页面，`title` / `page_type` 可省略
- **禁止**用 `update` 创建新页面。若不确定 slug 是否存在，按 `create` 处理并补全必填字段
- 判断依据：`当前 INDEX 页` 中列出的 slug 即为已存在

## 输出格式（强制 JSON）

LLM 编译时必须输出如下 JSON：

```json
{
  "actions": [
    {"action": "create", "page_type": "SOURCE_SUMMARY", "title": "...", "slug": "...", "content_md": "...", "links": ["slug1", "slug2"]},
    {"action": "update", "slug": "existing-slug", "content_md": "..."},
    {"action": "update_index", "content_md": "..."},
    {"action": "append_log", "entry": "## YYYY-MM-DD\n- 新增SOURCE_SUMMARY：[[文档标题-摘要]]\n- 新增CONCEPT：[[概念标题]]"}
  ]
}
```

## append_log 格式规范

- entry 以 `## YYYY-MM-DD` 日期标题开头（当天日期，不要用方括号）
- 日期标题下用 `- ` 列表项描述本次变更
- 涉及的页面必须用 `[[页面标题]]` 包裹，便于跳转
- 禁止使用 `## [YYYY-MM-DD] type | title` 这种单行格式
