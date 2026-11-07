package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.service.CredentialProblemException;
import com.dopplertask.dopplertask.service.CredentialService;
import com.dopplertask.dopplertask.service.OAuthConnections;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;
import com.dopplertask.dopplertask.service.CredentialService.CredentialSummary;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Values go in here and never come back out, so there is no way to read a credential.
 */
@RestController
public class CredentialController {

    public record NewCredential(String name, String type, Map<String, String> values) {
    }

    private final CredentialService credentials;

    private final OAuthConnections connections;

    public CredentialController(CredentialService credentials, OAuthConnections connections) {
        this.credentials = credentials;
        this.connections = connections;
    }

    /** Where to send the person to connect the account of this credential. */
    @PostMapping("/credentials/{name}/connect")
    public Map<String, String> connect(@PathVariable String name, HttpServletRequest request) {
        return Map.of("url", connections.start(name, serverAddress(request)));
    }

    /** The service sends the person back here. It is open, since the browser comes from the service, and the state proves the rest. */
    @GetMapping(value = OAuthConnections.CALLBACK_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> connected(@RequestParam(required = false) String state, @RequestParam(required = false) String code, @RequestParam(required = false) String error) {
        try {
            return ResponseEntity.ok(page(connections.finish(state, code, error), true));
        } catch (CredentialProblemException e) {
            return ResponseEntity.badRequest().body(page(e.getMessage(), false));
        }
    }

    private static String serverAddress(HttpServletRequest request) {
        return request.getScheme() + "://" + request.getHeader("Host");
    }

    private static String page(String message, boolean connected) {
        String text = HtmlUtils.htmlEscape(message);
        String notice = connected ? "<script>if (window.opener) { window.opener.postMessage('dopplertask-account-connected', window.location.origin); }</script>" : "";
        return "<!doctype html><meta charset=\"utf-8\"><title>DopplerTask</title><body style=\"font:16px system-ui;margin:48px;color:#0f1e2e\"><p>" + text + "</p>" + notice + "</body>";
    }

    @GetMapping("/credentials")
    public List<CredentialSummary> list() {
        return credentials.list();
    }

    @PostMapping("/credentials")
    public CredentialSummary create(@RequestBody NewCredential credential) {
        return credentials.create(credential.name(), credential.type(), credential.values() == null ? Map.of() : credential.values());
    }

    @PutMapping("/credentials/{name}")
    public CredentialSummary update(@PathVariable String name, @RequestBody Map<String, String> values) {
        return credentials.update(name, values);
    }

    @DeleteMapping("/credentials/{name}")
    public ResponseEntity<Void> delete(@PathVariable String name) {
        credentials.delete(name);
        return ResponseEntity.noContent().build();
    }
}
