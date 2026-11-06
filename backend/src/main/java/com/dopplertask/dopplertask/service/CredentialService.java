package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.dao.CredentialDao;
import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.Credential;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation;
import com.dopplertask.dopplertask.domain.action.Action.PropertyInformation.PropertyInformationCategory;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import com.dopplertask.dopplertask.domain.action.connection.HttpHeader;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.Transient;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The one place that holds what steps sign in with. A step names a credential, and gets its values only while it runs.
 */
@Service
public class CredentialService {

    private static final Pattern VALID_NAME = Pattern.compile("[\\w.-]+");
    private static final Pattern SECRET_HEADER = Pattern.compile("(?i).*(authorization|api[-_]?key|token|secret|password).*");
    private static final String HIDDEN = "(hidden)";
    // Anything shorter would also hide ordinary text in the output.
    private static final int SHORTEST_HIDDEN_VALUE = 4;
    private static final Map<String, Class<?>> STEP_TYPES = Arrays.stream(Action.class.getAnnotation(JsonSubTypes.class).value())
            .collect(Collectors.toMap(JsonSubTypes.Type::name, JsonSubTypes.Type::value));

    private final CredentialDao credentialDao;
    private final CredentialCipher cipher;
    private final ObjectMapper mapper = new ObjectMapper();

    public CredentialService(CredentialDao credentialDao, CredentialCipher cipher) {
        this.credentialDao = credentialDao;
        this.cipher = cipher;
    }

    public record CredentialSummary(String name, String stepType, List<String> fieldsSet, List<String> usedBy, Date updated) {
    }

    @FunctionalInterface
    public interface StepRun {
        ActionResult run() throws IOException;
    }

    @Transactional(readOnly = true)
    public List<CredentialSummary> list() {
        return credentialDao.findAll(Sort.by("name")).stream().map(this::summaryOf).collect(Collectors.toList());
    }

    private CredentialSummary summaryOf(Credential credential) {
        return new CredentialSummary(credential.getName(), credential.getStepType(), new ArrayList<>(valuesOf(credential).keySet()),
                credentialDao.findTaskNamesUsing(credential.getName()), credential.getUpdated());
    }

    @Transactional
    public CredentialSummary create(String name, String stepType, Map<String, String> values) {
        if (name == null || !VALID_NAME.matcher(name).matches()) {
            throw new CredentialProblemException("A credential needs a name of letters, numbers, dots, dashes and underscores, such as prod-slack.");
        }
        if (credentialDao.findByName(name).isPresent()) {
            throw new CredentialProblemException("There is already a credential called \"" + name + "\".");
        }
        Credential credential = new Credential();
        credential.setName(name);
        credential.setStepType(stepType);
        store(credential, onlyFieldsOf(stepType, values));
        return summaryOf(credential);
    }

    /**
     * A field left empty keeps its value, since the current values are never shown to whoever edits this.
     */
    @Transactional
    public CredentialSummary update(String name, Map<String, String> changes) {
        Credential credential = credentialDao.findByName(name).orElseThrow(() -> new CredentialProblemException("There is no credential called \"" + name + "\"."));
        Map<String, String> values = new LinkedHashMap<>(valuesOf(credential));
        values.putAll(onlyFieldsOf(credential.getStepType(), changes));
        store(credential, values);
        return summaryOf(credential);
    }

    @Transactional
    public void delete(String name) {
        Credential credential = credentialDao.findByName(name).orElseThrow(() -> new CredentialProblemException("There is no credential called \"" + name + "\"."));
        List<String> usedBy = credentialDao.findTaskNamesUsing(name);
        if (!usedBy.isEmpty()) {
            throw new CredentialProblemException("\"" + name + "\" is used by " + String.join(", ", usedBy) + ". Choose another credential there first.");
        }
        credentialDao.delete(credential);
    }

    /**
     * Runs a step with the values of its credential, and takes them away again whatever happens. Values that the step or
     * a service repeats are hidden from the result, since that is stored with the run.
     */
    public ActionResult runWith(Action action, StepRun run) throws IOException {
        Map<String, String> values = Map.of();
        String name = action.getCredential();
        if (name != null && !name.isBlank()) {
            Optional<Credential> credential = credentialDao.findByName(name);
            String stepType = action.getClass().getSimpleName();
            if (credential.isEmpty()) {
                return ActionResult.Companion.failure("The credential \"" + name + "\" does not exist. Create it under Credentials, or choose another one.");
            }
            if (!credential.get().getStepType().equals(stepType)) {
                return ActionResult.Companion.failure("The credential \"" + name + "\" is for " + credential.get().getStepType() + ", and this step is " + stepType + ".");
            }
            values = valuesOf(credential.get());
        }
        BeanWrapperImpl step = new BeanWrapperImpl(action);
        values.forEach(step::setPropertyValue);
        try {
            return withoutSecrets(run.run(), action, values);
        } finally {
            values.keySet().forEach(field -> step.setPropertyValue(field, null));
        }
    }

