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
