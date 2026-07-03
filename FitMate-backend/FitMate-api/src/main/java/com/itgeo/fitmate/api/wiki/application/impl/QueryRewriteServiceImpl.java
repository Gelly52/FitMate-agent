package com.itgeo.fitmate.api.wiki.application.impl;

import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.prompt.PromptTemplateManager;
import com.itgeo.fitmate.api.wiki.application.QueryRewriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class QueryRewriteServiceImpl implements QueryRewriteService {

    private final ChatModel chatModel;
    private final PromptTemplateManager promptTemplateManager;

    @Override
    public String rewrite(String question, String wikiContent) {
        if (StrUtil.isBlank(wikiContent)) {
            return question;
        }
        try {
            String promptText = promptTemplateManager.buildQueryRewritePrompt(question, wikiContent);
            String rewritten = chatModel.call(new Prompt(promptText))
                    .getResult()
                    .getOutput()
                    .getText();
            String trimmed = rewritten == null ? question : rewritten.trim();
            log.debug("Query rewrite: [{}] -> [{}]", question, trimmed);
            return StrUtil.isBlank(trimmed) ? question : trimmed;
        } catch (Exception e) {
            log.warn("Query rewrite 失败，返回原问题: {}", e.getMessage());
            return question;
        }
    }
}
