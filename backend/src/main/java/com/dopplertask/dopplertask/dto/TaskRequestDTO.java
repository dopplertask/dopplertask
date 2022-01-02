package com.dopplertask.dopplertask.dto;

import java.util.Map;

public class TaskRequestDTO {

    private String taskName;
    private Map<String, String> parameters;
    private TaskCreationDTO task;

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public TaskCreationDTO getTask() {
        return task;
    }

    public void setTask(TaskCreationDTO task) {
        this.task = task;
    }
}
