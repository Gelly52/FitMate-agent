package com.itgeo.fitmate.api.skill;

import lombok.Data;

/**
 * 技能元信息（L1 层，注入 Agent prompt 的轻量数据）。
 */
@Data
public class SkillInfo {

    /** 技能名称（中文，如"分析本周训练"） */
    private String name;

    /** 一句话描述 */
    private String description;

    /** 触发条件描述 */
    private String trigger;

    /** 完整技能内容（L2 层，仅在 skill.load 调用时返回） */
    private String content;

    /**
     * 返回 L1 层的简短描述文本，用于注入 Agent prompt 的「## 可用技能」区块。
     */
    public String toMetadataLine() {
        return String.format("- %s：%s（触发：%s）", name, description, trigger);
    }
}
