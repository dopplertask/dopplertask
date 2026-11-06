package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.service.CredentialService;
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

    public record NewCredential(String name, String stepType, Map<String, String> values) {
    }

    private final CredentialService credentials;

    public CredentialController(CredentialService credentials) {
        this.credentials = credentials;
    }

    @GetMapping("/credentials")
    public List<CredentialSummary> list() {
        return credentials.list();
    }

    @PostMapping("/credentials")
    public CredentialSummary create(@RequestBody NewCredential credential) {
        return credentials.create(credential.name(), credential.stepType(), credential.values() == null ? Map.of() : credential.values());
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
