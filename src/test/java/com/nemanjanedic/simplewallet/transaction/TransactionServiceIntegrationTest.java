package com.nemanjanedic.simplewallet.transaction;

import com.nemanjanedic.simplewallet.account.AccountService;
import com.nemanjanedic.simplewallet.account.dto.AccountResponse;
import com.nemanjanedic.simplewallet.account.dto.BalanceOperationRequest;
import com.nemanjanedic.simplewallet.account.dto.CreateAccountRequest;
import com.nemanjanedic.simplewallet.common.exception.AccountNotFoundException;
import com.nemanjanedic.simplewallet.transaction.dto.TransactionListResponse;
import com.nemanjanedic.simplewallet.transaction.dto.TransactionResponse;
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
class TransactionServiceIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountService accountService;

    @Test
    void shouldListTransactionsForAccountOrderedByNewestFirst() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        accountService.deposit(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("50.00"), "Top-up")
        );

        accountService.withdraw(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("25.00"), "Purchase")
        );

        TransactionListResponse response = transactionService.listTransactions(
                account.accountId(),
                PageRequest.of(0, 10)
        );

        assertThat(response.accountId()).isEqualTo(account.accountId());
        assertThat(response.transactions()).hasSize(3);
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);

        List<TransactionType> transactionTypes = response.transactions()
                .stream()
                .map(TransactionResponse::type)
                .toList();

        assertThat(transactionTypes).containsExactly(
                TransactionType.WITHDRAWAL,
                TransactionType.DEPOSIT,
                TransactionType.DEPOSIT
        );

        TransactionResponse withdrawal = response.transactions().getFirst();
        assertThat(withdrawal.amount()).isEqualByComparingTo("25.00");
        assertThat(withdrawal.balanceAfter()).isEqualByComparingTo("125.00");
        assertThat(withdrawal.description()).isEqualTo("Purchase");

        TransactionResponse deposit = response.transactions().get(1);
        assertThat(deposit.amount()).isEqualByComparingTo("50.00");
        assertThat(deposit.balanceAfter()).isEqualByComparingTo("150.00");
        assertThat(deposit.description()).isEqualTo("Top-up");

        TransactionResponse initialDeposit = response.transactions().get(2);
        assertThat(initialDeposit.amount()).isEqualByComparingTo("100.00");
        assertThat(initialDeposit.balanceAfter()).isEqualByComparingTo("100.00");
        assertThat(initialDeposit.description()).isEqualTo("Initial balance");
    }

    @Test
    void shouldReturnEmptyTransactionListForAccountWithoutTransactions() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(null)
        );

        TransactionListResponse response = transactionService.listTransactions(
                account.accountId(),
                PageRequest.of(0, 10)
        );

        assertThat(response.accountId()).isEqualTo(account.accountId());
        assertThat(response.transactions()).isEmpty();
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(10);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void shouldSupportPaginationWhenListingTransactions() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        accountService.deposit(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("10.00"), "Deposit 1")
        );

        accountService.deposit(
                account.accountId(),
                new BalanceOperationRequest(new BigDecimal("20.00"), "Deposit 2")
        );

        TransactionListResponse firstPage = transactionService.listTransactions(
                account.accountId(),
                PageRequest.of(0, 2)
        );

        TransactionListResponse secondPage = transactionService.listTransactions(
                account.accountId(),
                PageRequest.of(1, 2)
        );

        assertThat(firstPage.transactions()).hasSize(2);
        assertThat(firstPage.page()).isEqualTo(0);
        assertThat(firstPage.size()).isEqualTo(2);
        assertThat(firstPage.totalElements()).isEqualTo(3);
        assertThat(firstPage.totalPages()).isEqualTo(2);

        assertThat(secondPage.transactions()).hasSize(1);
        assertThat(secondPage.page()).isEqualTo(1);
        assertThat(secondPage.size()).isEqualTo(2);
        assertThat(secondPage.totalElements()).isEqualTo(3);
        assertThat(secondPage.totalPages()).isEqualTo(2);
    }

    @Test
    void shouldThrowExceptionWhenListingTransactionsForMissingAccount() {
        assertThatThrownBy(() -> transactionService.listTransactions(
                999L,
                PageRequest.of(0, 10)
        ))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 999");
    }
}
