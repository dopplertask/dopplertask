package com.dopplertask.dopplertask.controller;


import com.dopplertask.dopplertask.service.LinkedTaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Date;

@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(LinkedTaskNotFoundException.class)
    public ResponseEntity<ErrorDto> generateLinkedTaskNotFoundException(LinkedTaskNotFoundException ex) {
        ErrorDto errorDTO = new ErrorDto();
        errorDTO.setMessage(ex.getMessage());
        errorDTO.setTime(new Date().toString());

        return new ResponseEntity<>(errorDTO, HttpStatus.NOT_FOUND);
    }
}
