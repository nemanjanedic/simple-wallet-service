
package com.nemanjanedic.simplewallet.account.dto;

import java.math.BigDecimal;

public record AccountResponse(
        Long accountId,
        BigDecimal balance
) {
}
