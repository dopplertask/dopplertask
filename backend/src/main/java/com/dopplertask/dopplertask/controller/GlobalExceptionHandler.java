package com.dopplertask.dopplertask.controller;


import com.dopplertask.dopplertask.service.CredentialProblemException;
import com.dopplertask.dopplertask.service.IncompleteConnectionException;
import com.dopplertask.dopplertask.service.LinkedTaskNotFoundException;
import com.dopplertask.dopplertask.service.NoStartActionFoundException;
import com.dopplertask.dopplertask.service.TaskNotFoundException;
import com.dopplertask.dopplertask.service.WhiteSpaceInNameException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.dopplertask.dopplertask.service.ApprovalRequiredException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Date;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler({LinkedTaskNotFoundException.class, TaskNotFoundException.class})
    public ResponseEntity<ErrorDto> generateNotFoundError(RuntimeException ex) {
        return error(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ApprovalRequiredException.class)
    public ResponseEntity<ErrorDto> generateApprovalRequiredError(RuntimeException ex) {
        return error(ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({CredentialProblemException.class, WhiteSpaceInNameException.class, NoStartActionFoundException.class, IncompleteConnectionException.class})
    public ResponseEntity<ErrorDto> generateInvalidTaskError(RuntimeException ex) {
        return error(ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(JsonProcessingException.class)
    public ResponseEntity<ErrorDto> generateInvalidJsonError(JsonProcessingException ex) {
        String field = ex instanceof JsonMappingException mappingException ? fieldOf(mappingException) : "";
        return error("Invalid task JSON" + (field.isEmpty() ? "" : " at " + field) + ": " + ex.getOriginalMessage(), HttpStatus.BAD_REQUEST);
    }

    private String fieldOf(JsonMappingException ex) {
        return ex.getPath().stream()
                .map(reference -> reference.getFieldName() != null ? reference.getFieldName() : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }

    private ResponseEntity<ErrorDto> error(String message, HttpStatus status) {
        ErrorDto errorDTO = new ErrorDto();
        errorDTO.setMessage(message);
        errorDTO.setTime(new Date().toString());

        return new ResponseEntity<>(errorDTO, status);
    }
}
