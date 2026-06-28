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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransactionEntryRepository transactionEntryRepository;

    @InjectMocks
    private TransferService transferService;

    @Test
    void shouldTransferFundsBetweenAccounts() {
        Account source = account(1L, "100.00");
        Account target = account(2L, "50.00");

        when(accountRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(source, target));

        TransferResponse response = transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("30.00"), "Wallet transfer")
        );

        assertThat(response.fromAccountId()).isEqualTo(1L);
        assertThat(response.toAccountId()).isEqualTo(2L);
        assertThat(response.amount()).isEqualByComparingTo("30.00");
        assertThat(response.fromAccountBalance()).isEqualByComparingTo("70.00");
        assertThat(response.toAccountBalance()).isEqualByComparingTo("80.00");
        assertThat(response.referenceId()).isNotBlank();

        assertThat(source.getBalance()).isEqualByComparingTo("70.00");
        assertThat(target.getBalance()).isEqualByComparingTo("80.00");

        verify(accountRepository).findAllByIdInForUpdate(List.of(1L, 2L));
        verify(transactionEntryRepository, times(2)).save(any(TransactionEntry.class));
    }

    @Test
    void shouldCreateTransferOutAndTransferInEntries() {
        Account source = account(1L, "100.00");
        Account target = account(2L, "50.00");

        when(accountRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(source, target));

        TransferResponse response = transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("30.00"), "Wallet transfer")
        );

        ArgumentCaptor<TransactionEntry> captor = ArgumentCaptor.forClass(TransactionEntry.class);
        verify(transactionEntryRepository, times(2)).save(captor.capture());

        List<TransactionEntry> entries = captor.getAllValues();

        assertThat(entries).hasSize(2);

        TransactionEntry transferOut = entries.get(0);
        TransactionEntry transferIn = entries.get(1);

        assertThat(transferOut.getAccount()).isSameAs(source);
        assertThat(transferOut.getType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(transferOut.getAmount()).isEqualByComparingTo("30.00");
        assertThat(transferOut.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(transferOut.getDescription()).isEqualTo("Wallet transfer");
        assertThat(transferOut.getReferenceId()).isEqualTo(response.referenceId());

        assertThat(transferIn.getAccount()).isSameAs(target);
        assertThat(transferIn.getType()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(transferIn.getAmount()).isEqualByComparingTo("30.00");
        assertThat(transferIn.getBalanceAfter()).isEqualByComparingTo("80.00");
        assertThat(transferIn.getDescription()).isEqualTo("Wallet transfer");
        assertThat(transferIn.getReferenceId()).isEqualTo(response.referenceId());

        assertThat(transferOut.getReferenceId()).isEqualTo(transferIn.getReferenceId());
    }

    @Test
    void shouldLockAccountsInDeterministicOrderEvenWhenTransferDirectionIsReversed() {
        Account source = account(2L, "100.00");
        Account target = account(1L, "50.00");

        when(accountRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(target, source));

        TransferResponse response = transferService.transfer(
                new TransferRequest(2L, 1L, new BigDecimal("30.00"), "Reverse transfer")
        );

        verify(accountRepository).findAllByIdInForUpdate(List.of(1L, 2L));

        assertThat(response.fromAccountId()).isEqualTo(2L);
        assertThat(response.toAccountId()).isEqualTo(1L);
        assertThat(response.fromAccountBalance()).isEqualByComparingTo("70.00");
        assertThat(response.toAccountBalance()).isEqualByComparingTo("80.00");

        assertThat(source.getBalance()).isEqualByComparingTo("70.00");
        assertThat(target.getBalance()).isEqualByComparingTo("80.00");
    }

    @Test
    void shouldRejectTransferWhenFundsAreInsufficient() {
        Account source = account(1L, "20.00");
        Account target = account(2L, "50.00");

        when(accountRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(source, target));

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("30.00"), "Too much")
        ))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Account does not have enough funds: 1");

        assertThat(source.getBalance()).isEqualByComparingTo("20.00");
        assertThat(target.getBalance()).isEqualByComparingTo("50.00");

        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldRejectTransferToSameAccount() {
        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(1L, 1L, new BigDecimal("10.00"), "Same account")
        ))
                .isInstanceOf(SameAccountTransferException.class)
                .hasMessage("Cannot transfer funds to the same account");

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transactionEntryRepository);
    }

    @Test
    void shouldRejectTransferFromMissingAccount() {
        Account target = account(2L, "50.00");

        when(accountRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(target));

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("10.00"), "Missing source")
        ))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 1");

        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldRejectTransferToMissingAccount() {
        Account source = account(1L, "100.00");

        when(accountRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(source));

        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("10.00"), "Missing target")
        ))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessage("Account not found: 2");

        verify(transactionEntryRepository, never()).save(any(TransactionEntry.class));
    }

    @Test
    void shouldRejectTransferWithZeroAmount() {
        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("0.00"), "Invalid amount")
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transactionEntryRepository);
    }

    @Test
    void shouldRejectTransferWithNegativeAmount() {
        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("-10.00"), "Invalid amount")
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must be greater than zero");

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transactionEntryRepository);
    }

    @Test
    void shouldRejectTransferAmountWithMoreThanTwoDecimalPlaces() {
        assertThatThrownBy(() -> transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("10.999"), "Invalid amount")
        ))
                .isInstanceOf(InvalidAmountException.class)
                .hasMessage("Amount must have at most 2 decimal places");

        verifyNoInteractions(accountRepository);
        verifyNoInteractions(transactionEntryRepository);
    }

    @Test
    void shouldTrimTransferDescription() {
        Account source = account(1L, "100.00");
        Account target = account(2L, "50.00");

        when(accountRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(source, target));

        transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("10.00"), "  Wallet transfer  ")
        );

        ArgumentCaptor<TransactionEntry> captor = ArgumentCaptor.forClass(TransactionEntry.class);
        verify(transactionEntryRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(TransactionEntry::getDescription)
                .containsExactly("Wallet transfer", "Wallet transfer");
    }

    @Test
    void shouldStoreNullDescriptionWhenDescriptionIsBlank() {
        Account source = account(1L, "100.00");
        Account target = account(2L, "50.00");

        when(accountRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(source, target));

        transferService.transfer(
                new TransferRequest(1L, 2L, new BigDecimal("10.00"), "   ")
        );

        ArgumentCaptor<TransactionEntry> captor = ArgumentCaptor.forClass(TransactionEntry.class);
        verify(transactionEntryRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(TransactionEntry::getDescription)
                .containsExactly(null, null);
    }

    private Account account(Long id, String balance) {
        Account account = new Account(new BigDecimal(balance));
        ReflectionTestUtils.setField(account, "id", id);
        return account;
    }
}
