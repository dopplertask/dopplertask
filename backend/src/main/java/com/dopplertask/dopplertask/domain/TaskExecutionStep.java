package com.dopplertask.dopplertask.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * What one action did in a run. The action is identified by the external id of its first port, since that is the id clients know it by.
 */
@Entity
@Table(name = "TaskExecutionStep")
public class TaskExecutionStep {
    private static final ObjectMapper MAPPER = new ObjectMapper();


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

    @Lob
    private String outputVariables;

    @Column
    private long durationMs;

    // In a preview: what the step would have sent or done.
    @Lob
    private String held;

    // Values the step's settings asked for that nothing gave, as a JSON list.
    @Lob
    private String missingValues;

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
        this.held = result.getHeld();
        this.missingValues = result.getMissingValues().isEmpty() ? null : MAPPER.writeValueAsString(result.getMissingValues());
    }

    /**
     * Keeps only what the step declares, since an HTTP step also gives every response header, such as cookies.
     */
    public void keepOutputs(Collection<String> declared, Map<String, Object> given) {
        Map<String, Object> kept = new HashMap<>(given);
        kept.keySet().retainAll(declared);
        try {
            outputVariables = MAPPER.writeValueAsString(kept);
        } catch (JacksonException e) {
            throw new IllegalStateException("The outputs of a step could not be written.", e);
        }
    }

    public Map<String, Object> getOutputVariables() {
        if (outputVariables == null) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(outputVariables, new TypeReference<>() {
            });
        } catch (JacksonException e) {
            throw new IllegalStateException("The outputs of a step could not be read.", e);
        }
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
        copy.outputVariables = outputVariables;
        copy.durationMs = durationMs;
        copy.held = held;
        copy.missingValues = missingValues;
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

    public String getHeld() {
        return held;
    }

    public List<String> getMissingValues() {
        return missingValues == null ? List.of() : MAPPER.readValue(missingValues, new TypeReference<>() {
        });
    }
}
