package com.nemanjanedic.simplewallet.account;

import com.nemanjanedic.simplewallet.account.dto.AccountBalanceResponse;
import com.nemanjanedic.simplewallet.account.dto.AccountResponse;
import com.nemanjanedic.simplewallet.account.dto.BalanceOperationRequest;
import com.nemanjanedic.simplewallet.account.dto.CreateAccountRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody CreateAccountRequest request) {
        return accountService.createAccount(request);
    }

    @GetMapping("/{accountId}/balance")
    public AccountBalanceResponse getBalance(@PathVariable Long accountId) {
        return accountService.getBalance(accountId);
    }

    @PostMapping("/{accountId}/deposit")
    public AccountBalanceResponse deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody BalanceOperationRequest request
    ) {
        return accountService.deposit(accountId, request);
    }

    @PostMapping("/{accountId}/withdraw")
    public AccountBalanceResponse withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody BalanceOperationRequest request
    ) {
        return accountService.withdraw(accountId, request);
    }
}
