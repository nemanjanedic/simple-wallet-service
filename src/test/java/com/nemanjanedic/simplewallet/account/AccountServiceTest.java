package com.nemanjanedic.simplewallet.account;

import com.nemanjanedic.simplewallet.account.dto.AccountBalanceResponse;
import com.nemanjanedic.simplewallet.account.dto.AccountResponse;
import com.nemanjanedic.simplewallet.account.dto.CreateAccountRequest;
import com.nemanjanedic.simplewallet.common.exception.AccountNotFoundException;
import com.nemanjanedic.simplewallet.common.exception.InvalidAmountException;
import com.nemanjanedic.simplewallet.transaction.TransactionEntry;
import com.nemanjanedic.simplewallet.transaction.TransactionEntryRepository;
import com.nemanjanedic.simplewallet.transaction.TransactionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionEntryRepository transactionEntryRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldCreateAccountWithZeroBalanceWhenInitialBalanceIsNull() {
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 1L);
            return account;
        });

        AccountResponse response = accountService.createAccount(new CreateAccountRequest(null));

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualByComparingTo("0.00");

        verify(accountRepository).save(any(Account.class));
        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldCreateAccountWithInitialBalance() {
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 1L);
            return account;
        });

        AccountResponse response = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualByComparingTo("100.00");

        verify(accountRepository).save(any(Account.class));
    }

    @Test
    void shouldCreateInitialDepositTransactionWhenInitialBalanceIsPositive() {
        when(accountRepository.save(any(Account.class))).thenAnswer(invocation -> {
            Account account = invocation.getArgument(0);
            ReflectionTestUtils.setField(account, "id", 1L);
            return account;
        });

        accountService.createAccount(new CreateAccountRequest(new BigDecimal("100.00")));

        ArgumentCaptor<TransactionEntry> captor = ArgumentCaptor.forClass(TransactionEntry.class);

        verify(transactionEntryRepository).save(captor.capture());

        TransactionEntry transactionEntry = captor.getValue();

        assertThat(transactionEntry.getAccount().getId()).isEqualTo(1L);
        assertThat(transactionEntry.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transactionEntry.getAmount()).isEqualByComparingTo("100.00");
        assertThat(transactionEntry.getBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(transactionEntry.getDescription()).isEqualTo("Initial balance");
        assertThat(transactionEntry.getReferenceId()).isNull();
    }

    @Test
    void shouldRejectNegativeInitialBalance() {
        assertThatThrownBy(() -> accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("-1.00"))
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Initial balance cannot be negative");

        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldRejectInitialBalanceWithMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("10.999"))
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must have at most 2 decimal places");

        verify(accountRepository, never()).save(any(Account.class));
        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldReturnBalanceForExistingAccount() {
        Account account = new Account(new BigDecimal("250.00"));
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));

        AccountBalanceResponse response = accountService.getBalance(1L);

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualByComparingTo("250.00");
    }

    @Test
    void shouldThrowExceptionWhenGettingBalanceForMissingAccount() {
        when(accountRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getBalance(999L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 999");
    }
}
