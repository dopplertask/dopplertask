package com.dopplertask.dopplertask.domain;


import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.StartAction;
import com.dopplertask.dopplertask.domain.action.trigger.Trigger;
import com.dopplertask.dopplertask.domain.action.trigger.Webhook;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
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

    @Column(columnDefinition = "boolean default false")
    private boolean autoSaved = false;

    // Saved by an agent and waiting for a person, so runs, webhooks and schedules keep using the version before it.
    @Column(columnDefinition = "boolean default false")
    private boolean proposed = false;

    // Stays true after approval, as a record of where the version came from.
    @Column(columnDefinition = "boolean default false")
    private boolean madeByAgent = false;

    // Who decided on a proposal, as far as the server knows. Empty with a shared token.
    @Column
    private String approvedBy;

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

    public Action getWebhookStartAction() {
        List<Action> actions = actionList.stream().filter(StartAction.class::isInstance).collect(Collectors.toList());
        if (actions.isEmpty()) {
            return null;
        }
        return actions.get(0);
    }

    public Action getActionByPort(String portId) {
        return actionList.stream()
                .filter(action -> action.getPorts() != null && !action.getPorts().isEmpty() && portId.equals(action.getPorts().get(0).getExternalId()))
                .findFirst().orElse(null);
    }

    public Action getStartAction(String triggerName, String triggerSuffix) {
        List<Action> actions = actionList.stream().filter(action -> action.getStepTypeName().equalsIgnoreCase(triggerName) && action instanceof Trigger && ((Trigger) action).getTriggerSuffix().equals(triggerSuffix == null ? "" : triggerSuffix)).collect(Collectors.toList());
        if (actions.isEmpty()) {
            return null;
        }
        return actions.get(0);
    }

    public Action getWebhookStartAction(String triggerName, String triggerSuffix, String path, String method) {
        List<Action> actions = actionList.stream().filter(action -> action.getClass().getSimpleName().equalsIgnoreCase(triggerName) && action instanceof Webhook && ((Webhook) action).getPath().equals(path == null ? "" : path) && ((Webhook) action).getTriggerSuffix().equals(triggerSuffix == null ? "" : triggerSuffix) && ((Webhook) action).getMethod().equals(method == null ? "" : method)).collect(Collectors.toList());
        if (actions.isEmpty()) {
            return null;
        }
        return actions.get(0);
    }

    public List<Trigger> getTriggerActions() {
        List<Trigger> triggers = actionList.stream().filter(action -> action instanceof Trigger && !(action instanceof Webhook)).map(Trigger.class::cast).collect(Collectors.toList());
        if (triggers.isEmpty()) {
            return Collections.emptyList();
        }
        return triggers;
    }

    public List<Connection> getConnections() {
        return connections;
    }

    public void setConnections(List<Connection> connections) {
        this.connections = connections;
    }

    public boolean isProposed() {
        return proposed;
    }

    public void setProposed(boolean proposed) {
        this.proposed = proposed;
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public boolean isMadeByAgent() {
        return madeByAgent;
    }

    public void setMadeByAgent(boolean madeByAgent) {
        this.madeByAgent = madeByAgent;
    }

    public boolean isAutoSaved() {
        return autoSaved;
    }

    public void setAutoSaved(boolean autoSaved) {
        this.autoSaved = autoSaved;
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
