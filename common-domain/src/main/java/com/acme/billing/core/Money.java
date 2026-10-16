package com.acme.billing.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}
    public static BigDecimal positive(String text) {
        BigDecimal amount;
        try { amount = new BigDecimal(text).setScale(2, RoundingMode.UNNECESSARY); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Amount must be an exact decimal with at most two fractional digits"); }
        if (amount.signum() <= 0 || amount.precision() > 12) throw new IllegalArgumentException("Amount must be positive and fit DECIMAL(12,2)");
        return amount;
    }
}
