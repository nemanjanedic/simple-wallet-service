package com.nemanjanedic.simplewallet.concurrency;

import com.nemanjanedic.simplewallet.account.AccountService;
import com.nemanjanedic.simplewallet.account.dto.AccountBalanceResponse;
import com.nemanjanedic.simplewallet.account.dto.AccountResponse;
import com.nemanjanedic.simplewallet.account.dto.BalanceOperationRequest;
import com.nemanjanedic.simplewallet.account.dto.CreateAccountRequest;
import com.nemanjanedic.simplewallet.common.exception.InsufficientFundsException;
import com.nemanjanedic.simplewallet.transfer.TransferService;
import com.nemanjanedic.simplewallet.transfer.dto.TransferRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WalletConcurrencyIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransferService transferService;

    @Test
    void concurrentWithdrawalsShouldNotOverdrawAccount() throws Exception {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("1000.00"))
        );

        int numberOfRequests = 20;
        BigDecimal withdrawalAmount = new BigDecimal("100.00");

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfRequests);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successfulWithdrawals = new AtomicInteger();
        AtomicInteger failedWithdrawals = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < numberOfRequests; i++) {
            tasks.add(() -> {
                startLatch.await();

                try {
                    accountService.withdraw(
                            account.accountId(),
                            new BalanceOperationRequest(withdrawalAmount, "Concurrent withdrawal")
                    );
                    successfulWithdrawals.incrementAndGet();
                } catch (InsufficientFundsException ex) {
                    failedWithdrawals.incrementAndGet();
                }

                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();

        for (Callable<Void> task : tasks) {
            futures.add(executorService.submit(task));
        }

        startLatch.countDown();

        for (Future<Void> future : futures) {
            future.get();
        }

        executorService.shutdown();

        AccountBalanceResponse finalBalance = accountService.getBalance(account.accountId());

        assertThat(successfulWithdrawals.get()).isEqualTo(10);
        assertThat(failedWithdrawals.get()).isEqualTo(10);
        assertThat(finalBalance.balance()).isEqualByComparingTo("0.00");
    }

    @Test
    void concurrentTransfersShouldPreserveTotalFundsAcrossAccounts() throws Exception {
        AccountResponse firstAccount = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("1000.00"))
        );

        AccountResponse secondAccount = accountService.createAccount(
                new CreateAccountRequest(new BigDecimal("1000.00"))
        );

        int transfersInEachDirection = 50;
        BigDecimal transferAmount = new BigDecimal("10.00");

        ExecutorService executorService = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Callable<Void>> tasks = new ArrayList<>();

        for (int i = 0; i < transfersInEachDirection; i++) {
            tasks.add(() -> {
                startLatch.await();

                transferService.transfer(
                        new TransferRequest(
                                firstAccount.accountId(),
                                secondAccount.accountId(),
                                transferAmount,
                                "Concurrent transfer A to B"
                        )
                );

                return null;
            });

            tasks.add(() -> {
                startLatch.await();

                transferService.transfer(
                        new TransferRequest(
                                secondAccount.accountId(),
                                firstAccount.accountId(),
                                transferAmount,
                                "Concurrent transfer B to A"
                        )
                );

                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();

        for (Callable<Void> task : tasks) {
            futures.add(executorService.submit(task));
        }

        startLatch.countDown();

        for (Future<Void> future : futures) {
            future.get();
        }

        executorService.shutdown();

        AccountBalanceResponse firstFinalBalance = accountService.getBalance(firstAccount.accountId());
        AccountBalanceResponse secondFinalBalance = accountService.getBalance(secondAccount.accountId());

        BigDecimal totalFunds = firstFinalBalance.balance().add(secondFinalBalance.balance());

        assertThat(firstFinalBalance.balance()).isEqualByComparingTo("1000.00");
        assertThat(secondFinalBalance.balance()).isEqualByComparingTo("1000.00");
        assertThat(totalFunds).isEqualByComparingTo("2000.00");
    }
}
