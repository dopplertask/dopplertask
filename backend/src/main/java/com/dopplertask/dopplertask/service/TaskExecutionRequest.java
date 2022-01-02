package com.dopplertask.dopplertask.service;

public class TaskExecutionRequest extends TaskRequest {

    private Long executionId;

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }
}
