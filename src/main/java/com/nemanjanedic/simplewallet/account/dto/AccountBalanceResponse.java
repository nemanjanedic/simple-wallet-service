
package com.nemanjanedic.simplewallet.account.dto;

import java.math.BigDecimal;

public record AccountBalanceResponse(
        Long accountId,
        BigDecimal balance
) {
}
