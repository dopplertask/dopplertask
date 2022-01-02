package com.dopplertask.dopplertask.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Linked task could not be found.")
public class LinkedTaskNotFoundException extends RuntimeException {
    public LinkedTaskNotFoundException(String s) {
        super(s);
    }
}
