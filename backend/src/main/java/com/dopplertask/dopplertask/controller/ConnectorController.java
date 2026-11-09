package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.action.connector.ConnectorAction;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorField;
import com.dopplertask.dopplertask.domain.action.connector.ConnectorOption;
import com.dopplertask.dopplertask.domain.action.connector.Connectors;
import com.dopplertask.dopplertask.service.CredentialProblemException;
import com.dopplertask.dopplertask.service.CredentialService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The choices of a connector's input, such as the audiences of an account, and the fields of a form that follows the
 * service, such as the columns of a table, so that a person picks and fills in rather than looks up ids. The request runs on the server with the credential, which never reaches the browser, and agents
 * cannot ask for it, since it is not one of their requests.
 */
@RestController
public class ConnectorController {

    private final CredentialService credentials;

    public ConnectorController(CredentialService credentials) {
        this.credentials = credentials;
    }

    public record StepLookup(String operation, String input, String credential, Map<String, String> settings) {
    }

    @PostMapping("/connectors/{name}/choices")
    public List<ConnectorOption> choices(@PathVariable String name, @RequestBody StepLookup request) {
        return lookUp(name, request, step -> step.choices(request.input()));
    }

    @PostMapping("/connectors/{name}/fields")
    public List<ConnectorField> fields(@PathVariable String name, @RequestBody StepLookup request) {
        return lookUp(name, request, step -> step.fields(request.input()));
    }

    /** Runs a lookup as the step would, with its settings and its credential, on the server. */
    private <T> T lookUp(String name, StepLookup request, Function<ConnectorAction, T> lookup) {
        if (Connectors.INSTANCE.find(name) == null) {
            throw new CredentialProblemException("There is no connector called " + name + ".");
        }
        if (request.credential() == null || request.credential().isBlank()) {
            throw new CredentialProblemException("Choose a credential first, so that this can be read from the service.");
        }
        ConnectorAction step = ConnectorAction.of(name);
        step.setOperation(request.operation());
        if (request.settings() != null) {
            request.settings().forEach(step::setSetting);
        }
        step.useCredentialValues(credentials.valuesFor(request.credential(), name));
        try {
            return lookup.apply(step);
        } catch (IllegalArgumentException e) {
            throw new CredentialProblemException(e.getMessage());
        }
    }
}
