package com.dopplertask.dopplertask.dto;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.dopplertask.dopplertask.domain.Connection;
import com.dopplertask.dopplertask.domain.TaskParameter;
import com.dopplertask.dopplertask.domain.action.Action;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class TaskCreationDTO {
    // A saved or downloaded task can leave out a yes or no setting, which then means no.
    private static final ObjectMapper MAPPER = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();

    public static TaskCreationDTO fromJson(String json) {
        return MAPPER.readValue(json, TaskCreationDTO.class);
    }

    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }

    private String name;
    private boolean active;
    private List<TaskParameter> parameters;
    private List<Action> actions;
    private String description;
    private List<Connection> connections;

    @JsonCreator
    public TaskCreationDTO(@JsonProperty(value = "name", required = true) String name, @JsonProperty(value = "connections", required = true) List<Connection> connections, @JsonProperty(value = "parameters") List<TaskParameter> parameters, @JsonProperty(value = "actions", required = true) List<Action> actions, @JsonProperty(value = "description") String description, @JsonProperty(value = "active") boolean active) {
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
