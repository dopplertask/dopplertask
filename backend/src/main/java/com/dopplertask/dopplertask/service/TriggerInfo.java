package com.dopplertask.dopplertask.service;

import java.util.Map;

/**
 * This class represents the trigger info in a task request
 */
public class TriggerInfo {
    private String triggerName;
    private String triggerPath;
    private String triggerSuffix;
    private Map<String, String> triggerParameters;

    public TriggerInfo(String triggerName, String triggerPath, String triggerSuffix, Map<String, String> triggerParameters) {
        this.triggerName = triggerName;
        this.triggerPath = triggerPath;
        this.triggerSuffix = triggerSuffix;
        this.triggerParameters = triggerParameters;
    }

    public String getTriggerName() {
        return triggerName;
    }

    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
    }

    public String getTriggerPath() {
        return triggerPath;
    }

    public void setTriggerPath(String triggerPath) {
        this.triggerPath = triggerPath;
    }

    public Map<String, String> getTriggerParameters() {
        return triggerParameters;
    }

    public void setTriggerParameters(Map<String, String> triggerParameters) {
        this.triggerParameters = triggerParameters;
    }

    public String getTriggerSuffix() {
        return triggerSuffix;
    }

    public void setTriggerSuffix(String triggerSuffix) {
        this.triggerSuffix = triggerSuffix;
    }
}
