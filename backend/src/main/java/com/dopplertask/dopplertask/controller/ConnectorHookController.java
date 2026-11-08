package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.action.connector.ConnectorWebhooks;
import com.dopplertask.dopplertask.domain.action.integration.JsonApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where services send the events a connector trigger subscribed to. The random key in the address finds the trigger,
 * and the event is only handed over as data: the trigger's task starts with it as its new item.
 */
@RestController
public class ConnectorHookController {

    public ConnectorHookController(@Value("${dopplertask.publicUrl:}") String publicUrl) {
        ConnectorWebhooks.INSTANCE.setPublicUrl(publicUrl);
    }

    @PostMapping(ConnectorWebhooks.PATH + "{key}")
    public ResponseEntity<Void> event(@PathVariable String key, @RequestBody(required = false) String body) {
        var event = body == null || body.isBlank() ? JsonApi.INSTANCE.getMapper().createObjectNode() : JsonApi.INSTANCE.getMapper().readTree(body);
        return ConnectorWebhooks.INSTANCE.deliver(key, event) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}
