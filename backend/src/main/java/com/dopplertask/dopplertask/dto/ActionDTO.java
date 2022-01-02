package com.dopplertask.dopplertask.dto;

import java.util.Map;

public class ActionDTO {

    private Map<String, Object> fields;
    private String actionType;

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }
}
