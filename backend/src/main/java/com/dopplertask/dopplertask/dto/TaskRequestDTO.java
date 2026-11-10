package com.dopplertask.dopplertask.dto;

import com.dopplertask.dopplertask.domain.Preview;
import java.util.Map;

public class TaskRequestDTO {

    private String taskName;
    private Map<String, String> parameters;
    private TaskCreationDTO task;
    private Long resumeFromExecutionId;
    // Set to run the task as a preview, which shows what would be sent and sends none of it.
    private Preview preview;

    public Preview getPreview() {
        return preview;
    }

    public void setPreview(Preview preview) {
        this.preview = preview;
    }

    public Long getResumeFromExecutionId() {
        return resumeFromExecutionId;
    }

    public void setResumeFromExecutionId(Long resumeFromExecutionId) {
        this.resumeFromExecutionId = resumeFromExecutionId;
    }

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
