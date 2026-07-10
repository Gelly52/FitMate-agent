package com.itgeo.fitmate.api.skill;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * 技能注册表。
 * <p>
 * 启动时扫描 prompts/skills/ 目录下所有 .md 文件，解析为 SkillInfo。
 * 文件格式：前几行为 name/description/trigger 元数据，--- 分隔后为技能正文。
 */
@Component
@Slf4j
public class SkillRegistry {

    private static final String SKILLS_DIR = "prompts/skills";
    private static final String SEPARATOR = "---";

    private final Map<String, SkillInfo> skills = new LinkedHashMap<>();

    @PostConstruct
    public void init() {
        try {
            ClassPathResource dirResource = new ClassPathResource(SKILLS_DIR);
            Resource[] resources = dirResource.exists()
                    ? scanDirectory(dirResource)
                    : new Resource[0];

            if (resources.length == 0) {
                log.warn("技能目录 prompts/skills/ 下未找到任何技能文件");
                return;
            }

            for (Resource resource : resources) {
                try {
                    SkillInfo skill = parseSkill(resource);
                    if (skill != null) {
                        skills.put(skill.getName(), skill);
                        log.info("已加载技能: {}", skill.getName());
                    }
                } catch (Exception e) {
                    log.error("解析技能文件失败: {}", resource.getFilename(), e);
                }
            }
            log.info("技能注册表初始化完成，共加载 {} 个技能", skills.size());
        } catch (Exception e) {
            log.error("技能注册表初始化失败", e);
        }
    }

    private Resource[] scanDirectory(ClassPathResource dirResource) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        return resolver.getResources("classpath*:" + SKILLS_DIR + "/*.md");
    }

    private SkillInfo parseSkill(Resource resource) throws IOException {
        String text = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        int separatorIndex = text.indexOf("\n" + SEPARATOR + "\n");
        if (separatorIndex < 0) {
            log.warn("技能文件 {} 缺少 --- 分隔符，跳过", resource.getFilename());
            return null;
        }

        String metadata = text.substring(0, separatorIndex);
        String content = text.substring(separatorIndex + SEPARATOR.length() + 2).trim();

        SkillInfo skill = new SkillInfo();
        for (String line : metadata.split("\n")) {
            line = line.trim();
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            switch (key) {
                case "name" -> skill.setName(value);
                case "description" -> skill.setDescription(value);
                case "trigger" -> skill.setTrigger(value);
            }
        }

        if (skill.getName() == null || skill.getName().isBlank()) {
            log.warn("技能文件 {} 缺少 name 字段，跳过", resource.getFilename());
            return null;
        }
        skill.setContent(content);
        return skill;
    }

    /**
     * 返回所有技能的元信息列表（不含 content）。
     */
    public List<SkillInfo> listMetadata() {
        List<SkillInfo> result = new ArrayList<>();
        for (SkillInfo skill : skills.values()) {
            SkillInfo meta = new SkillInfo();
            meta.setName(skill.getName());
            meta.setDescription(skill.getDescription());
            meta.setTrigger(skill.getTrigger());
            result.add(meta);
        }
        return result;
    }

    /**
     * 按名称查找技能完整内容。
     */
    public SkillInfo findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return skills.get(name);
    }

    /**
     * 构建注入 Agent prompt 的「## 可用技能」区块文本。
     */
    public String buildSkillsSection() {
        if (skills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n## 可用技能\n");
        sb.append("以下是可用的技能列表。当用户问题匹配某技能的触发条件时，先调用 `skill.load` 工具加载该技能的完整内容，再按技能中的步骤执行。\n\n");
        for (SkillInfo skill : skills.values()) {
            sb.append(skill.toMetadataLine()).append("\n");
        }
        return sb.toString();
    }
}
