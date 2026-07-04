package com.itgeo.fitmate.api.fitness.training.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MuscleGroupDictionaryTest {

    @Test
    void inferMuscleGroup_benchPress_returnsChest() {
        assertEquals("胸肌", MuscleGroupDictionary.inferMuscleGroup("杠铃卧推"));
    }

    @Test
    void inferMuscleGroup_squat_returnsQuads() {
        assertEquals("股四头肌", MuscleGroupDictionary.inferMuscleGroup("杠铃深蹲"));
    }

    @Test
    void inferMuscleGroup_deadlift_returnsBack() {
        assertEquals("背部", MuscleGroupDictionary.inferMuscleGroup("传统硬拉"));
    }

    @Test
    void inferMuscleGroup_pullUp_returnsLats() {
        assertEquals("背阔肌", MuscleGroupDictionary.inferMuscleGroup("引体向上"));
    }

    @Test
    void inferMuscleGroup_bicepCurl_returnsBiceps() {
        assertEquals("肱二头肌", MuscleGroupDictionary.inferMuscleGroup("哑铃弯举"));
    }

    @Test
    void inferMuscleGroup_unknownMovement_returnsNull() {
        assertNull(MuscleGroupDictionary.inferMuscleGroup("未知动作"));
    }

    @Test
    void inferMuscleGroup_nullOrBlank_returnsNull() {
        assertNull(MuscleGroupDictionary.inferMuscleGroup(null));
        assertNull(MuscleGroupDictionary.inferMuscleGroup(""));
        assertNull(MuscleGroupDictionary.inferMuscleGroup("   "));
    }

    @Test
    void inferMuscleGroup_priorityMultipleMatches_returnsFirstMatch() {
        // "卧推" 优先于 "推举"，应返回胸肌
        assertEquals("胸肌", MuscleGroupDictionary.inferMuscleGroup("上斜卧推"));
    }
}
