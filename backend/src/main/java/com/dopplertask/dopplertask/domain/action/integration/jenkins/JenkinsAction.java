package com.dopplertask.dopplertask.domain.action.integration.jenkins;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.common.ScriptLanguage;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import com.dopplertask.dopplertask.domain.action.connection.HttpHeader;
import com.dopplertask.dopplertask.service.BroadcastListener;
import com.dopplertask.dopplertask.service.ColumnEncryptor;
import com.dopplertask.dopplertask.service.TaskService;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Lob;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "JenkinsAction")
@DiscriminatorValue("jenkins_action")
public class JenkinsAction extends Action {

    // Credentials
    @Convert(converter = ColumnEncryptor.class)
    private String username;

    @Convert(converter = ColumnEncryptor.class)
    private String apiToken;

    @Convert(converter = ColumnEncryptor.class)
    private String jenkinsUrl;

    @Column(columnDefinition = "BOOLEAN")
    private boolean useCrumb = true;

    private String resourceType;
    private String operation;

    private String reason;

    // Job
    // Job Operation: Trigger, Trigger with parameters
    private String jobName;

    @OneToMany(mappedBy = "jenkinsAction", cascade = CascadeType.ALL)
    private List<JenkinsParameter> jenkinsParameters = new ArrayList<>();

    // Job Operation: Create, Copy
    private String newJobName;

    // Job Operation: Create
    @Lob
    @Column(columnDefinition = "TEXT")
    private String newJobXML;

    @Column(columnDefinition = "BOOLEAN")
    private boolean returnAll;

    private String limit;

    @NotNull
    @Override
    public ActionResult run(@NotNull TaskService taskService, @NotNull TaskExecution execution, @NotNull VariableExtractorUtil variableExtractorUtil, @Nullable BroadcastListener broadcastListener) throws IOException {
        // Handle credentials
        String credUsername = variableExtractorUtil.extract(username, execution, ScriptLanguage.VELOCITY);
        String credApiToken = variableExtractorUtil.extract(apiToken, execution, ScriptLanguage.VELOCITY);
        String credJenkinsUrl = variableExtractorUtil.extract(jenkinsUrl, execution, ScriptLanguage.VELOCITY);

        if (credJenkinsUrl.isEmpty()) {
            ActionResult result = new ActionResult();
            result.setStatusCode(StatusCode.FAILURE);
            result.setErrorMsg("Jenkins URL is empty. Please input a Jenkins instance URL");
            return result;
        }

        // Handle properties
        String resourceTypeVariable = variableExtractorUtil.extract(resourceType, execution, getScriptLanguage());
        String operationVariable = variableExtractorUtil.extract(operation, execution, getScriptLanguage());

        String jobNameVariable = variableExtractorUtil.extract(jobName, execution, getScriptLanguage());
        String newJobNameVariable = variableExtractorUtil.extract(newJobName, execution, getScriptLanguage());
        String newJobXMLVariable = variableExtractorUtil.extract(newJobXML, execution, getScriptLanguage());
        String limitVariable = variableExtractorUtil.extract(limit, execution, getScriptLanguage());


        switch (resourceTypeVariable) {
            case "build":
                if ("getAll".equals(operationVariable)) {
                    String limitUrl = "";
                    if(returnAll) {
                        limitUrl = "{0, " + limitVariable + "}";
                    }
                    return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "job/" + jobNameVariable + "/api/json?tree=builds[*]" + limitUrl, "POST", Map.of(), "", taskService, variableExtractorUtil);
                }
                break;
            case "instance":
                switch (operationVariable) {
                    case "cancelQuietDown":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "cancelQuietDown", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "quietDown":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "quietDown", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "restart":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "restart", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "safelyRestart":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "safeRestart", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "safelyShutdown":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "safeExit", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "shutdown":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "exit", "POST", Map.of(), "", taskService, variableExtractorUtil);
                }

                break;
            case "job":
            default:
                switch (operationVariable) {
                    case "getAll":
                        ActionResult actionResult = callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "api/json", "GET", Map.of(), "", taskService, variableExtractorUtil);
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode rootNode = mapper.readTree(actionResult.getOutput());
                        actionResult.setOutput(rootNode.get("jobs").toPrettyString());

