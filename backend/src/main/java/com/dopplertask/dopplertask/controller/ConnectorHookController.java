package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.action.connector.ConnectorWebhooks;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Where services send the events of connector triggers. The random key in the address finds the trigger, and the event
 * is only handed over as data: the trigger's task starts with it as its new item. A service may first check the address,
 * with a request without a body or with a challenge that the trigger's reply answers.
 */
@RestController
public class ConnectorHookController {

    public ConnectorHookController(@Value("${dopplertask.publicUrl:}") String publicUrl) {
        ConnectorWebhooks.INSTANCE.setPublicUrl(publicUrl);
    }

    @RequestMapping(ConnectorWebhooks.PATH + "{key}")
    public ResponseEntity<Object> event(@PathVariable String key, HttpServletRequest request) throws IOException {
        String body = bodyOf(request);
        if (body.isEmpty() && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()))) {
            return ConnectorWebhooks.INSTANCE.isListening(key) ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
        }
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name.toLowerCase(), request.getHeader(name)));
        Map<String, String> query = UriComponentsBuilder.newInstance().query(request.getQueryString()).build().getQueryParams().toSingleValueMap();
        var reply = ConnectorWebhooks.INSTANCE.deliver(key, ConnectorWebhooks.INSTANCE.eventOf(request.getContentType(), body, headers, query));
        if (reply == null) {
            return ResponseEntity.notFound().build();
        }
        return reply.isNull() ? ResponseEntity.ok().build() : ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(reply.toString());
    }

    /** The body as it was sent. A form that the server already read is written back from its fields. */
    private static String bodyOf(HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!body.isEmpty() || !String.valueOf(request.getContentType()).startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
            return body;
        }
        return request.getParameterMap().entrySet().stream()
                .flatMap(field -> java.util.Arrays.stream(field.getValue()).map(value -> encoded(field.getKey()) + "=" + encoded(value)))
                .collect(Collectors.joining("&"));
    }

    private static String encoded(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
