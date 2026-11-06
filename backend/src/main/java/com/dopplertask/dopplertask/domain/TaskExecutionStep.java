package com.dopplertask.dopplertask.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * What one action did in a run. The action is identified by the external id of its first port, since that is the id clients know it by.
 */
@Entity
@Table(name = "TaskExecutionStep")
public class TaskExecutionStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @ManyToOne
    @JoinColumn
    @JsonIgnore
    private TaskExecution taskExecution;

    @Column
    private String portId;

    @Column
    private String actionType;

    @Enumerated(EnumType.STRING)
    @Column
    private StatusCode status;

    @Lob
    private String output;

    @Lob
    private String error;

    @Column
    private long durationMs;

    public TaskExecutionStep() {
    }

    public TaskExecutionStep(TaskExecution taskExecution, String portId, String actionType, ActionResult result, long durationMs) {
        this.taskExecution = taskExecution;
        this.portId = portId;
        this.actionType = actionType;
        this.status = result.getStatusCode();
        this.output = result.getOutput();
        this.error = result.getErrorMsg();
        this.durationMs = durationMs;
    }

    /**
     * The same result, for a resumed run that did not repeat this step.
     */
    public TaskExecutionStep copyTo(TaskExecution execution) {
        TaskExecutionStep copy = new TaskExecutionStep();
        copy.taskExecution = execution;
        copy.portId = portId;
        copy.actionType = actionType;
        copy.status = status;
        copy.output = output;
        copy.error = error;
        copy.durationMs = durationMs;
        return copy;
    }

    public String getPortId() {
        return portId;
    }

    public String getActionType() {
        return actionType;
    }

    public StatusCode getStatus() {
        return status;
    }

    public String getOutput() {
        return output;
    }

    public String getError() {
        return error;
    }

    public long getDurationMs() {
        return durationMs;
    }
}
