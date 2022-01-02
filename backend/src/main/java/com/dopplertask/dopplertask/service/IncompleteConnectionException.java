package com.dopplertask.dopplertask.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class IncompleteConnectionException extends RuntimeException {
    public IncompleteConnectionException(String s) {
        super(s);
    }
}
