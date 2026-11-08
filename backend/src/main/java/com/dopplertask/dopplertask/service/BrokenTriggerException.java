package com.dopplertask.dopplertask.service;

/** A task is turned on with a trigger that cannot start it, such as a cron line or time zone that does not exist. */
public class BrokenTriggerException extends RuntimeException {
    public BrokenTriggerException(String message) {
        super(message);
    }
}
