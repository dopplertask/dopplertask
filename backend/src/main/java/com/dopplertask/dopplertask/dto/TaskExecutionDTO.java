package com.dopplertask.dopplertask.dto;

import java.util.Date;

public class TaskExecutionDTO {
    private boolean startedByAgent;
    private boolean preview;

    public boolean isPreview() {
        return preview;
    }

    public void setPreview(boolean preview) {
        this.preview = preview;
    }
    private String executionId;
    private String taskName;
    private String status;
    private Date startDate;
    private Date endDate;

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(String executionId) {
        this.executionId = executionId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public boolean isStartedByAgent() {
        return startedByAgent;
    }

    public void setStartedByAgent(boolean startedByAgent) {
        this.startedByAgent = startedByAgent;
    }
}
