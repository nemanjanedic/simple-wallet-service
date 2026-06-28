package com.nemanjanedic.simplewallet.transfer;

import com.nemanjanedic.simplewallet.account.Account;
import com.nemanjanedic.simplewallet.account.AccountRepository;
import com.nemanjanedic.simplewallet.account.AccountService;
import com.nemanjanedic.simplewallet.account.dto.AccountResponse;
import com.nemanjanedic.simplewallet.account.dto.CreateAccountRequest;
import com.nemanjanedic.simplewallet.common.exception.AccountNotFoundException;
import com.nemanjanedic.simplewallet.common.exception.InsufficientFundsException;
import com.nemanjanedic.simplewallet.common.exception.InvalidAmountException;
import com.nemanjanedic.simplewallet.common.exception.SameAccountTransferException;
import com.nemanjanedic.simplewallet.transaction.TransactionEntry;
import com.nemanjanedic.simplewallet.transaction.TransactionEntryRepository;
import com.nemanjanedic.simplewallet.transaction.TransactionType;
import com.nemanjanedic.simplewallet.transfer.dto.TransferRequest;
import com.nemanjanedic.simplewallet.transfer.dto.TransferResponse;
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
class TransferServiceIntegrationTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionEntryRepository transactionEntryRepository;

    @Test
    void shouldTransferFundsBetweenAccounts() {
        AccountResponse source = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );
        AccountResponse target = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("50.00"))
        );

        TransferResponse response = transferService.transfer(
                new TransferRequest(
                        source.accountId(),
                        target.accountId(),
                        new BigDecimal("30.00"),
                        "Wallet transfer"
                )
        );

        assertThat(response.fromAccountId()).isEqualTo(source.accountId());
        assertThat(response.toAccountId()).isEqualTo(target.accountId());
        assertThat(response.amount()).isEqualByComparingTo("30.00");
        assertThat(response.fromAccountBalance()).isEqualByComparingTo("70.00");
        assertThat(response.toAccountBalance()).isEqualByComparingTo("80.00");
        assertThat(response.referenceId()).isNotBlank();

        Account savedSource = accountRepository.findById(source.accountId()).orElseThrow();
        Account savedTarget = accountRepository.findById(target.accountId()).orElseThrow();

        assertThat(savedSource.getBalance()).isEqualByComparingTo("70.00");
        assertThat(savedTarget.getBalance()).isEqualByComparingTo("80.00");
    }

    @Test
    void shouldCreateTransferOutAndTransferInTransactionEntries() {
        AccountResponse source = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );
        AccountResponse target = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("50.00"))
        );

        TransferResponse response = transferService.transfer(
                new TransferRequest(
                        source.accountId(),
                        target.accountId(),
                        new BigDecimal("30.00"),
                        "Wallet transfer"
                )
        );

        List<TransactionEntry> sourceTransactions = transactionEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(source.accountId(), PageRequest.of(0, 10))
                .getContent();

        List<TransactionEntry> targetTransactions = transactionEntryRepository
                .findByAccountIdOrderByCreatedAtDesc(target.accountId(), PageRequest.of(0, 10))
                .getContent();

        assertThat(sourceTransactions).hasSize(2);
        assertThat(targetTransactions).hasSize(2);

        TransactionEntry sourceLatest = sourceTransactions.getFirst();
        TransactionEntry targetLatest = targetTransactions.getFirst();

        assertThat(sourceLatest.getType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(sourceLatest.getAmount()).isEqualByComparingTo("30.00");
        assertThat(sourceLatest.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(sourceLatest.getDescription()).isEqualTo("Wallet transfer");
        assertThat(sourceLatest.getReferenceId()).isEqualTo(response.referenceId());

        assertThat(targetLatest.getType()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(targetLatest.getAmount()).isEqualByComparingTo("30.00");
        assertThat(targetLatest.getBalanceAfter()).isEqualByComparingTo("80.00");
        assertThat(targetLatest.getDescription()).isEqualTo("Wallet transfer");
        assertThat(targetLatest.getReferenceId()).isEqualTo(response.referenceId());

        assertThat(sourceLatest.getReferenceId()).isEqualTo(targetLatest.getReferenceId());
    }

    @Test
    void shouldRejectTransferWhenFundsAreInsufficient() {
        AccountResponse source = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("20.00"))
        );
        AccountResponse target = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("50.00"))
        );

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(
                        source.accountId(),
                        target.accountId(),
                        new BigDecimal("30.00"),
                        "Too much"
                )
        ))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Account does not have enough funds: " + source.accountId());

        Account savedSource = accountRepository.findById(source.accountId()).orElseThrow();
        Account savedTarget = accountRepository.findById(target.accountId()).orElseThrow();

        assertThat(savedSource.getBalance()).isEqualByComparingTo("20.00");
        assertThat(savedTarget.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldRejectTransferToSameAccount() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(
                        account.accountId(),
                        account.accountId(),
                        new BigDecimal("10.00"),
                        "Same account"
                )
        ))
                .isInstanceOf(SameAccountTransferException.class)
                .hasMessage("Cannot transfer funds to the same account");
    }

    @Test
    void shouldRejectTransferFromMissingAccount() {
        AccountResponse target = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("50.00"))
        );

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(
                        999L,
                        target.accountId(),
                        new BigDecimal("10.00"),
                        "Missing source"
                )
        ))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 999");
    }

    @Test
    void shouldRejectTransferToMissingAccount() {
        AccountResponse source = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(
                        source.accountId(),
                        999L,
                        new BigDecimal("10.00"),
                        "Missing target"
                )
        ))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 999");
    }

    @Test
    void shouldRejectTransferWithZeroAmount() {
        AccountResponse source = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );
        AccountResponse target = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("50.00"))
        );

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(
                        source.accountId(),
                        target.accountId(),
                        new BigDecimal("0.00"),
                        "Invalid"
                )
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");
    }

    @Test
    void shouldRejectTransferAmountWithMoreThanTwoDecimalPlaces() {
        AccountResponse source = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("100.00"))
        );
        AccountResponse target = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("50.00"))
        );

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(
                        source.accountId(),
                        target.accountId(),
                        new BigDecimal("10.999"),
                        "Invalid"
                )
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must have at most 2 decimal places");
    }
}
