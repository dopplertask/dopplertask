package com.dopplertask.dopplertask.dto;

import com.dopplertask.dopplertask.domain.action.Action;

import java.util.HashMap;
import java.util.Map;

public class ActionTestRequestDTO {
    private Action action;
    private Map<String, String> parameters = new HashMap<>();
    private String lastOutput = "";
    private Map<String, Object> lastOutputVariables = new HashMap<>();

    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public String getLastOutput() {
        return lastOutput;
    }

    public Map<String, Object> getLastOutputVariables() {
        return lastOutputVariables;
    }

    public void setLastOutputVariables(Map<String, Object> lastOutputVariables) {
        this.lastOutputVariables = lastOutputVariables;
    }

    public void setLastOutput(String lastOutput) {
        this.lastOutput = lastOutput;
    }
}
