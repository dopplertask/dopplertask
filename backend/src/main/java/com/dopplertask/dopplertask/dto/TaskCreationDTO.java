package com.dopplertask.dopplertask.dto;

import com.dopplertask.dopplertask.domain.Connection;
import com.dopplertask.dopplertask.domain.TaskParameter;
import com.dopplertask.dopplertask.domain.action.Action;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TaskCreationDTO {
    private String name;
    private boolean active;
    private List<TaskParameter> parameters;
    private List<Action> actions;
    private String description;
    private List<Connection> connections;

    @JsonCreator
    public TaskCreationDTO(@JsonProperty(value = "name", required = true) String name, @JsonProperty(value = "connections", required = true) List<Connection> connections, @JsonProperty(value = "parameters") List<TaskParameter> parameters, @JsonProperty(value = "actions", required = true) List<Action> actions, @JsonProperty(value = "description", required = true) String description, @JsonProperty(value = "active", required = true) boolean active) {
        this.name = name;
        this.parameters = parameters;
        this.actions = actions;
        this.description = description;
        this.connections = connections;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<TaskParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<TaskParameter> parameters) {
        this.parameters = parameters;
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
}
