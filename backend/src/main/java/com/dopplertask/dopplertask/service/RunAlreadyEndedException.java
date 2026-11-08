package com.dopplertask.dopplertask.service;

public class RunAlreadyEndedException extends RuntimeException {
    public RunAlreadyEndedException() {
        super("This run has already ended.");
    }
}
