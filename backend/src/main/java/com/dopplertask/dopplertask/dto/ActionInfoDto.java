package com.dopplertask.dopplertask.dto;

import com.dopplertask.dopplertask.domain.action.Action;

import java.util.List;
import java.util.Map;

/** A kind of step as the editor lists it. Label and icon are empty for the kinds the editor already knows how to show. */
public record ActionInfoDto(String name, String label, String icon, String description, List<Action.PropertyInformation> propertyInformationList,
                            boolean trigger, Map<String, String> outputs, String credentialType) {
}
