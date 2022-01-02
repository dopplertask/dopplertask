package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.OutputType;

public interface BroadcastListener {
    void run(String output, OutputType outputType);
}
