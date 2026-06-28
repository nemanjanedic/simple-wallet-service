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
}
