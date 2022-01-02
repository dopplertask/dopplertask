package com.dopplertask.dopplertask.dto;

import java.util.ArrayList;
import java.util.List;

public class ActionListResponseDto {
    private List<ActionInfoDto> actions;

    public ActionListResponseDto() {
        actions = new ArrayList<>();
    }

    public List<ActionInfoDto> getActions() {
        return actions;
    }

    public void setActions(List<ActionInfoDto> actions) {
        this.actions = actions;
    }
}
