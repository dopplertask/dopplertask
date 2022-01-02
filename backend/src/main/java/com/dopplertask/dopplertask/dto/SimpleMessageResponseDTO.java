package com.dopplertask.dopplertask.dto;

public class SimpleMessageResponseDTO {
    private String message;

    public SimpleMessageResponseDTO(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
