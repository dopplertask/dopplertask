package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.ExecutionParameter;

import java.util.Map;

public class TaskRequest {
    private String taskName;
    private String checksum;
    private Map<String, ExecutionParameter> parameters;
    private int depth = 0;
    private boolean removeTaskAfterExecution;

    // If defined then automation starts from a trigger
    private TriggerInfo triggerInfo;
    private Long resumeFromExecutionId;
    // What arrived at the address of the step a resumed run waited at, which that step reads.
    private String resumeWith;
    private boolean startedByAgent;

    public TaskRequest(String taskName, Map<String, ExecutionParameter> parameters) {
        this(taskName, parameters, false);
    }

    public TaskRequest() {
        this.removeTaskAfterExecution = false;
    }

    public TaskRequest(String taskName, Map<String, ExecutionParameter> parameters, boolean removeTaskAfterExecution) {
        this.taskName = taskName;
        this.parameters = parameters;
        this.removeTaskAfterExecution = removeTaskAfterExecution;
    }

    public boolean isStartedByAgent() {
        return startedByAgent;
    }

    public void setStartedByAgent(boolean startedByAgent) {
        this.startedByAgent = startedByAgent;
    }

    public Long getResumeFromExecutionId() {
        return resumeFromExecutionId;
    }

    public void setResumeFromExecutionId(Long resumeFromExecutionId) {
        this.resumeFromExecutionId = resumeFromExecutionId;
    }

    public String getResumeWith() {
        return resumeWith;
    }

    public void setResumeWith(String resumeWith) {
        this.resumeWith = resumeWith;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public Map<String, ExecutionParameter> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, ExecutionParameter> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "TaskRequest{" +
                "taskName=" + taskName +
                ", parameters=" + parameters +
                '}';
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public boolean isRemoveTaskAfterExecution() {
        return removeTaskAfterExecution;
    }

    public void setRemoveTaskAfterExecution(boolean removeTaskAfterExecution) {
        this.removeTaskAfterExecution = removeTaskAfterExecution;
    }


    public TriggerInfo getTriggerInfo() {
        return triggerInfo;
    }

    public void setTriggerInfo(TriggerInfo triggerInfo) {
        this.triggerInfo = triggerInfo;
    }
}
