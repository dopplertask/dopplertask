package com.dopplertask.dopplertask.filter;

/**
 * Who is making a request. An agent can propose work, and a person decides what runs.
 */
public enum Caller {
    PERSON, AGENT;

    static final String REQUEST_ATTRIBUTE = "dopplertask.caller";
}
