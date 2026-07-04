package com.itgeo.fitmate.api.fitness.cardio.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardioMetTableTest {

    @Test
    void getMet_running_returns9_8() {
        assertEquals(9.8, CardioMetTable.getMet("running"));
    }

    @Test
    void getMet_cycling_returns7_5() {
        assertEquals(7.5, CardioMetTable.getMet("cycling"));
    }

    @Test
    void getMet_swimming_returns8_0() {
        assertEquals(8.0, CardioMetTable.getMet("swimming"));
    }

    @Test
    void getMet_rowing_returns7_0() {
        assertEquals(7.0, CardioMetTable.getMet("rowing"));
    }

    @Test
    void getMet_jumpRope_returns12_0() {
        assertEquals(12.0, CardioMetTable.getMet("jump_rope"));
    }

    @Test
    void getMet_other_returns6_0() {
        assertEquals(6.0, CardioMetTable.getMet("other"));
    }

    @Test
    void getMet_unknownType_returnsDefault6_0() {
        assertEquals(6.0, CardioMetTable.getMet("unknown_type"));
    }

    @Test
    void getMet_nullType_returnsDefault6_0() {
        assertEquals(6.0, CardioMetTable.getMet(null));
    }
}
