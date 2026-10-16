package com.acme.billing.core;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

class PlanTest {
    @Test void pricesUseExactDecimalArithmetic() {
        assertThat(Plan.STARTER.monthlyRate().add(Plan.GROWTH.monthlyRate())).isEqualByComparingTo(new BigDecimal("198.00"));
        for (Plan plan : Plan.values()) assertThat(plan.monthlyRate().scale()).isEqualTo(2);
    }
}
