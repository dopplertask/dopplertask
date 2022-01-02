package com.dopplertask.dopplertask.domain;


import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.StartAction;
import com.dopplertask.dopplertask.domain.action.trigger.Trigger;
import com.dopplertask.dopplertask.domain.action.trigger.Webhook;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Basic;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.*;
import java.util.stream.Collectors;

@Entity
@Table(name = "Task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column
    private String name;

    @Column
    private String description;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<TaskParameter> taskParameterList = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    @OrderBy("orderPosition ASC")
    private List<Action> actionList = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<TaskExecution> executions = new ArrayList<>();

    @Basic
    @Temporal(TemporalType.TIMESTAMP)
    private Date created;

    @Column(unique = true)
    @JsonIgnore
    private String checksum;

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL)
    private List<Connection> connections;

    private boolean active = false;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<Action> getActionList() {
        return actionList;
    }

    public void setActionList(List<Action> actionList) {
        this.actionList = actionList;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<TaskExecution> getExecutions() {
        return executions;
    }

    public void setExecutions(List<TaskExecution> executions) {
        this.executions = executions;
    }

    public List<TaskParameter> getTaskParameterList() {
        return taskParameterList;
    }

    public void setTaskParameterList(List<TaskParameter> taskParameterList) {
        this.taskParameterList = taskParameterList;
    }

    public Action getStartAction() {
        List<Action> actions = actionList.stream().filter(action -> action instanceof StartAction).collect(Collectors.toList());
        if (actions.isEmpty()) {
            return null;
        }
        return actions.get(0);
    }

    public Action getStartAction(String triggerName, String triggerSuffix, String path) {
        List<Action> actions = actionList.stream().filter(action -> action.getClass().getSimpleName().equalsIgnoreCase(triggerName) && action instanceof Trigger && ((Trigger) action).getPath().equals(path == null ? "" : path) && ((Trigger) action).getTriggerSuffix().equals(triggerSuffix == null ? "" : triggerSuffix)).collect(Collectors.toList());
        if (actions.isEmpty()) {
            return null;
        }
        return actions.get(0);
    }

    public List<Trigger> getTriggerActions() {
        List<Trigger> triggers = actionList.stream().filter(action -> action instanceof Trigger && !(action instanceof Webhook)).map(action -> (Trigger) action).collect(Collectors.toList());
        if (triggers.isEmpty()) {
            return Collections.EMPTY_LIST;
        }
        return triggers;
    }

    public List<Connection> getConnections() {
        return connections;
    }

    public void setConnections(List<Connection> connections) {
        this.connections = connections;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }


    // Two customers are equal if their IDs are equal
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return name.equals(task.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
