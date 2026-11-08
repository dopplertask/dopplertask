package com.dopplertask.dopplertask.service;

import java.util.Map;

public class WebhookTriggerInfo extends TriggerInfo {
    private String path;
    private HttpMethod method;
    // Who waits for the answer, when the webhook does not simply answer with the run log.
    private WebhookReply reply;

    public WebhookTriggerInfo(String triggerName, String triggerPath, String triggerSuffix, Map<String, String> triggerParameters, HttpMethod method) {
        super(triggerName, triggerSuffix, triggerParameters);
        this.path = triggerPath;
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public HttpMethod getMethod() {
        return method;
    }

    public void setMethod(HttpMethod method) {
        this.method = method;
    }

    public WebhookReply getReply() {
        return reply;
    }

    public void setReply(WebhookReply reply) {
        this.reply = reply;
    }
}
