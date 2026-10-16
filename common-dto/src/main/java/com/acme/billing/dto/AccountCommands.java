package com.acme.billing.dto;

import com.acme.billing.core.*;
import jakarta.validation.constraints.*;

public final class AccountCommands {
    private AccountCommands() {}
    public record Create(
            @NotBlank @Size(max = 120) String company,
            @NotBlank @Email @Size(max = 254) String billingEmail,
            @NotNull Plan plan,
            @Size(max = 500) String justification) {
        public Create(String company, String billingEmail, Plan plan) { this(company, billingEmail, plan, null); }
    }
    public record Update(
            @NotBlank @Size(max = 120) String company,
            @NotBlank @Email @Size(max = 254) String billingEmail,
            @NotNull Plan plan,
            @NotNull AccountStatus status,
            @NotNull @PositiveOrZero Long version,
            @Size(max = 500) String justification) {
        public Update(String company, String billingEmail, Plan plan, AccountStatus status, Long version) {
            this(company, billingEmail, plan, status, version, null);
        }
    }
}
