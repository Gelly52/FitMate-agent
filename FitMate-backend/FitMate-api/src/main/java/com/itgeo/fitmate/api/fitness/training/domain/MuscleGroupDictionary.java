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
