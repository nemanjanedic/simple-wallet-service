package com.nemanjanedic.simplewallet.transfer.dto;

import java.math.BigDecimal;

public record TransferResponse(
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        BigDecimal fromAccountBalance,
        BigDecimal toAccountBalance,
        String referenceId
) {
}
