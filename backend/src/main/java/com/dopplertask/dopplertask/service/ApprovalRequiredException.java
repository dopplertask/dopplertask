package com.dopplertask.dopplertask.service;

/**
 * An agent asked for something that needs a person first.
 */
public class ApprovalRequiredException extends RuntimeException {
    public ApprovalRequiredException(String message) {
        super(message);
    }
}
