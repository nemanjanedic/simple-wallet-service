package com.nemanjanedic.simplewallet.transaction;

import com.nemanjanedic.simplewallet.account.AccountRepository;
import com.nemanjanedic.simplewallet.common.exception.AccountNotFoundException;
import com.nemanjanedic.simplewallet.transaction.dto.TransactionListResponse;
import com.nemanjanedic.simplewallet.transaction.dto.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionEntryRepository transactionEntryRepository;

    public TransactionService(
            AccountRepository accountRepository,
            TransactionEntryRepository transactionEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionEntryRepository = transactionEntryRepository;
    }

    @Transactional(readOnly = true)
    public TransactionListResponse listTransactions(Long accountId, Pageable pageable) {
        if (accountId == null || !accountRepository.existsById(accountId)) {
            throw new AccountNotFoundException(accountId);
        }

        Page<TransactionEntry> transactionPage =
                transactionEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable);

        List<TransactionResponse> transactions = transactionPage.getContent()
                .stream()
                .map(TransactionResponse::from)
                .toList();

        return new TransactionListResponse(
                accountId,
                transactions,
                transactionPage.getNumber(),
                transactionPage.getSize(),
                transactionPage.getTotalElements(),
                transactionPage.getTotalPages()
        );
    }
}
