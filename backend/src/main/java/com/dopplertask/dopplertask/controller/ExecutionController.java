package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.dto.TaskResponseSingleDTO;
import com.dopplertask.dopplertask.service.ExecutionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(@Autowired ExecutionService executionService) {
        this.executionService = executionService;
    }

    @DeleteMapping("/execution/{id}")
    public ResponseEntity<TaskResponseSingleDTO> deleteExecution(@PathVariable("id") long id) {
        executionService.deleteExecution(id);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
