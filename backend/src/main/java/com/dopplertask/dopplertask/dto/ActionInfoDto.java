package com.dopplertask.dopplertask.dto;

import com.dopplertask.dopplertask.domain.action.Action;

import java.util.List;

public class ActionInfoDto {
    private String name;
    private String description;
    private List<Action.PropertyInformation> propertyInformationList;
    private boolean trigger;

    public ActionInfoDto(String name, String description, List<Action.PropertyInformation> propertyInformationList, boolean trigger) {
        this.name = name;
        this.description = description;
        this.propertyInformationList = propertyInformationList;
        this.trigger = trigger;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Action.PropertyInformation> getPropertyInformationList() {
        return propertyInformationList;
    }

    public void setPropertyInformationList(List<Action.PropertyInformation> propertyInformationList) {
        this.propertyInformationList = propertyInformationList;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isTrigger() {
        return trigger;
    }

    public void setTrigger(boolean trigger) {
        this.trigger = trigger;
    }
}
