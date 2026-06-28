package com.nemanjanedic.simplewallet.transfer;

import com.nemanjanedic.simplewallet.account.Account;
import com.nemanjanedic.simplewallet.account.AccountRepository;
import com.nemanjanedic.simplewallet.common.exception.AccountNotFoundException;
import com.nemanjanedic.simplewallet.common.exception.InsufficientFundsException;
import com.nemanjanedic.simplewallet.common.exception.InvalidAmountException;
import com.nemanjanedic.simplewallet.common.exception.SameAccountTransferException;
import com.nemanjanedic.simplewallet.transaction.TransactionEntry;
import com.nemanjanedic.simplewallet.transaction.TransactionEntryRepository;
import com.nemanjanedic.simplewallet.transaction.TransactionType;
import com.nemanjanedic.simplewallet.transfer.dto.TransferRequest;
import com.nemanjanedic.simplewallet.transfer.dto.TransferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransactionEntryRepository transactionEntryRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransactionEntryRepository transactionEntryRepository
    ) {
        this.accountRepository = accountRepository;
        this.transactionEntryRepository = transactionEntryRepository;
    }

    @Transactional
    public TransferResponse transfer(TransferRequest request) {
        validateAccounts(request.fromAccountId(), request.toAccountId());

        BigDecimal amount = normalizePositiveAmount(request.amount());

        List<Long> accountIdsToLock = Stream.of(request.fromAccountId(), request.toAccountId())
                .sorted()
                .toList();

        List<Account> lockedAccounts = accountRepository.findAllByIdInForUpdate(accountIdsToLock)
                .stream()
                .sorted(Comparator.comparing(Account::getId))
                .toList();

        if (lockedAccounts.size() != 2) {
            validateExistingAccounts(request.fromAccountId(), request.toAccountId(), lockedAccounts);
        }

        Account fromAccount = findAccount(lockedAccounts, request.fromAccountId());
        Account toAccount = findAccount(lockedAccounts, request.toAccountId());

        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromAccount.getId());
        }

        BigDecimal fromAccountNewBalance = fromAccount.getBalance().subtract(amount);
        BigDecimal toAccountNewBalance = toAccount.getBalance().add(amount);

        fromAccount.setBalance(fromAccountNewBalance);
        toAccount.setBalance(toAccountNewBalance);

        String referenceId = UUID.randomUUID().toString();
        String description = normalizeDescription(request.description());

        TransactionEntry transferOutEntry = new TransactionEntry(
                fromAccount,
                TransactionType.TRANSFER_OUT,
                amount,
                fromAccountNewBalance,
                description,
                referenceId
        );

        TransactionEntry transferInEntry = new TransactionEntry(
                toAccount,
                TransactionType.TRANSFER_IN,
                amount,
                toAccountNewBalance,
                description,
                referenceId
        );

        transactionEntryRepository.save(transferOutEntry);
        transactionEntryRepository.save(transferInEntry);

        return new TransferResponse(
                fromAccount.getId(),
                toAccount.getId(),
                amount,
                fromAccount.getBalance(),
                toAccount.getBalance(),
                referenceId
        );
    }

    private void validateAccounts(Long fromAccountId, Long toAccountId) {
        if (fromAccountId == null) {
            throw new AccountNotFoundException(null);
        }

        if (toAccountId == null) {
            throw new AccountNotFoundException(null);
        }

        if (fromAccountId.equals(toAccountId)) {
            throw new SameAccountTransferException();
        }
    }

    private void validateExistingAccounts(Long fromAccountId, Long toAccountId, List<Account> lockedAccounts) {
        boolean fromAccountExists = lockedAccounts.stream()
                .anyMatch(account -> account.getId().equals(fromAccountId));

        boolean toAccountExists = lockedAccounts.stream()
                .anyMatch(account -> account.getId().equals(toAccountId));

        if (!fromAccountExists) {
            throw new AccountNotFoundException(fromAccountId);
        }

        if (!toAccountExists) {
            throw new AccountNotFoundException(toAccountId);
        }
    }

    private Account findAccount(List<Account> accounts, Long accountId) {
        return accounts.stream()
                .filter(account -> account.getId().equals(accountId))
                .findFirst()
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private BigDecimal normalizePositiveAmount(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidAmountException("Amount is required");
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }

        return normalizeScale(amount);
    }

    private BigDecimal normalizeScale(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new InvalidAmountException("Amount must have at most 2 decimal places");
        }
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        return description.trim();
    }
}
