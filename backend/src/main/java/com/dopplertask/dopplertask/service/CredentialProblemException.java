package com.dopplertask.dopplertask.service;

/**
 * Something about a credential that the person or agent asking can correct.
 */
public class CredentialProblemException extends RuntimeException {
    public CredentialProblemException(String message) {
        super(message);
    }
}
