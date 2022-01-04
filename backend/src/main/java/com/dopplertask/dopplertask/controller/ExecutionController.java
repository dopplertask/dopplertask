package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionLog;
import com.dopplertask.dopplertask.dto.TaskExecutionLogResponseDTO;
import com.dopplertask.dopplertask.dto.TaskResponseSingleDTO;
import com.dopplertask.dopplertask.service.ExecutionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

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

    @GetMapping("/execution/{id}")
    public ResponseEntity<TaskExecutionLogResponseDTO> getTask(@PathVariable("id") long id) {
        Optional<TaskExecution> execution = executionService.getExecution(id);

        TaskExecutionLogResponseDTO responseDTO = new TaskExecutionLogResponseDTO();
        if (execution.isPresent()) {
            TaskExecution executionObj = execution.get();
            for (TaskExecutionLog log : executionObj.getLogs()) {
                responseDTO.getOutput().add(log.getOutput());
            }

            return new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

}
