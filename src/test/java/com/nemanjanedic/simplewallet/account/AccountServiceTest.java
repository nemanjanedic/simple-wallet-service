package com.nemanjanedic.simplewallet.account;

import com.nemanjanedic.simplewallet.account.dto.AccountBalanceResponse;
import com.nemanjanedic.simplewallet.account.dto.AccountResponse;
import com.nemanjanedic.simplewallet.account.dto.BalanceOperationRequest;
import com.nemanjanedic.simplewallet.account.dto.CreateAccountRequest;
import com.nemanjanedic.simplewallet.common.exception.AccountNotFoundException;
import com.nemanjanedic.simplewallet.common.exception.InsufficientFundsException;
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

    @Test
    void shouldDepositFunds() {
        Account account = new Account(new BigDecimal("100.00"));
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        AccountBalanceResponse response = accountService.deposit(
                1L,
                new BalanceOperationRequest(new BigDecimal("50.00"), "Top-up")
        );

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualByComparingTo("150.00");
        assertThat(account.getBalance()).isEqualByComparingTo("150.00");

        ArgumentCaptor<TransactionEntry> captor = ArgumentCaptor.forClass(TransactionEntry.class);
        verify(transactionEntryRepository).save(captor.capture());

        TransactionEntry transactionEntry = captor.getValue();

        assertThat(transactionEntry.getAccount()).isEqualTo(account);
        assertThat(transactionEntry.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transactionEntry.getAmount()).isEqualByComparingTo("50.00");
        assertThat(transactionEntry.getBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(transactionEntry.getDescription()).isEqualTo("Top-up");
        assertThat(transactionEntry.getReferenceId()).isNull();
    }

    @Test
    void shouldWithdrawFunds() {
        Account account = new Account(new BigDecimal("100.00"));
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        AccountBalanceResponse response = accountService.withdraw(
                1L,
                new BalanceOperationRequest(new BigDecimal("25.00"), "Purchase")
        );

        assertThat(response.accountId()).isEqualTo(1L);
        assertThat(response.balance()).isEqualByComparingTo("75.00");
        assertThat(account.getBalance()).isEqualByComparingTo("75.00");

        ArgumentCaptor<TransactionEntry> captor = ArgumentCaptor.forClass(TransactionEntry.class);
        verify(transactionEntryRepository).save(captor.capture());

        TransactionEntry transactionEntry = captor.getValue();

        assertThat(transactionEntry.getAccount()).isEqualTo(account);
        assertThat(transactionEntry.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(transactionEntry.getAmount()).isEqualByComparingTo("25.00");
        assertThat(transactionEntry.getBalanceAfter()).isEqualByComparingTo("75.00");
        assertThat(transactionEntry.getDescription()).isEqualTo("Purchase");
        assertThat(transactionEntry.getReferenceId()).isNull();
    }

    @Test
    void shouldRejectWithdrawalWhenFundsAreInsufficient() {
        Account account = new Account(new BigDecimal("50.00"));
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> accountService.withdraw(
                1L,
                new BalanceOperationRequest(new BigDecimal("75.00"), "Too much")
        ))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Account does not have enough funds: 1");

        assertThat(account.getBalance()).isEqualByComparingTo("50.00");

        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldRejectDepositForMissingAccount() {
        when(accountRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deposit(
                999L,
                new BalanceOperationRequest(new BigDecimal("10.00"), "Top-up")
        ))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 999");

        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldRejectWithdrawalForMissingAccount() {
        when(accountRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.withdraw(
                999L,
                new BalanceOperationRequest(new BigDecimal("10.00"), "Purchase")
        ))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 999");

        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldRejectDepositWithZeroAmount() {
        assertThatThrownBy(() -> accountService.deposit(
                1L,
                new BalanceOperationRequest(new BigDecimal("0.00"), "Invalid")
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");

        verify(accountRepository, never()).findByIdForUpdate(any());
        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldRejectWithdrawalWithNegativeAmount() {
        assertThatThrownBy(() -> accountService.withdraw(
                1L,
                new BalanceOperationRequest(new BigDecimal("-10.00"), "Invalid")
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");

        verify(accountRepository, never()).findByIdForUpdate(any());
        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldTrimDescriptionForDeposit() {
        Account account = new Account(new BigDecimal("100.00"));
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        accountService.deposit(
                1L,
                new BalanceOperationRequest(new BigDecimal("10.00"), "  Top-up  ")
        );

        ArgumentCaptor<TransactionEntry> captor = ArgumentCaptor.forClass(TransactionEntry.class);
        verify(transactionEntryRepository).save(captor.capture());

        assertThat(captor.getValue().getDescription()).isEqualTo("Top-up");
    }

    @Test
    void shouldStoreNullDescriptionWhenDescriptionIsBlank() {
        Account account = new Account(new BigDecimal("100.00"));
        ReflectionTestUtils.setField(account, "id", 1L);

        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(account));

        accountService.deposit(
                1L,
                new BalanceOperationRequest(new BigDecimal("10.00"), "   ")
        );

        ArgumentCaptor<TransactionEntry> captor = ArgumentCaptor.forClass(TransactionEntry.class);
        verify(transactionEntryRepository).save(captor.capture());

        assertThat(captor.getValue().getDescription()).isNull();
    }
}
