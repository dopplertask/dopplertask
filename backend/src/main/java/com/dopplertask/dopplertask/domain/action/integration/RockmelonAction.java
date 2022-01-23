package com.dopplertask.dopplertask.domain.action.integration;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.common.ScriptLanguage;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import com.dopplertask.dopplertask.service.BroadcastListener;
import com.dopplertask.dopplertask.service.TaskService;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.persistence.*;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "RockmelonAction")
@DiscriminatorValue("rockmelon_action")
public class RockmelonAction extends Action {

    private static final String ROCKMELON_API_URL = "https://www.rockmelon.app/api/link-group/create";

    private String apiKey;
    private String name;

    @OneToMany(mappedBy = "rockmelonAction", cascade = CascadeType.ALL)
    private List<RockmelonParameter> links = new ArrayList<>();

    private String rcIdentifier;

    @Override
    public ActionResult run(@NotNull TaskService taskService, @NotNull TaskExecution execution, @NotNull VariableExtractorUtil variableExtractorUtil, @Nullable BroadcastListener broadcastListener) throws IOException {
        String apiKeyVariable = variableExtractorUtil.extract(apiKey, execution, ScriptLanguage.VELOCITY);
        String nameVariable = variableExtractorUtil.extract(name, execution, getScriptLanguage());
        String rcIdentifierVariable = variableExtractorUtil.extract(rcIdentifier, execution, getScriptLanguage());

        // Prepare request
        StringBuilder linksStr = new StringBuilder();
        links.forEach(link -> {
            try {
                linksStr.append("links[]=" + URLEncoder.encode(variableExtractorUtil.extract(link.getValue(), execution, getScriptLanguage()), StandardCharsets.UTF_8) + "&");
            } catch (IOException e) {
                e.printStackTrace();
            }
        });


        // Call Rockmelon API
        HttpAction rockmelonHttpRequest = new HttpAction();
        rockmelonHttpRequest.setUrl(ROCKMELON_API_URL + "?name=" + URLEncoder.encode(nameVariable, StandardCharsets.UTF_8) + "&" + linksStr + "&api_key=" + apiKeyVariable + "&rc_identifier="+ URLEncoder.encode(rcIdentifierVariable, StandardCharsets.UTF_8));
        rockmelonHttpRequest.setMethod("POST");
        ActionResult actionResult = rockmelonHttpRequest.run(taskService, new TaskExecution(), variableExtractorUtil, null);
        return actionResult;

    }

    @NotNull
    @Override
    public List<PropertyInformation> getActionInfo() {
        List<PropertyInformation> actionList = super.getActionInfo();
        actionList.add(new PropertyInformation("apiKey", "API Key", PropertyInformation.PropertyInformationType.STRING, "", "Rockmelon API Key", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.CREDENTIAL));
        actionList.add(new PropertyInformation("name", "Name", PropertyInformation.PropertyInformationType.STRING, "", "Name of Rockmelon Link", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.PROPERTY));
        actionList.add(new PropertyInformation("rcIdentifier", "RC Identifier", PropertyInformation.PropertyInformationType.STRING, "", "Rockmelon Collection identifier, which this Rockmelon Link belongs to", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.PROPERTY));
        actionList.add(new PropertyInformation("links", "Links", PropertyInformation.PropertyInformationType.MAP, "", "List of links to add to this RL", List.of(
                new PropertyInformation("value", "Link URL")
        )));
        return actionList;
    }

    @NotNull
    @Override
    public String getDescription() {
        return "Consumes Rockmelon API";
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<RockmelonParameter> getLinks() {
        return links;
    }

    public void setLinks(List<RockmelonParameter> rockmelonParameters) {
        this.links = rockmelonParameters;
        this.links.forEach(rockmelonParameter -> {
            rockmelonParameter.setRockmelonAction(this);
        });
    }

    public String getRcIdentifier() {
        return rcIdentifier;
    }

    public void setRcIdentifier(String rcIdentifier) {
        this.rcIdentifier = rcIdentifier;
    }
}
