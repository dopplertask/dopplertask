package com.dopplertask.dopplertask.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "Could not build successfully.")
public class BuildNotSuccessfulException extends RuntimeException {
    public BuildNotSuccessfulException(String s) {
        super(s);
    }
}
