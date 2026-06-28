package com.nemanjanedic.simplewallet.transaction.dto;

import com.nemanjanedic.simplewallet.transaction.TransactionEntry;
import com.nemanjanedic.simplewallet.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String referenceId,
        Instant createdAt
) {

    public static TransactionResponse from(TransactionEntry transactionEntry) {
        return new TransactionResponse(
                transactionEntry.getId(),
                transactionEntry.getType(),
                transactionEntry.getAmount(),
                transactionEntry.getBalanceAfter(),
                transactionEntry.getDescription(),
                transactionEntry.getReferenceId(),
                transactionEntry.getCreatedAt()
        );
    }
}
