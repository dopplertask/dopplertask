package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.action.connector.ConnectorWebhooks;
import com.dopplertask.dopplertask.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where services send the events of connector triggers. The random key in the address finds the trigger, and the event
 * is only handed over as data: the trigger's task starts with it as its new item. A service may first check the address,
 * with a request without a body or with a challenge, which the trigger's reply answers. A run that waits, such as for an
 * approval, continues when its own address is called once.
 */
@RestController
public class ConnectorHookController {

    private final TaskService taskService;

    public ConnectorHookController(@Value("${dopplertask.publicUrl:}") String publicUrl, TaskService taskService) {
        ConnectorWebhooks.INSTANCE.setPublicUrl(publicUrl);
        this.taskService = taskService;
    }

    /** The address a waiting run gives, such as in an approval button. Any call is the answer, also a link opened without a body. */
    @RequestMapping(ConnectorWebhooks.PATH + ConnectorWebhooks.RESUME + "{key}")
    public ResponseEntity<String> resume(@PathVariable String key, HttpServletRequest request) throws IOException {
        return taskService.resumeWaiting(key, ConnectorWebhooks.INSTANCE.storedOf(eventOf(request))) == null ? ResponseEntity.notFound().build()
                : ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("Thank you. The run continues.");
    }

    /**
     * The page an approval email links to. Opening it only shows Approve and Disapprove, so that a mail scanner that opens
     * every link answers nothing. A button sends the answer, with action=approve or action=disapprove in the query.
     */
    @RequestMapping(ConnectorWebhooks.PATH + ConnectorWebhooks.RESUME + "{key}/confirm")
    public ResponseEntity<String> confirm(@PathVariable String key, HttpServletRequest request) throws IOException {
        if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())) {
            if (!taskService.isWaiting(key)) {
                return page(HttpStatus.NOT_FOUND, "This request has already been answered.", "");
            }
            return page(HttpStatus.OK, "Approve or disapprove this request.",
                    button(request, "approve", "Approve") + button(request, "disapprove", "Disapprove"));
        }
        return taskService.resumeWaiting(key, ConnectorWebhooks.INSTANCE.storedOf(eventOf(request))) == null
                ? page(HttpStatus.NOT_FOUND, "This request has already been answered.", "")
                : page(HttpStatus.OK, "Thank you. Your answer is recorded.", "");
    }

    /** A button that sends the answer to the same page, with the page's query and the action. */
    private static String button(HttpServletRequest request, String action, String label) {
        String query = request.getQueryString() == null ? "" : java.util.Arrays.stream(request.getQueryString().split("&"))
                .filter(pair -> !pair.isEmpty() && !pair.startsWith("action=")).map(pair -> pair + "&").collect(java.util.stream.Collectors.joining());
        return "<form method=\"post\" action=\"confirm?" + HtmlUtils.htmlEscape(query + "action=" + action) + "\"><button>" + label + "</button></form>";
    }

    private static ResponseEntity<String> page(HttpStatus status, String message, String buttons) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).body("<!doctype html><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\">"
                + "<title>DopplerTask</title><style>body{font:16px system-ui,sans-serif;margin:15vh auto;max-width:28rem;padding:0 1rem}form{display:inline}"
                + "button{font:inherit;padding:.5rem 1.2rem;margin:0 .5rem .5rem 0;cursor:pointer}</style><p>" + message + "</p>" + buttons);
    }

    /** The one address of a connector's app, such as a Slack app's Request URL, for the events of all its triggers. */
    @RequestMapping(ConnectorWebhooks.PATH + "app/{connector}")
    public ResponseEntity<String> appEvent(@PathVariable String connector, HttpServletRequest request) throws IOException {
        return event(ConnectorWebhooks.INSTANCE.appKey(connector), request);
    }

    @RequestMapping(ConnectorWebhooks.PATH + "{key}")
    public ResponseEntity<String> event(@PathVariable String key, HttpServletRequest request) throws IOException {
        var event = eventOf(request);
        boolean isCheck = event.getReceived().path("text").asText().isEmpty() && ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod()));
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

    private static ConnectorWebhooks.Event eventOf(HttpServletRequest request) throws IOException {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> headers.put(name.toLowerCase(), request.getHeader(name)));
        return ConnectorWebhooks.INSTANCE.eventOf(request.getContentType(), body, headers, request.getQueryString());
    }
}
