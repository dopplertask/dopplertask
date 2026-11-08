package com.dopplertask.dopplertask.service;

import java.util.Map;

/**
 * This class represents the trigger info in a task request
 */
public class TriggerInfo {
    private String triggerName;
    private String triggerSuffix;
    private Map<String, String> triggerParameters;
    private Map<String, byte[]> files = Map.of();

    public TriggerInfo(String triggerName, String triggerSuffix, Map<String, String> triggerParameters) {
        this.triggerName = triggerName;
        this.triggerSuffix = triggerSuffix;
        this.triggerParameters = triggerParameters;
    }

    /** The files the trigger found, such as the attachments of a new email, which the run keeps as its files. */
    public Map<String, byte[]> getFiles() {
        return files;
    }

    public void setFiles(Map<String, byte[]> files) {
        this.files = files;
    }

    public String getTriggerName() {
        return triggerName;
    }

    public void setTriggerName(String triggerName) {
        this.triggerName = triggerName;
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
