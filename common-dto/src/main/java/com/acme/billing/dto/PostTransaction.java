package com.acme.billing.dto;

import com.acme.billing.core.TransactionKind;
import jakarta.validation.constraints.*;

public record PostTransaction(@NotNull TransactionKind kind,
                              @NotBlank @Pattern(regexp = "[0-9]{1,10}(\\.[0-9]{1,2})?") String amount,
                              @NotBlank @Size(max = 200) String description,
                              @NotNull @PositiveOrZero Long expectedBalanceVersion) {}
