package com.nemanjanedic.simplewallet.account;

import com.nemanjanedic.simplewallet.account.dto.AccountBalanceResponse;
import com.nemanjanedic.simplewallet.account.dto.AccountResponse;
import com.nemanjanedic.simplewallet.account.dto.CreateAccountRequest;
import com.nemanjanedic.simplewallet.common.exception.AccountNotFoundException;
import com.nemanjanedic.simplewallet.common.exception.InvalidAmountException;
import com.nemanjanedic.simplewallet.transaction.TransactionEntry;
import com.nemanjanedic.simplewallet.transaction.TransactionEntryRepository;
import com.nemanjanedic.simplewallet.transaction.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class AccountService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);

    private final AccountRepository accountRepository;
    private final TransactionEntryRepository transactionEntryRepository;

    public AccountService(
            AccountRepository accountRepository,
            TransactionEntryRepository transactionEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionEntryRepository = transactionEntryRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        BigDecimal initialBalance = normalizeInitialBalance(request.initialBalance());

        Account account = new Account(initialBalance);
        Account savedAccount = accountRepository.save(account);

        if (initialBalance.compareTo(ZERO) > 0) {
            TransactionEntry transactionEntry = new TransactionEntry(
                    savedAccount,
                    TransactionType.DEPOSIT,
                    initialBalance,
                    savedAccount.getBalance(),
                    "Initial balance",
                    null
            );

            transactionEntryRepository.save(transactionEntry);
        }

        return new AccountResponse(savedAccount.getId(), savedAccount.getBalance());
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getBalance(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        return new AccountBalanceResponse(account.getId(), account.getBalance());
    }

    private BigDecimal normalizeInitialBalance(BigDecimal amount) {
        if (amount == null) {
            return ZERO;
        }

        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidAmountException("Initial balance cannot be negative");
        }

        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new InvalidAmountException("Amount must have at most 2 decimal places");
        }
    }
}
