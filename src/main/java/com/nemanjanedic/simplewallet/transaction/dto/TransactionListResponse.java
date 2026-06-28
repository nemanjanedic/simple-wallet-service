package com.nemanjanedic.simplewallet.transaction.dto;

import java.util.List;

public record TransactionListResponse(
        Long accountId,
        List<TransactionResponse> transactions,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
