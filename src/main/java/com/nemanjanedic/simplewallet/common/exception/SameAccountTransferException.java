package com.nemanjanedic.simplewallet.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SameAccountTransferException extends RuntimeException {

    public SameAccountTransferException() {
        super("Cannot transfer funds to the same account");
    }
}
