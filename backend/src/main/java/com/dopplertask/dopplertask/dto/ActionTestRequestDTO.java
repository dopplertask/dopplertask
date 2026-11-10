package com.dopplertask.dopplertask.dto;

import com.dopplertask.dopplertask.domain.action.Action;

import java.util.HashMap;
import java.util.Map;

public class ActionTestRequestDTO {
    private Action action;
    private Map<String, String> parameters = new HashMap<>();
    private String lastOutput = "";
    private Map<String, Object> lastOutputVariables = new HashMap<>();
    // What the steps before this one gave, by the name of each step, so that the test reads them as a run does.
    private Map<String, EarlierStep> steps = new HashMap<>();
    // Set to hold what the step would send, and show it.
    private boolean preview;
    // The item a loop is at, as JSON, when the step is inside a pass, so that the test reads $loop.item as a run does.
    private String loopItem;
    // Where that item is in the list, from 0, and how many items the list has.
    private int loopIndex;
    private int loopCount = 1;

    public static class EarlierStep {
        private String output = "";
        private Map<String, Object> outputVariables = new HashMap<>();

        public String getOutput() {
            return output;
        }

        public void setOutput(String output) {
            this.output = output;
        }

        public Map<String, Object> getOutputVariables() {
            return outputVariables;
        }

        public void setOutputVariables(Map<String, Object> outputVariables) {
            this.outputVariables = outputVariables;
        }
    }

    public Map<String, EarlierStep> getSteps() {
        return steps;
    }

    public void setSteps(Map<String, EarlierStep> steps) {
        this.steps = steps;
    }

    public boolean isPreview() {
        return preview;
    }

    public void setPreview(boolean preview) {
        this.preview = preview;
    }

    public String getLoopItem() {
        return loopItem;
    }

    public void setLoopItem(String loopItem) {
        this.loopItem = loopItem;
    }

    public int getLoopIndex() {
        return loopIndex;
    }

    public void setLoopIndex(int loopIndex) {
        this.loopIndex = loopIndex;
    }

    public int getLoopCount() {
        return loopCount;
    }

    public void setLoopCount(int loopCount) {
        this.loopCount = loopCount;
    }

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
