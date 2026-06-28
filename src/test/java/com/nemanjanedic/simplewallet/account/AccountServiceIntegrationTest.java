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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AccountServiceIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionEntryRepository transactionEntryRepository;

    @Test
    void shouldCreateAccountWithZeroBalanceWhenInitialBalanceIsNotProvided() {
        AccountResponse response = accountService.createAccount(new CreateAccountRequest(null));

        assertThat(response.accountId()).isNotNull();
        assertThat(response.balance()).isEqualByComparingTo("0.00");

        Account savedAccount = accountRepository.findById(response.accountId()).orElseThrow();
        assertThat(savedAccount.getBalance()).isEqualByComparingTo("0.00");

        List<TransactionEntry> transactions = transactionEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(response.accountId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(transactions).isEmpty();
    }

    @Test
    void shouldCreateAccountWithInitialBalance() {
        AccountResponse response = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        assertThat(response.accountId()).isNotNull();
        assertThat(response.balance()).isEqualByComparingTo("100.00");

        Account savedAccount = accountRepository.findById(response.accountId()).orElseThrow();
        assertThat(savedAccount.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void shouldCreateInitialDepositTransactionWhenInitialBalanceIsPositive() {
        AccountResponse response = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        List<TransactionEntry> transactions = transactionEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(response.accountId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(transactions).hasSize(1);

        TransactionEntry transaction = transactions.getFirst();

        assertThat(transaction.getAccount().getId()).isEqualTo(response.accountId());
        assertThat(transaction.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getAmount()).isEqualByComparingTo("100.00");
        assertThat(transaction.getBalanceAfter()).isEqualByComparingTo("100.00");
        assertThat(transaction.getDescription()).isEqualTo("Initial balance");
        assertThat(transaction.getReferenceId()).isNull();
        assertThat(transaction.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldRejectNegativeInitialBalance() {
        assertThatThrownBy(() -> accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("-1.00"))
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Initial balance cannot be negative");
    }

    @Test
    void shouldRejectInitialBalanceWithMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("10.999"))
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must have at most 2 decimal places");
    }

    @Test
    void shouldReturnBalanceForExistingAccount() {
        AccountResponse createdAccount = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("250.00"))
        );

        AccountBalanceResponse balanceResponse = accountService.getBalance(createdAccount.accountId());

        assertThat(balanceResponse.accountId()).isEqualTo(createdAccount.accountId());
        assertThat(balanceResponse.balance()).isEqualByComparingTo("250.00");
    }

    @Test
    void shouldThrowExceptionWhenGettingBalanceForMissingAccount() {
        Long missingAccountId = 999L;

        assertThatThrownBy(() -> accountService.getBalance(missingAccountId))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 999");
    }

    @Test
    void shouldDepositFundsAndCreateTransactionEntry() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        AccountBalanceResponse response = accountService.deposit(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("50.00"), "Top-up")
        );

        assertThat(response.accountId()).isEqualTo(account.accountId());
        assertThat(response.balance()).isEqualByComparingTo("150.00");

        Account savedAccount = accountRepository.findById(account.accountId()).orElseThrow();
        assertThat(savedAccount.getBalance()).isEqualByComparingTo("150.00");

        List<TransactionEntry> transactions = transactionEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(account.accountId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(transactions).hasSize(2);

        TransactionEntry latestTransaction = transactions.get(0);

        assertThat(latestTransaction.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(latestTransaction.getAmount()).isEqualByComparingTo("50.00");
        assertThat(latestTransaction.getBalanceAfter()).isEqualByComparingTo("150.00");
        assertThat(latestTransaction.getDescription()).isEqualTo("Top-up");
        assertThat(latestTransaction.getReferenceId()).isNull();
    }

    @Test
    void shouldWithdrawFundsAndCreateTransactionEntry() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        AccountBalanceResponse response = accountService.withdraw(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("25.00"), "Purchase")
        );

        assertThat(response.accountId()).isEqualTo(account.accountId());
        assertThat(response.balance()).isEqualByComparingTo("75.00");

        Account savedAccount = accountRepository.findById(account.accountId()).orElseThrow();
        assertThat(savedAccount.getBalance()).isEqualByComparingTo("75.00");

        List<TransactionEntry> transactions = transactionEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(account.accountId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(transactions).hasSize(2);

        TransactionEntry latestTransaction = transactions.get(0);

        assertThat(latestTransaction.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(latestTransaction.getAmount()).isEqualByComparingTo("25.00");
        assertThat(latestTransaction.getBalanceAfter()).isEqualByComparingTo("75.00");
        assertThat(latestTransaction.getDescription()).isEqualTo("Purchase");
        assertThat(latestTransaction.getReferenceId()).isNull();
    }

    @Test
    void shouldRejectWithdrawalWhenFundsAreInsufficient() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("50.00"))
        );

        assertThatThrownBy(() -> accountService.withdraw(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("75.00"), "Too much")
        ))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Account does not have enough funds: " + account.accountId());

        Account savedAccount = accountRepository.findById(account.accountId()).orElseThrow();
        assertThat(savedAccount.getBalance()).isEqualByComparingTo("50.00");

        List<TransactionEntry> transactions = transactionEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(account.accountId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getType()).isEqualTo(TransactionType.DEPOSIT);
    }

    @Test
    void shouldRejectDepositWithZeroAmount() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        assertThatThrownBy(() -> accountService.deposit(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("0.00"), "Invalid deposit")
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");
    }

    @Test
    void shouldRejectWithdrawalWithNegativeAmount() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        assertThatThrownBy(() -> accountService.withdraw(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("-10.00"), "Invalid withdrawal")
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");
    }

    @Test
    void shouldRejectOperationAmountWithMoreThanTwoDecimalPlaces() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        assertThatThrownBy(() -> accountService.deposit(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("10.999"), "Invalid amount")
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must have at most 2 decimal places");
    }
}
