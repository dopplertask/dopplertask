package com.dopplertask.dopplertask.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Task could not be uploaded. Check that you've logged in, or that you have an internet connection.")
public class UploadNotSuccessfulException extends RuntimeException {
    public UploadNotSuccessfulException(String message) {
        super(message);
    }
}
