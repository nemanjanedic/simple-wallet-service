package com.nemanjanedic.simplewallet.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(Long accountId) {
        super("Account does not have enough funds: " + accountId);
    }
}
