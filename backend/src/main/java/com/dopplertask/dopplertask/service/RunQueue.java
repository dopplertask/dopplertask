package com.dopplertask.dopplertask.service;

import java.util.Set;

/** Where queued runs wait. An install uses its built-in queue unless an add-on supplies another. */
public interface RunQueue {
    void submit(TaskExecutionRequest request);

    /** The runs of the current customer scope that wait here and still start after a restart. */
    Set<Long> waiting();
}
