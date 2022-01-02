package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.OutputType;

/**
 * This class represents the result that will be sent over the messaging queue for other clients to consume.
 */
public class BroadcastResult {
    private String output;
    private OutputType outputType;

    public BroadcastResult(String output, OutputType outputType) {
        this.output = output;
        this.outputType = outputType;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public OutputType getOutputType() {
        return outputType;
    }

    public void setOutputType(OutputType outputType) {
        this.outputType = outputType;
    }
}
