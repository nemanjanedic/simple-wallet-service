package com.nemanjanedic.simplewallet.transaction;

import com.nemanjanedic.simplewallet.transaction.dto.TransactionListResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts/{accountId}/transactions")
public class TransactionController {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public TransactionListResponse listTransactions(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(defaultValue = "" + DEFAULT_SIZE) int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, MAX_SIZE);

        Pageable pageable = PageRequest.of(safePage, safeSize);

        return transactionService.listTransactions(accountId, pageable);
    }
}
