package com.dopplertask.dopplertask.dto;

import java.util.ArrayList;
import java.util.List;

public class TaskExecutionLogResponseDTO {

    private List<String> output;

    public TaskExecutionLogResponseDTO() {
        output = new ArrayList<>();
    }

    public List<String> getOutput() {
        return output;
    }

    public void setOutput(List<String> output) {
        this.output = output;
    }
}
