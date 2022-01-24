package com.dopplertask.dopplertask.domain.action.integration.jenkins;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.common.ScriptLanguage;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import com.dopplertask.dopplertask.domain.action.connection.HttpHeader;
import com.dopplertask.dopplertask.service.BroadcastListener;
import com.dopplertask.dopplertask.service.TaskService;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Entity
@Table(name = "JenkinsAction")
@DiscriminatorValue("jenkins_action")
public class JenkinsAction extends Action {

    // Credentials
    private String username;
    private String apiToken;
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


        switch (resourceTypeVariable) {
            case "Build":
                switch (operationVariable) {
                    case "GetAll":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "job/" + jobNameVariable + "/api/json?tree=builds[*]", "POST", Map.of(), "", taskService, variableExtractorUtil);
                }
                break;
            case "Instance":
                switch (operationVariable) {
                    case "CancelQuietDown":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "cancelQuietDown", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "QuietDown":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "quietDown", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "Restart":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "restart", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "SafelyRestart":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "safeRestart", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "SafelyShutdown":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "safeExit", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "Shutdown":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "exit", "POST", Map.of(), "", taskService, variableExtractorUtil);
                }

                break;
            case "Job":
            default:
                switch (operationVariable) {
                    case "GetAllJobs":
                        ActionResult actionResult = callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "api/json", "GET", Map.of(), "", taskService, variableExtractorUtil);
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode rootNode = mapper.readTree(actionResult.getOutput());
                        actionResult.setOutput(rootNode.get("jobs").toPrettyString());

                        return actionResult;
                    case "Trigger":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "job/" + jobNameVariable + "/build", "POST", Map.of(), "", taskService, variableExtractorUtil);
                    case "TriggerWithParameters":
                        Map<String, String> queryParameters = new HashMap<>();
                        jenkinsParameters.forEach(jenkinsParameter -> {
                            try {
                                queryParameters.put(variableExtractorUtil.extract(jenkinsParameter.getName(), execution, getScriptLanguage()), variableExtractorUtil.extract(jenkinsParameter.getValue(), execution, getScriptLanguage()));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "job/" + jobNameVariable + "/buildWithParameters", "POST", queryParameters, "", taskService, variableExtractorUtil);
                    case "Copy":
                        return callJenkinsAction(credUsername, credApiToken, credJenkinsUrl, "createItem", "POST", Map.of("name", newJobNameVariable, "mode", "copy", "from", jobNameVariable), "", taskService, variableExtractorUtil);
                    case "Create":
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
        queryParameters.forEach((key, value) -> {
            queryParamSB.append(URLEncoder.encode(key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8) + "&");
        });
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

        actionList.add(new PropertyInformation("resourceType", "Resource type", PropertyInformation.PropertyInformationType.DROPDOWN, "Job", "", List.of(
                new PropertyInformation("Job", "Job", List.of(
                        new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "Create", "", List.of(
                                new PropertyInformation("Create", "Create", List.of(
                                        new PropertyInformation("newJobName", "New Job Name"),
                                        new PropertyInformation("newJobXML", "New Job XML", PropertyInformation.PropertyInformationType.MULTILINE, "", "XML for the new job. Add config.xml to the end of the job URL to create a clone of an existing job")
                                )),
                                new PropertyInformation("Copy", "Copy", List.of(
                                        new PropertyInformation("jobName", "Existing Job Name"),
                                        new PropertyInformation("newJobName", "New Job Name")
                                )),
                                new PropertyInformation("Trigger", "Trigger", List.of(
                                        new PropertyInformation("jobName", "Job Name")
                                )),
                                new PropertyInformation("TriggerWithParameters", "Trigger With Parameters", List.of(
                                        new PropertyInformation("jobName", "Job Name"),
                                        new PropertyInformation("jenkinsParameters", "Parameters", PropertyInformation.PropertyInformationType.MAP, "", "", List.of(
                                                new PropertyInformation("name", "Name"),
                                                new PropertyInformation("value", "Value")
                                        ))
                                )),
                                new PropertyInformation("GetAllJobs", "Get all jobs")
                        ))
                )),
                new PropertyInformation("Instance", "Instance", List.of(
                        new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "CancelQuietDown", "", List.of(
                                new PropertyInformation("CancelQuietDown", "Cancel Quiet Down", PropertyInformation.PropertyInformationType.STRING, "", "Cancel quiet down state"),
                                new PropertyInformation("QuietDown", "Quiet Down", PropertyInformation.PropertyInformationType.STRING, "", "Put Jenkins in a Quiet mode, in preparation for a restart. In that mode Jenkins don’t start any build", List.of(
                                        new PropertyInformation("reason", "Reason")
                                )),
                                new PropertyInformation("Restart", "Restart", PropertyInformation.PropertyInformationType.STRING, "", "Restart Jenkins instance immediately"),
                                new PropertyInformation("SafelyRestart", "Safely Restart", PropertyInformation.PropertyInformationType.STRING, "", "Puts Jenkins into the quiet mode, wait for existing builds to be completed, and then restart Jenkins"),
                                new PropertyInformation("SafelyShutdown", "Safely Shutdown", PropertyInformation.PropertyInformationType.STRING, "", "Puts Jenkins into the quiet mode, wait for existing builds to be completed, and then shut down Jenkins"),
                                new PropertyInformation("Shutdown", "Shutdown", PropertyInformation.PropertyInformationType.STRING, "", "Shut down Jenkins immediately")
                        ))
                )),
                new PropertyInformation("Build", "Build", List.of(
                        new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "GetAll", "", List.of(
                                new PropertyInformation("GetAll", "Get All", List.of(
                                        new PropertyInformation("jobName", "Job Name")
                                ))
                        ))
                ))

        )));

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
        this.jenkinsParameters.forEach(jenkinsParameter -> {
            jenkinsParameter.setJenkinsAction(this);
        });
    }

    public String getReason() {
        return reason;
    }

    public JenkinsAction setReason(String reason) {
        this.reason = reason;
        return this;
    }
}
