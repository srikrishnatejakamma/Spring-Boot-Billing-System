package com.acme.billing.core;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

class MoneyTest {
    @Test void exactDecimalArithmeticPreservesTheCent() {
        assertThat(Money.positive("0.10").add(Money.positive("0.20"))).isEqualByComparingTo("0.30");
        assertThat(Money.positive("9999999999.99")).isEqualByComparingTo(new BigDecimal("9999999999.99"));
    }
    @Test void rejectsNonPositiveFractionalCentAndOverflowingValues() {
        for (String value : new String[]{"0", "-1.00", "0.001", "10000000000.00", "NaN", null})
            assertThatThrownBy(() -> Money.positive(value)).isInstanceOf(IllegalArgumentException.class);
    }
}