                        return actionResult;
                    case "trigger":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "job/" + jobNameVariable + "/build", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "triggerWithParameters":
                        Map<String, String> queryParameters = new HashMap<>();
                        jenkinsParameters.forEach(jenkinsParameter -> {
                            try {
                                queryParameters.put(variableExtractorUtil.extract(jenkinsParameter.getName(), execution, getScriptLanguage()), variableExtractorUtil.extract(jenkinsParameter.getValue(), execution, getScriptLanguage()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "job/" + jobNameVariable + "/buildWithParameters", "POST", queryParameters, "", taskService, variableExtractorUtil);
                    case "copy":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "createItem", "POST", Map.of("name", newJobNameVariable, "mode", "copy", "from", jobNameVariable), "", taskService, variableExtractorUtil);
                    case "create":
                    default:
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "createItem", "POST", Map.of("name", newJobNameVariable), newJobXMLVariable, taskService, variableExtractorUtil);
                }

        }


        ActionResult result = new ActionResult();
        result.setStatusCode(StatusCode.FAILURE);
        result.setErrorMsg("Something went wrong. No alternative matched.");
        return result;
    }

    @NotNull
    private ActionResult callJenkinsAction(String credUsername, String credApiToken, String credJenkinsUrl, String path, String method, Map<String, String> queryParameters, String body, @NotNull TaskService taskService, @NotNull VariableExtractorUtil variableExtractorUtil) throws IOException {
        HttpAction action = new HttpAction();

        StringBuilder queryParamSB = new StringBuilder();
        queryParameters.forEach((key, value) -> queryParamSB.append(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8) + "&"));
        if (queryParamSB.length() > 0) {
            queryParamSB.setLength(queryParamSB.length() - 1);
        }

        action.setUrl(credJenkinsUrl + "/" + path + "" + (!queryParamSB.isEmpty() ? "?" + queryParamSB : ""));

        action.setBody(body);
        action.setMethod(method);
        HttpHeader contentTypeHeader = new HttpHeader();
        contentTypeHeader.setHeaderName("Content-Type");
        contentTypeHeader.setHeaderValue("text/xml");

        List<HttpHeader> authorizationHeaders = getAuthorizationHeaders(taskService, variableExtractorUtil, credUsername, credApiToken, credJenkinsUrl);
        action.setHeaders(authorizationHeaders);
        action.getHeaders().add(contentTypeHeader);

        ActionResult httpResultAction = action.run(taskService, new TaskExecution(), variableExtractorUtil, null);

        ActionResult result = new ActionResult();
        if (!httpResultAction.getOutput().contains("Error")) {
            result.setStatusCode(StatusCode.SUCCESS);
        } else {
            result.setStatusCode(StatusCode.FAILURE);
        }
        result.setOutput(httpResultAction.getOutput());
        return result;
    }

    @NotNull
    private List<HttpHeader> getAuthorizationHeaders(@NotNull TaskService taskService, @NotNull VariableExtractorUtil variableExtractorUtil, String credUsername, String credApiToken, String credJenkinsUrl) throws IOException {
        List<HttpHeader> headers = new ArrayList<>();
        if (useCrumb) {
            String crumbValue = requestCrumb(taskService, variableExtractorUtil, credUsername, credApiToken, credJenkinsUrl);

            HttpHeader crumbHeader = new HttpHeader();
            crumbHeader.setHeaderName("Jenkins-Crumb");
            crumbHeader.setHeaderValue(crumbValue);

            headers.add(crumbHeader);
        }

        HttpHeader authorizationHeader = new HttpHeader();
        authorizationHeader.setHeaderName("Authorization");
        authorizationHeader.setHeaderValue("Basic " + Base64.getEncoder().withoutPadding().encodeToString((credUsername + ":" + credApiToken).getBytes(StandardCharsets.UTF_8)));

        headers.add(authorizationHeader);

        return headers;
    }

    private String requestCrumb(@NotNull TaskService taskService, @NotNull VariableExtractorUtil variableExtractorUtil, String credUsername, String credApiToken, String credJenkinsUrl) throws IOException {
        HttpAction crumbAction = new HttpAction();
        crumbAction.setUrl(credJenkinsUrl + "/crumbIssuer/api/json");
        crumbAction.setMethod("GET");

        HttpHeader authorizationHeader = new HttpHeader();
        authorizationHeader.setHeaderName("Authorization");
        authorizationHeader.setHeaderValue("Basic " + Base64.getEncoder().withoutPadding().encodeToString((credUsername + ":" + credApiToken).getBytes()));

        crumbAction.setHeaders(List.of(
                authorizationHeader
        ));


        ActionResult crumbActionResult = crumbAction.run(taskService, new TaskExecution(), variableExtractorUtil, null);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(crumbActionResult.getOutput());
        return rootNode.get("crumb").asText();
    }

    @NotNull
    @Override
    public List<PropertyInformation> getActionInfo() {
        List<PropertyInformation> actionList = super.getActionInfo();

        actionList.add(new PropertyInformation("username", "Username", PropertyInformation.PropertyInformationType.STRING, "", "Jenkins username", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.CREDENTIAL));
        actionList.add(new PropertyInformation("apiToken", "API Token", PropertyInformation.PropertyInformationType.STRING, "", "Jenkins API Token", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.CREDENTIAL));
        actionList.add(new PropertyInformation("jenkinsUrl", "Jenkins URL", PropertyInformation.PropertyInformationType.STRING, "", "Jenkins URL", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.CREDENTIAL));
        actionList.add(new PropertyInformation("useCrumb", "Use Crumb", PropertyInformation.PropertyInformationType.BOOLEAN, "true", "If CSRF protection is on, leave this activated", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.CREDENTIAL));


        actionList.add(new PropertyInformation("resourceType", "Resource Type", PropertyInformation.PropertyInformationType.DROPDOWN, "job", "", List.of(
                new PropertyInformation("job", "Job"),
                new PropertyInformation("build", "Build"),
                new PropertyInformation("instance", "Instance")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
        )));
        actionList.add(new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "getAll", "", List.of(
                new PropertyInformation("getAll", "Get All"),
                new PropertyInformation("copy", "Copy"),
                new PropertyInformation("create", "Create"),
                new PropertyInformation("trigger", "Trigger"),
                new PropertyInformation("triggerParams", "Trigger with Parameters")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"job"})));
        actionList.add(new PropertyInformation("jobName", "Job Name", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"job"},
                "operation", new String[]{"trigger", "triggerParams", "copy"})));
        actionList.add(new PropertyInformation("param", "Parameters", PropertyInformation.PropertyInformationType.MAP, "", "", List.of(
                new PropertyInformation("name", "Name"),
                new PropertyInformation("value", "Value")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"job"},
                "operation", new String[]{"triggerParams"})));
        actionList.add(new PropertyInformation("newJob", "New Job Name", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"job"},
                "operation", new String[]{"copy", "create"})));
        actionList.add(new PropertyInformation("newJobXML", "XML", PropertyInformation.PropertyInformationType.MULTILINE, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"job"},
                "operation", new String[]{"create"})));
        actionList.add(new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "safeRestart", "", List.of(
                new PropertyInformation("cancelQuietDown", "Cancel Quiet Down"),
                new PropertyInformation("quietDown", "Quiet Down"),
                new PropertyInformation("restart", "Restart"),
                new PropertyInformation("safeRestart", "Safely Restart"),
                new PropertyInformation("safeExit", "Safely Shutdown"),
                new PropertyInformation("exit", "Shutdown")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"instance"})));
        actionList.add(new PropertyInformation("reason", "Reason", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"instance"},
                "operation", new String[]{"quietDown"})));

        actionList.add(new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "getAll", "", List.of(
                new PropertyInformation("getAll", "Get All")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"build"})));
        actionList.add(new PropertyInformation("jobName", "Job Name", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"build"},
                "operation", new String[]{"getAll"})));
        actionList.add(new PropertyInformation("returnAll", "Return All", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"build"},
                "operation", new String[]{"getAll"})));
        actionList.add(new PropertyInformation("limit", "Limit", PropertyInformation.PropertyInformationType.STRING, "50", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"build"},
                "operation", new String[]{"getAll"},
                "returnAll", new String[]{"false"})));


        return actionList;
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Consumes Jenkins API";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getApiToken() {
        return apiToken;
    }

    public void setApiToken(String apiToken) {
        this.apiToken = apiToken;
    }

    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = jenkinsUrl;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getNewJobName() {
        return newJobName;
    }

    public void setNewJobName(String newJobName) {
        this.newJobName = newJobName;
    }

    public String getNewJobXML() {
        return newJobXML;
    }

    public void setNewJobXML(String newJobXML) {
        this.newJobXML = newJobXML;
    }

    public boolean isUseCrumb() {
        return useCrumb;
    }

    public void setUseCrumb(boolean useCrumb) {
        this.useCrumb = useCrumb;
    }

    public List<JenkinsParameter> getJenkinsParameters() {
        return jenkinsParameters;
    }

    public void setJenkinsParameters(List<JenkinsParameter> jenkinsParameters) {
        this.jenkinsParameters = jenkinsParameters;
        this.jenkinsParameters.forEach(jenkinsParameter -> jenkinsParameter.setJenkinsAction(this));
    }

    public String getReason() {
        return reason;
    }

    public JenkinsAction setReason(String reason) {
        this.reason = reason;
        return this;
    }

    public boolean isReturnAll() {
        return returnAll;
    }

    public JenkinsAction setReturnAll(boolean returnAll) {
        this.returnAll = returnAll;
        return this;
    }

    public String getLimit() {
        return limit;
    }

    public JenkinsAction setLimit(String limit) {
        this.limit = limit;
        return this;
    }
}
