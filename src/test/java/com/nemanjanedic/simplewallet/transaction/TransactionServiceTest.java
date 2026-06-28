package com.nemanjanedic.simplewallet.transaction;

import com.nemanjanedic.simplewallet.account.Account;
import com.nemanjanedic.simplewallet.account.AccountRepository;
import com.nemanjanedic.simplewallet.common.exception.AccountNotFoundException;
import com.nemanjanedic.simplewallet.transaction.dto.TransactionListResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionEntryRepository transactionEntryRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void shouldListTransactionsForExistingAccount() {
        Long accountId = 1L;
        PageRequest pageable = PageRequest.of(0, 10);

        Account account = account(accountId, "125.00");

        TransactionEntry withdrawal = transactionEntry(
                3L,
                account,
                TransactionType.WITHDRAWAL,
                "25.00",
                "125.00",
                "Purchase",
                null
        );

        TransactionEntry deposit = transactionEntry(
                2L,
                account,
                TransactionType.DEPOSIT,
                "50.00",
                "150.00",
                "Top-up",
                null
        );

        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(transactionEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable))
                .thenReturn(new PageImpl<>(List.of(withdrawal, deposit), pageable, 2));

        TransactionListResponse response = transactionService.listTransactions(accountId, pageable);

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.transactions()).hasSize(2);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(1);

        assertThat(response.transactions().getFirst().id()).isEqualTo(3L);
        assertThat(response.transactions().getFirst().type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(response.transactions().getFirst().amount()).isEqualByComparingTo("25.00");
        assertThat(response.transactions().get(0).balanceAfter()).isEqualByComparingTo("125.00");
        assertThat(response.transactions().get(0).description()).isEqualTo("Purchase");

        assertThat(response.transactions().get(1).id()).isEqualTo(2L);
        assertThat(response.transactions().get(1).type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(response.transactions().get(1).amount()).isEqualByComparingTo("50.00");
        assertThat(response.transactions().get(1).balanceAfter()).isEqualByComparingTo("150.00");
        assertThat(response.transactions().get(1).description()).isEqualTo("Top-up");

        verify(accountRepository).existsById(accountId);
        verify(transactionEntryRepository).findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    @Test
    void shouldReturnEmptyListWhenAccountHasNoTransactions() {
        Long accountId = 1L;
        PageRequest pageable = PageRequest.of(0, 10);

        when(accountRepository.existsById(accountId)).thenReturn(true);
        when(transactionEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        TransactionListResponse response = transactionService.listTransactions(accountId, pageable);

        assertThat(response.accountId()).isEqualTo(accountId);
        assertThat(response.transactions()).isEmpty();
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();

        verify(accountRepository).existsById(accountId);
        verify(transactionEntryRepository).findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    @Test
    void shouldThrowExceptionWhenAccountDoesNotExist() {
        Long accountId = 999L;
        PageRequest pageable = PageRequest.of(0, 10);

        when(accountRepository.existsById(accountId)).thenReturn(false);

        assertThatThrownBy(() -> transactionService.listTransactions(accountId, pageable))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 999");

        verify(accountRepository).existsById(accountId);
        verify(transactionEntryRepository, never())
                .findByAccountIdOrderByCreatedAtDesc(accountId, pageable);
    }

    @Test
    void shouldThrowExceptionWhenAccountIdIsNull() {
        PageRequest pageable = PageRequest.of(0, 10);

        assertThatThrownBy(() -> transactionService.listTransactions(null, pageable))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: null");

        verify(transactionEntryRepository, never())
                .findByAccountIdOrderByCreatedAtDesc(null, pageable);
    }

    private Account account(Long id, String balance) {
        Account account = new Account(new BigDecimal(balance));
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }

    private TransactionEntry transactionEntry(
            Long id,
            Account account,
            TransactionType type,
            String amount,
            String balanceAfter,
            String description,
            String referenceId
    ) {
        TransactionEntry transactionEntry = new TransactionEntry(
                account,
                type,
                new BigDecimal(amount),
                new BigDecimal(balanceAfter),
                description,
                referenceId
        );

        ReflectionTestUtils.setField(transactionEntry, "id", id);
        ReflectionTestUtils.setField(transactionEntry, "createdAt", Instant.now());

        return transactionEntry;
    }
}
