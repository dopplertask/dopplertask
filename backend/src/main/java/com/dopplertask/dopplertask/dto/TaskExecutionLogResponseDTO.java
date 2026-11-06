package com.dopplertask.dopplertask.dto;

import com.dopplertask.dopplertask.domain.TaskExecutionStep;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TaskExecutionLogResponseDTO {

    private List<String> output;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String status;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Long executionId;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> logs = new ArrayList<>();
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<TaskExecutionStep> steps = new ArrayList<>();
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, String> parameters = new HashMap<>();
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Date startDate;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Date endDate;

    public TaskExecutionLogResponseDTO() {
        output = new ArrayList<>();
    }

    public List<String> getOutput() {
        return output;
    }

    public void setOutput(List<String> output) {
        this.output = output;
    }

    public List<TaskExecutionStep> getSteps() {
        return steps;
    }

    public void setSteps(List<TaskExecutionStep> steps) {
        this.steps = steps;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
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

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
