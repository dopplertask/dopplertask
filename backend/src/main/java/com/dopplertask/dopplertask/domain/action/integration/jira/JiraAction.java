package com.dopplertask.dopplertask.domain.action.integration.jira;

import com.dopplertask.dopplertask.domain.action.Action;
import org.jetbrains.annotations.NotNull;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "JiraAction")
@DiscriminatorValue("jira_action")
public class JiraAction extends Action {

    private String resourceType;
    private String operation;

    private String jiraVersion;
    private String project;
    private String issueType;
    private String issueKey;
    private String summary;
    private String additionalFields;
    private String updateFields;

    @Column(name = "limitPosts")
    private String limit;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean jsonParameters;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean download;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean deleteSubtasks;

    @Column(nullable = false, columnDefinition = "TINYINT(1)")
    private boolean returnAll;

    @NotNull
    @Override
    public String getDescription() {
        return null;
    }

    @NotNull
    @Override
    public List<PropertyInformation> getActionInfo() {
        List<PropertyInformation> actionList = super.getActionInfo();


        actionList.add(new PropertyInformation("username", "Username", PropertyInformation.PropertyInformationType.STRING, "", "Jira username", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.CREDENTIAL));
        actionList.add(new PropertyInformation("apiToken", "API Token", PropertyInformation.PropertyInformationType.STRING, "", "Jira API Token", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.CREDENTIAL));
        actionList.add(new PropertyInformation("password", "Password", PropertyInformation.PropertyInformationType.STRING, "", "Password", Collections.emptyList(), PropertyInformation.PropertyInformationCategory.CREDENTIAL));


        actionList.add(new PropertyInformation("jiraVersion", "Jira Version", PropertyInformation.PropertyInformationType.DROPDOWN, "cloud", "", List.of(
                new PropertyInformation("cloud", "Cloud"),
                new PropertyInformation("server", "Server (Self Hosted)")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
        )));
        actionList.add(new PropertyInformation("resourceType", "Resource Type", PropertyInformation.PropertyInformationType.DROPDOWN, "issue", "", List.of(
                new PropertyInformation("issue", "Issue"),
                new PropertyInformation("issueAttachment", "Issue Attachment"),
                new PropertyInformation("issueComment", "Issue Comment"),
                new PropertyInformation("user", "User")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
        )));
        actionList.add(new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "create", "", List.of(
                new PropertyInformation("changelog", "Changelog"),
                new PropertyInformation("create", "Create"),
                new PropertyInformation("delete", "Delete"),
                new PropertyInformation("get", "Get"),
                new PropertyInformation("getAll", "Get All"),
                new PropertyInformation("notify", "Notify"),
                new PropertyInformation("transitions", "Status"),
                new PropertyInformation("update", "Update")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"})));
        actionList.add(new PropertyInformation("project", "Project", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"create"})));
        actionList.add(new PropertyInformation("issueType", "Issue Type", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"create"})));
        actionList.add(new PropertyInformation("summary", "Summary", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"create"})));
        actionList.add(new PropertyInformation("additionalFields", "Additional Fields", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("assignee", "Assignee"),
                new PropertyInformation("description", "Description"),
                new PropertyInformation("componentIds", "Components"),
                new PropertyInformation("customFieldsUi", "Custom Fields"),
                new PropertyInformation("labels", "Labels"),
                new PropertyInformation("serverLabels", "Labels"),
                new PropertyInformation("parentIssueKey", "Parent Issue Key"),
                new PropertyInformation("priority", "Priority"),
                new PropertyInformation("reporter", "Reporter"),
                new PropertyInformation("updateHistory", "Update History")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"create"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"update"})));
        actionList.add(new PropertyInformation("updateFields", "Update Fields", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("assignee", "Assignee"),
                new PropertyInformation("description", "Description"),
                new PropertyInformation("customFieldsUi", "Custom Fields"),
                new PropertyInformation("issueType", "Issue Type"),
                new PropertyInformation("labels", "Labels"),
                new PropertyInformation("serverLabels", "Labels"),
                new PropertyInformation("parentIssueKey", "Parent Issue Key"),
                new PropertyInformation("priority", "Priority"),
                new PropertyInformation("reporter", "Reporter"),
                new PropertyInformation("summary", "Summary"),
                new PropertyInformation("statusId", "Status ID")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"update"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"delete"})));
        actionList.add(new PropertyInformation("deleteSubtasks", "Delete Subtasks", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"delete"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"get"})));
        actionList.add(new PropertyInformation("additionalFields", "Additional Fields", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("expand", "Expand"),
                new PropertyInformation("fields", "Fields"),
                new PropertyInformation("fieldsByKey", "Fields By Key"),
                new PropertyInformation("properties", "Properties"),
                new PropertyInformation("updateHistory", "Update History")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"get"})));
        actionList.add(new PropertyInformation("returnAll", "Return All", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"getAll"})));
        actionList.add(new PropertyInformation("limit", "Limit", PropertyInformation.PropertyInformationType.STRING, "50", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"getAll"},
                "returnAll", new String[]{"false"})));
        actionList.add(new PropertyInformation("options", "Options", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("expand", "Expand"),
                new PropertyInformation("fields", "Fields"),
                new PropertyInformation("fieldsByKey", "Fields By Key"),
                new PropertyInformation("jql", " JQL")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "operation", new String[]{"getAll"},
                "resourceType", new String[]{"issue"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"changelog"})));
        actionList.add(new PropertyInformation("returnAll", "Return All", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"changelog"})));
        actionList.add(new PropertyInformation("limit", "Limit", PropertyInformation.PropertyInformationType.STRING, "50", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"changelog"},
                "returnAll", new String[]{"false"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"notify"})));
        actionList.add(new PropertyInformation("jsonParameters", "JSON Parameters", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"notify"})));
        actionList.add(new PropertyInformation("additionalFields", "Additional Fields", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("htmlBody", "HTML Body"),
                new PropertyInformation("subject", "Subject"),
                new PropertyInformation("textBody", "Text Body")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"notify"})));
        actionList.add(new PropertyInformation("notificationRecipientsUi", "Notification Recipients", PropertyInformation.PropertyInformationType.MAP, "", "", List.of(
                new PropertyInformation("reporter", "Reporter"),
                new PropertyInformation("assignee", "Assignee"),
                new PropertyInformation("watchers", "Watchers"),
                new PropertyInformation("voters", "Voters"),
                new PropertyInformation("users", "Users"),
                new PropertyInformation("groups", "Groups")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"notify"},
                "jsonParameters", new String[]{"false"})));
        actionList.add(new PropertyInformation("notificationRecipientsJson", "Notification Recipients", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"notify"},
                "jsonParameters", new String[]{"true"})));
        actionList.add(new PropertyInformation("notificationRecipientsRestrictionsUi", "Notification Recipients Restrictions", PropertyInformation.PropertyInformationType.MAP, "", "", List.of(
                new PropertyInformation("users", "Users"),
                new PropertyInformation("groups", "Groups")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"notify"},
                "jsonParameters", new String[]{"false"})));
        actionList.add(new PropertyInformation("notificationRecipientsRestrictionsJson", "Notification Recipients Restrictions", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"notify"},
                "jsonParameters", new String[]{"true"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"transitions"})));
        actionList.add(new PropertyInformation("additionalFields", "Additional Fields", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("expand", "Expand"),
                new PropertyInformation("transitionId", "Transition ID"),
                new PropertyInformation("skipRemoteOnlyCondition", "Skip Remote Only Condition")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issue"},
                "operation", new String[]{"transitions"})));
        actionList.add(new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "add", "", List.of(
                new PropertyInformation("add", "Add"),
                new PropertyInformation("get", "Get"),
                new PropertyInformation("getAll", "Get All"),
                new PropertyInformation("remove", "Remove")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"add"})));
        actionList.add(new PropertyInformation("binaryPropertyName", "Binary Property", PropertyInformation.PropertyInformationType.STRING, "data", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"add"})));
        actionList.add(new PropertyInformation("attachmentId", "Attachment ID", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"get"})));
        actionList.add(new PropertyInformation("download", "Download", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"get"})));
        actionList.add(new PropertyInformation("binaryProperty", "Binary Property", PropertyInformation.PropertyInformationType.STRING, "data", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"get"},
                "download", new String[]{"true"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"getAll"})));
        actionList.add(new PropertyInformation("returnAll", "Return All", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"getAll"})));
        actionList.add(new PropertyInformation("limit", "Limit", PropertyInformation.PropertyInformationType.STRING, "50", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"getAll"},
                "returnAll", new String[]{"false"})));
        actionList.add(new PropertyInformation("download", "Download", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"getAll"})));
        actionList.add(new PropertyInformation("binaryProperty", "Binary Property", PropertyInformation.PropertyInformationType.STRING, "data", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"getAll"},
                "download", new String[]{"true"})));
        actionList.add(new PropertyInformation("attachmentId", "Attachment ID", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueAttachment"},
                "operation", new String[]{"remove"})));
        actionList.add(new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "add", "", List.of(
                new PropertyInformation("add", "Add"),
                new PropertyInformation("get", "Get"),
                new PropertyInformation("getAll", "Get All"),
                new PropertyInformation("remove", "Remove"),
                new PropertyInformation("update", "Update")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"add"})));
        actionList.add(new PropertyInformation("jsonParameters", "JSON Parameters", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"add"})));
        actionList.add(new PropertyInformation("comment", "Comment", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"add"},
                "jsonParameters", new String[]{"false"})));
        actionList.add(new PropertyInformation("commentJson", "Document Format (JSON)", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"add"},
                "jsonParameters", new String[]{"true"})));
        actionList.add(new PropertyInformation("options", "Options", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("expand", "Expand")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"add"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"get"})));
        actionList.add(new PropertyInformation("commentId", "Comment ID", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"get"})));
        actionList.add(new PropertyInformation("options", "Options", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("expand", "Expand")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"get"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"getAll"})));
        actionList.add(new PropertyInformation("returnAll", "Return All", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"getAll"})));
        actionList.add(new PropertyInformation("limit", "Limit", PropertyInformation.PropertyInformationType.STRING, "50", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"getAll"},
                "returnAll", new String[]{"false"})));
        actionList.add(new PropertyInformation("options", "Options", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("expand", "Expand")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"getAll"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"remove"})));
        actionList.add(new PropertyInformation("commentId", "Comment ID", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"remove"})));
        actionList.add(new PropertyInformation("issueKey", "Issue Key", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"update"})));
        actionList.add(new PropertyInformation("commentId", "Comment ID", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"update"})));
        actionList.add(new PropertyInformation("jsonParameters", "JSON Parameters", PropertyInformation.PropertyInformationType.BOOLEAN, "false", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"update"})));
        actionList.add(new PropertyInformation("comment", "Comment", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"update"},
                "jsonParameters", new String[]{"false"})));
        actionList.add(new PropertyInformation("commentJson", "Document Format (JSON)", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"update"},
                "jsonParameters", new String[]{"true"})));
        actionList.add(new PropertyInformation("options", "Options", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("expand", "Expand")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"issueComment"},
                "operation", new String[]{"update"})));
        actionList.add(new PropertyInformation("operation", "Operation", PropertyInformation.PropertyInformationType.DROPDOWN, "create", "", List.of(
                new PropertyInformation("create", "Create"),
                new PropertyInformation("delete", "Delete"),
                new PropertyInformation("get", "Get")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"user"})));
        actionList.add(new PropertyInformation("username", "Username", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"user"},
                "operation", new String[]{"create"})));
        actionList.add(new PropertyInformation("emailAddress", "Email Address", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"user"},
                "operation", new String[]{"create"})));
        actionList.add(new PropertyInformation("displayName", "Display Name", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"user"},
                "operation", new String[]{"create"})));
        actionList.add(new PropertyInformation("additionalFields", "Additional Fields", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("password", "Password"),
                new PropertyInformation("notification", "Notification")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"user"},
                "operation", new String[]{"create"})));
        actionList.add(new PropertyInformation("accountId", "Account ID", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"user"},
                "operation", new String[]{"delete"})));
        actionList.add(new PropertyInformation("accountId", "Account ID", PropertyInformation.PropertyInformationType.STRING, "", "", List.of(
        ), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"user"},
                "operation", new String[]{"get"})));
        actionList.add(new PropertyInformation("additionalFields", "Additional Fields", PropertyInformation.PropertyInformationType.DROPDOWN, "", "", List.of(
                new PropertyInformation("expand", "Expand")), PropertyInformation.PropertyInformationCategory.PROPERTY, Map.of(
                "resourceType", new String[]{"user"},
                "operation", new String[]{"get"})));

        return actionList;
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

    public String getJiraVersion() {
        return jiraVersion;
    }

    public void setJiraVersion(String jiraVersion) {
        this.jiraVersion = jiraVersion;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getIssueKey() {
        return issueKey;
    }

    public void setIssueKey(String issueKey) {
        this.issueKey = issueKey;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getAdditionalFields() {
        return additionalFields;
    }

    public void setAdditionalFields(String additionalFields) {
        this.additionalFields = additionalFields;
    }

    public String getUpdateFields() {
        return updateFields;
    }

    public void setUpdateFields(String updateFields) {
        this.updateFields = updateFields;
    }

    public boolean isDeleteSubtasks() {
        return deleteSubtasks;
    }

    public void setDeleteSubtasks(boolean deleteSubtasks) {
        this.deleteSubtasks = deleteSubtasks;
    }

    public boolean isReturnAll() {
        return returnAll;
    }

    public void setReturnAll(boolean returnAll) {
        this.returnAll = returnAll;
    }

    public String getLimit() {
        return limit;
    }

    public void setLimit(String limit) {
        this.limit = limit;
    }

    public boolean isJsonParameters() {
        return jsonParameters;
    }

    public void setJsonParameters(boolean jsonParameters) {
        this.jsonParameters = jsonParameters;
    }

    public boolean isDownload() {
        return download;
    }

    public void setDownload(boolean download) {
        this.download = download;
    }
}
