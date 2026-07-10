## 知识库 Wiki（预检索）
针对用户问题「{question}」，已从知识库 Wiki 中预检索到以下编译后内容。

检索策略：
- 若以下内容已足够回答用户问题，直接给出 final；
- 若不足且下方内容非空，可调用 `kb.search` 用改写后的 query 进一步检索；
- 若下方内容为空或与用户问题明显无关，禁止调用 `kb.search` 或 `rag.search`，直接基于已有信息给出 final。

{wikiContent}
