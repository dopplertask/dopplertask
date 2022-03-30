package com.dopplertask.dopplertask.domain;

import com.dopplertask.dopplertask.domain.action.Action;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "TaskExecution")
public class TaskExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @OneToMany(cascade = CascadeType.ALL)
    @JoinTable(name = "execution_parameter_mapping", joinColumns = @JoinColumn(name = "execution_id", referencedColumnName = "id"), inverseJoinColumns = @JoinColumn(name = "parameter_id", referencedColumnName = "id"))
    @MapKey(name = "paramName")
    private Map<String, ExecutionParameter> parameters = new HashMap<>();

    @ManyToOne
    @JoinColumn
    private Task task;

    @OneToMany(mappedBy = "taskExecution", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private List<TaskExecutionLog> logs = new ArrayList<>();

    private Date startdate;
    private Date enddate;

    // Useful for linked tasks to know how deep in the linking it has reached to avoid stack overflow.
    private Integer depth = 0;

    @Enumerated(EnumType.STRING)
    private TaskExecutionStatus status = TaskExecutionStatus.CREATED;

    @Transient
    private Action currentAction;

    @Transient
    private Map<Long, Integer> actionAccessCountMap = new HashMap<>();

    private boolean success = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Map<String, ExecutionParameter> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, ExecutionParameter> parameters) {
        this.parameters = parameters;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }

    public Date getStartdate() {
        return startdate;
    }

    public void setStartdate(Date startdate) {
        this.startdate = startdate;
    }

    public Date getEnddate() {
        return enddate;
    }

    public void setEnddate(Date enddate) {
        this.enddate = enddate;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public void addLog(TaskExecutionLog executionLog) {
        logs.add(executionLog);
    }

    public List<TaskExecutionLog> getLogs() {
        return logs;
    }

    public TaskExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(TaskExecutionStatus status) {
        this.status = status;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Action getCurrentAction() {
        return currentAction;
    }

    public void setCurrentAction(Action currentAction) {
        this.currentAction = currentAction;
    }

    public int getActionAccessCount(Long actionId) {
        Integer count = this.actionAccessCountMap.get(actionId);
        if (count == null) {
            return 0;
        } else {
            return count;
        }
    }

    /**
     * Add access count to an action.
     *
     * @param actionId
     */
    public void addActionAccessCountByOne(Long actionId) {
        Integer count = this.actionAccessCountMap.get(actionId);
        if (count == null) {
            this.actionAccessCountMap.put(actionId, 1);
        } else {
            this.actionAccessCountMap.put(actionId, count + 1);
        }
    }
}
