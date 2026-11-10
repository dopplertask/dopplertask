package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionLog;
import com.dopplertask.dopplertask.dto.TaskExecutionLogResponseDTO;
import com.dopplertask.dopplertask.filter.AccessFilter;
import com.dopplertask.dopplertask.service.ExecutionService;
import com.dopplertask.dopplertask.service.RunHousekeeping;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import com.dopplertask.dopplertask.domain.TaskExecutionStep;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
public class ExecutionController {

    private final ExecutionService executionService;
    private final RunHousekeeping runHousekeeping;

    public ExecutionController(@Autowired ExecutionService executionService, RunHousekeeping runHousekeeping) {
        this.executionService = executionService;
        this.runHousekeeping = runHousekeeping;
    }

    /** Only a person may stop a run, and the run keeps who did. */
    @PostMapping("/execution/{id}/stop")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void stop(@PathVariable("id") long id, HttpServletRequest request) throws InterruptedException {
        runHousekeeping.stop(id, AccessFilter.personOf(request).map(person -> "This run was stopped by " + person + ".").orElse("This run was stopped."));
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
            responseDTO.setStatus(executionObj.getStatus().name());
            List<TaskExecutionStep> steps = new ArrayList<>(executionObj.getSteps());
            if (executionObj.getStatus() == TaskExecutionStatus.STARTED) {
                steps.addAll(executionService.getLiveSteps(id));
            }
            responseDTO.setSteps(steps);
            responseDTO.setPreview(executionObj.getPreview() != null);
            responseDTO.setStartDate(executionObj.getStartdate());
            responseDTO.setEndDate(executionObj.getEnddate());
            executionObj.getParameters().forEach((name, parameter) -> {
                if (!name.startsWith("TRIGGER_")) {
                    responseDTO.getParameters().put(name, parameter.toString());
                }
            });

            return new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

}
