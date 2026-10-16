package com.acme.billing.core;

import java.math.BigDecimal;

public enum Plan {
    STARTER("49.00"), GROWTH("149.00"), ENTERPRISE("499.00");

    private final BigDecimal monthlyRate;
    Plan(String monthlyRate) { this.monthlyRate = new BigDecimal(monthlyRate); }
    public BigDecimal monthlyRate() { return monthlyRate; }
}