    /**
     * What used to be written directly in a step, such as an imported task, becomes a credential. An agent may name
     * credentials but not hand over secrets, since only a person should decide what is stored.
     */
    @Transactional
    public void adoptInlineSecrets(List<Action> actions, boolean byAgent) {
        for (Action action : actions) {
            if (action instanceof HttpAction http) {
                moveSecretHeader(http);
            }
            Map<String, String> inline = inlineValuesOf(action);
            if (inline.isEmpty()) {
                continue;
            }
            if (byAgent) {
                throw new CredentialProblemException("An agent cannot save secrets in a step. Leave " + String.join(", ", inline.keySet())
                        + " out, and set credential to a name from list_credentials, or ask a person to create one.");
            }
            action.setCredential(findOrCreate(action.getClass().getSimpleName(), inline));
            BeanWrapperImpl step = new BeanWrapperImpl(action);
            inline.keySet().forEach(field -> step.setPropertyValue(field, null));
        }
    }

    /**
     * The credential of this kind with exactly these values, or a new one, so that moving secrets does not make copies.
     */
    @Transactional
    public String findOrCreate(String stepType, Map<String, String> values) {
        List<Credential> sameType = credentialDao.findByStepType(stepType);
        for (Credential credential : sameType) {
            if (valuesOf(credential).equals(values)) {
                return credential.getName();
            }
        }
        String base = stepType.replaceAll("Action$", "").toLowerCase();
        int number = sameType.size() + 1;
        while (credentialDao.findByName(base + "-" + number).isPresent()) {
            number++;
        }
        Credential credential = new Credential();
        credential.setName(base + "-" + number);
        credential.setStepType(stepType);
        store(credential, values);
        return credential.getName();
    }

    private void moveSecretHeader(HttpAction http) {
        if (http.getHeaders() == null) {
            return;
        }
        Optional<HttpHeader> secretHeader = http.getHeaders().stream()
                .filter(header -> header.getHeaderName() != null && SECRET_HEADER.matcher(header.getHeaderName()).matches())
                .findFirst();
        if (secretHeader.isPresent()) {
            http.setAuthHeaderName(secretHeader.get().getHeaderName());
            http.setAuthHeaderValue(secretHeader.get().getHeaderValue());
            http.setHeaders(http.getHeaders().stream().filter(header -> header != secretHeader.get()).collect(Collectors.toList()));
        }
    }

    private Map<String, String> inlineValuesOf(Action action) {
        BeanWrapperImpl step = new BeanWrapperImpl(action);
        Map<String, String> values = new LinkedHashMap<>();
        fieldsOf(action.getClass()).keySet().forEach(field -> {
            Object value = step.getPropertyValue(field);
            if (value != null && !value.toString().isBlank()) {
                values.put(field, value.toString());
            }
        });
        return values;
    }

    private ActionResult withoutSecrets(ActionResult result, Action action, Map<String, String> values) {
        Map<String, Boolean> fields = fieldsOf(action.getClass());
        values.forEach((field, value) -> {
            if (Boolean.TRUE.equals(fields.get(field)) && value.length() >= SHORTEST_HIDDEN_VALUE) {
                result.setOutput(result.getOutput().replace(value, HIDDEN));
                result.setErrorMsg(result.getErrorMsg().replace(value, HIDDEN));
            }
        });
        return result;
    }

    private Map<String, String> onlyFieldsOf(String stepType, Map<String, String> values) {
        Class<?> stepClass = STEP_TYPES.get(stepType);
        if (stepClass == null || fieldsOf(stepClass).isEmpty()) {
            throw new CredentialProblemException("Steps of the kind \"" + stepType + "\" do not use a credential.");
        }
        Map<String, Boolean> known = fieldsOf(stepClass);
        Map<String, String> kept = new LinkedHashMap<>();
        values.forEach((field, value) -> {
            if (!known.containsKey(field)) {
                throw new CredentialProblemException(stepType + " has no credential field called \"" + field + "\".");
            }
            if (value != null && !value.isBlank()) {
                kept.put(field, value);
            }
        });
        return kept;
    }

    private void store(Credential credential, Map<String, String> values) {
        try {
            credential.setEncryptedValues(cipher.encrypt(mapper.writeValueAsString(values)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A credential could not be written.", e);
        }
        credential.setUpdated(new Date());
        credentialDao.save(credential);
    }

    private Map<String, String> valuesOf(Credential credential) {
        try {
            return mapper.readValue(cipher.decrypt(credential.getEncryptedValues()), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A credential could not be read.", e);
        }
    }

    /**
     * The fields a kind of step takes from a credential, by name, with whether each one is secret. What the step declares
     * is the only list of them.
     */
    public static Map<String, Boolean> fieldsOf(Class<?> stepClass) {
        Map<String, Boolean> fields = new HashMap<>();
        try {
            Action step = (Action) stepClass.getDeclaredConstructor().newInstance();
            collect(step.getActionInfo(), fields);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("A step could not be looked at: " + stepClass.getSimpleName(), e);
        }
        return fields;
    }

    private static void collect(List<PropertyInformation> information, Map<String, Boolean> fields) {
        for (PropertyInformation property : information) {
            if (property.getCategory() == PropertyInformationCategory.SECRET) {
                fields.put(property.getName(), true);
            } else if (property.getCategory() == PropertyInformationCategory.CREDENTIAL) {
                fields.put(property.getName(), false);
            }
            collect(property.getOptions(), fields);
        }
    }

    /**
     * Whether a field is left out of the table of its step. The move to credentials removes a column, so this has to hold.
     */
    public static boolean isOnlyInCredentials(Class<?> stepClass, String field) {
        try {
            return stepClass.getDeclaredField(field).isAnnotationPresent(Transient.class);
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    public static Map<String, Class<?>> stepTypes() {
        return STEP_TYPES;
    }
}
