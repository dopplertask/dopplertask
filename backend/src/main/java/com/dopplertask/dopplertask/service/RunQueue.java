package com.dopplertask.dopplertask.service;

/** Where queued runs wait. An install uses its built-in queue unless an add-on supplies another. */
public interface RunQueue {
    void submit(TaskExecutionRequest request);
}
