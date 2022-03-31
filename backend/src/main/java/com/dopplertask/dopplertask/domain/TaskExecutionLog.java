package com.dopplertask.dopplertask.domain;

import javax.persistence.*;
import java.util.Date;
import java.util.Map;

@Entity
@Table(name = "TaskExecutionLog")
public class TaskExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn
    private TaskExecution taskExecution;

    @Lob
    private String output;

    @Enumerated(EnumType.STRING)
    @Column
    private OutputType outputType = OutputType.STRING;

    @Transient
    private Map<String, Object> outputVariables;

    private Date date;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public TaskExecution getTaskExecution() {
        return taskExecution;
    }

    public void setTaskExecution(TaskExecution taskExecution) {
        this.taskExecution = taskExecution;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public void setOutputType(OutputType outputType) {
        this.outputType = outputType;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public Map<String, Object> getOutputVariables() {
        return outputVariables;
    }

    public void setOutputVariables(Map<String, Object> outputVariables) {
        this.outputVariables = outputVariables;
    }
}
