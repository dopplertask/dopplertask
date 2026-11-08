package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.action.connector.ConnectorWebhooks;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where services send the events of connector triggers. The random key in the address finds the trigger, and the event
 * is only handed over as data: the trigger's task starts with it as its new item. A service may first check the address,
 * with a request without a body or with a challenge, which the trigger's reply answers.
 */
@RestController
public class ConnectorHookController {

    public ConnectorHookController(@Value("${dopplertask.publicUrl:}") String publicUrl) {
        ConnectorWebhooks.INSTANCE.setPublicUrl(publicUrl);
    }

    @RequestMapping(ConnectorWebhooks.PATH + "{key}")
    public ResponseEntity<String> event(@PathVariable String key, HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name.toLowerCase(), request.getHeader(name)));
        var event = ConnectorWebhooks.INSTANCE.eventOf(request.getContentType(), body, headers, request.getQueryString());
        boolean isCheck = body.isEmpty() && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()));
        JsonNode reply = isCheck ? ConnectorWebhooks.INSTANCE.check(key, event) : ConnectorWebhooks.INSTANCE.deliver(key, event);
        if (reply == null) {
            return ResponseEntity.notFound().build();
        }
        if (reply.isNull()) {
            return ResponseEntity.ok().build();
        }
        // A reply in text, such as a token the service checks, goes back as that text.
        return reply.isTextual() ? ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(reply.asText())
                : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(reply.toString());
    }
}
