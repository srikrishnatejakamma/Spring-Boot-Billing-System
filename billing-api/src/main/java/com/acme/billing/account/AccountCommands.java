package com.acme.billing.account;

import com.acme.billing.core.*;
import jakarta.validation.constraints.*;

public final class AccountCommands {
    private AccountCommands() {}
    public record Create(
            @NotBlank @Size(max = 120) String company,
            @NotBlank @Email @Size(max = 254) String billingEmail,
            @NotNull Plan plan) {}
    public record Update(
            @NotBlank @Size(max = 120) String company,
            @NotBlank @Email @Size(max = 254) String billingEmail,
            @NotNull Plan plan,
            @NotNull AccountStatus status,
            @NotNull @PositiveOrZero Long version) {}
}
