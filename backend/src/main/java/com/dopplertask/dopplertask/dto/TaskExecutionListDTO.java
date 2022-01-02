package com.dopplertask.dopplertask.dto;

import java.util.List;

public class TaskExecutionListDTO {
    private List<TaskExecutionDTO> executions;

    public List<TaskExecutionDTO> getExecutions() {
        return executions;
    }

    public void setExecutions(List<TaskExecutionDTO> executions) {
        this.executions = executions;
    }
}
