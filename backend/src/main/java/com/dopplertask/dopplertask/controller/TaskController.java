package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.*;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.trigger.Trigger;
import com.dopplertask.dopplertask.dto.*;
import com.dopplertask.dopplertask.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.velocity.app.VelocityEngine;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
public class TaskController {

    private final TaskService taskService;
    private final ExecutionService executionService;

    public TaskController(@Autowired TaskService taskService, @Autowired ExecutionService executionService) {
        this.taskService = taskService;
        this.executionService = executionService;
    }

    private static String bytesToHex(byte[] hash) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @PostMapping(path = "/schedule/task")
    public ResponseEntity<SimpleIdResponseDto> scheduleTask(@RequestBody TaskRequestDTO taskRequestDTO) {
        boolean removeTaskAfterExecution = false;
        String token = "_UNSAVED_" + (int) (Math.random() * 1000000);
        if (taskRequestDTO.getTask() != null) {
            this.taskService.createTask(taskRequestDTO.getTask().getName() + token, taskRequestDTO.getTask().getParameters(), taskRequestDTO.getTask().getActions(), taskRequestDTO.getTask().getDescription(), taskRequestDTO.getTask().getConnections(), taskRequestDTO.getTask().getName() + token, taskRequestDTO.getTask().isActive());
            removeTaskAfterExecution = true;
        }

        Map<String, byte[]> parameters = new HashMap<>();
        taskRequestDTO.getParameters().forEach((parameterName, parameterValue) -> {
            parameters.put(parameterName, parameterValue.getBytes(StandardCharsets.UTF_8));
        });

        TaskRequest request = new TaskRequest(removeTaskAfterExecution ? taskRequestDTO.getTaskName() + token : taskRequestDTO.getTaskName(), parameters, removeTaskAfterExecution);
        request.setChecksum(taskRequestDTO.getTaskName());
        TaskExecution taskExecution = taskService.delegate(request);

        if (taskExecution != null) {
            SimpleIdResponseDto idResponseDto = new SimpleIdResponseDto();
            idResponseDto.setId(String.valueOf(taskExecution.getId()));
            return new ResponseEntity<>(idResponseDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }


    @PostMapping(path = "/schedule/directtask")
    public ResponseEntity<TaskExecutionLogResponseDTO> runTask(@RequestBody TaskRequestDTO taskRequestDTO) {
        Map<String, byte[]> parameters = new HashMap<>();
        taskRequestDTO.getParameters().forEach((parameterName, parameterValue) -> {
            parameters.put(parameterName, parameterValue.getBytes(StandardCharsets.UTF_8));
        });

        TaskRequest request = new TaskRequest(taskRequestDTO.getTaskName(), parameters);
        request.setChecksum(taskRequestDTO.getTaskName());
        TaskExecution execution = taskService.runRequest(request);

        TaskExecutionLogResponseDTO responseDTO = new TaskExecutionLogResponseDTO();
        if (execution != null) {
            for (TaskExecutionLog log : execution.getLogs()) {
                responseDTO.getOutput().add(log.getOutput());
            }

            return new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);

    }

    @PostMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookPost(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.POST);
    }

    @PutMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookPut(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.PUT);
    }

    @DeleteMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookDelete(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.DELETE);
    }

    @PatchMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookPatch(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.PATCH);
    }

    @GetMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookGet(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.GET);
    }

    @NotNull
    private ResponseEntity<TaskExecutionLogResponseDTO> runWebhookTrigger(Map<String, String> headers, String body, String taskName, String triggerName, String triggerSuffix, String path, HttpMethod httpMethod) {
        TaskRequest request = new TaskRequest(taskName, Collections.emptyMap());

        Map<String, String> triggerParameters = new HashMap<>(headers);
        triggerParameters.put("body", body);
        TriggerInfo triggerInfo = new WebhookTriggerInfo(triggerName, path, triggerSuffix, triggerParameters, httpMethod);
        request.setTriggerInfo(triggerInfo);

        TaskExecution execution = taskService.runRequest(request);

        //TODO: Initialize the trigger because we need to know if there is an authentication required.
        TaskExecutionLogResponseDTO responseDTO = new TaskExecutionLogResponseDTO();
        if (execution != null) {
            for (TaskExecutionLog log : execution.getLogs()) {
                responseDTO.getOutput().add(log.getOutput());
            }

            return new ResponseEntity<>(responseDTO, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @PostMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookPost(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookPost(headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @PutMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookPut(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookPut(headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @DeleteMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookDelete(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookDelete(headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @PatchMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookPatch(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookPatch(headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @GetMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<TaskExecutionLogResponseDTO> runWebhookGet(@RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookGet(headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @PostMapping(path = "/task/push")
    public ResponseEntity<TaskNameDTO> pushTask(@RequestBody TaskNameDTO taskNameDTO) {
        try {
            boolean pushed = taskService.pushTask(taskNameDTO.getTaskName());

            if (pushed) {
                return new ResponseEntity<>(taskNameDTO, HttpStatus.OK);
            }
        } catch (RuntimeException e) {
            TaskNameDTO errorMsg = new TaskNameDTO();
            errorMsg.setTaskName(e.getMessage());
            return new ResponseEntity<>(errorMsg, HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @PostMapping(path = "/task")
    public ResponseEntity<SimpleChecksumResponseDto> createTask(@RequestBody String body) throws IOException, NoSuchAlgorithmException {

        // Translate JSON to object
        ObjectMapper mapper = new ObjectMapper();
        TaskCreationDTO taskCreationDTO = mapper.readValue(body, TaskCreationDTO.class);

        // Generate compact JSON
        String compactJSON = mapper.writeValueAsString(taskCreationDTO);

        // Create checksum
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(compactJSON.getBytes(StandardCharsets.UTF_8));
        String sha3_256hex = bytesToHex(encodedhash);

        Long id = taskService.createTask(taskCreationDTO.getName(), taskCreationDTO.getParameters(), taskCreationDTO.getActions(), taskCreationDTO.getDescription(), taskCreationDTO.getConnections(), sha3_256hex, taskCreationDTO.isActive());

        if (id != null) {
            SimpleChecksumResponseDto checksumResponseDto = new SimpleChecksumResponseDto();
            checksumResponseDto.setChecksum(sha3_256hex);
            return new ResponseEntity<>(checksumResponseDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @PostMapping(path = "/task/action")
    public ResponseEntity<ActionResult> runAction(@RequestBody Action action) throws IOException {
        try {
            ActionResult actionResult = action.run(taskService, new TaskExecution(), new VariableExtractorUtil(new VelocityEngine()), (output, outputType) -> {
            });
            if (actionResult != null) {
                return new ResponseEntity<>(actionResult, HttpStatus.OK);
            }
        } catch (Exception e) {
            ActionResult actionResult = new ActionResult(StatusCode.FAILURE, "", e.toString());
            return new ResponseEntity<>(actionResult, HttpStatus.OK);
        }


        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/task")
    public ResponseEntity<List<TaskResponseSingleDTO>> getTasks() {
        List<Task> tasks = taskService.getAllTasks();
        List<TaskResponseSingleDTO> taskResponseDTOList = new ArrayList<>();

        for (Task task : tasks) {
            TaskResponseSingleDTO taskDto = new TaskResponseSingleDTO();
            taskDto.setChecksum(task.getChecksum());
            taskDto.setName(task.getName());
            taskDto.setCreated(task.getCreated());
            taskDto.setActive(task.isActive());
            taskDto.setParameters(task.getTaskParameterList());

            taskResponseDTOList.add(taskDto);
        }

        return new ResponseEntity<>(taskResponseDTOList, HttpStatus.OK);
    }

    @GetMapping("/task/grouped")
    public ResponseEntity<Map<String, List<TaskResponseSingleDTO>>> getTasksGrouped() {
        List<Task> tasks = taskService.getAllTasks();

        HashMap<String, List<TaskResponseSingleDTO>> groupedTasks = new HashMap<>();
        for (Task task : tasks) {
            TaskResponseSingleDTO taskDto = new TaskResponseSingleDTO();
            taskDto.setChecksum(task.getChecksum());
            taskDto.setName(task.getName());
            taskDto.setCreated(task.getCreated());
            taskDto.setActive(task.isActive());

            if (!groupedTasks.containsKey(task.getName())) {
                groupedTasks.put(task.getName(), new ArrayList<>());
            }

            groupedTasks.get(task.getName()).add(taskDto);
        }

        // Sort tasks
        groupedTasks.forEach((s, taskResponseSingleDTOS) -> {
            Collections.sort(taskResponseSingleDTOS, Comparator.comparing(TaskResponseSingleDTO::getCreated).reversed());
        });

        return new ResponseEntity<>(groupedTasks, HttpStatus.OK);
    }

    @GetMapping("/task/detail")
    public ResponseEntity<List<TaskResponseSingleDTO>> getDetailedTasks() {
        List<Task> tasks = taskService.getAllTasks();
        List<TaskResponseSingleDTO> taskResponseDTOList = new ArrayList<>();

        for (Task task : tasks) {
            TaskResponseSingleDTO taskDto = new TaskResponseSingleDTO();
            taskDto.setChecksum(task.getChecksum());
            taskDto.setName(task.getName());
            taskDto.setCreated(task.getCreated());
            taskDto.setActions(task.getActionList());
            taskDto.setParameters(task.getTaskParameterList());
            taskDto.setConnections(task.getConnections());
            taskDto.setActive(task.isActive());

            taskResponseDTOList.add(taskDto);
        }

        return new ResponseEntity<>(taskResponseDTOList, HttpStatus.OK);
    }

    @GetMapping("/task/{id}")
    public ResponseEntity<TaskResponseSingleDTO> getTask(@PathVariable("id") long id) {
        Task task = taskService.getTask(id);
        if (task != null) {
            TaskResponseSingleDTO taskDto = new TaskResponseSingleDTO();
            taskDto.setName(task.getName());
            taskDto.setDescription(task.getDescription());
            taskDto.setActions(task.getActionList());
            taskDto.setChecksum(task.getChecksum());
            taskDto.setParameters(task.getTaskParameterList());
            taskDto.setConnections(task.getConnections());
            taskDto.setActive(task.isActive());

            return new ResponseEntity<>(taskDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/task/{name}/taskname")
    public ResponseEntity<TaskResponseSingleDTO> getTask(@PathVariable("name") String id) {
        Task task = taskService.getTaskByName(id);
        if (task != null) {
            TaskResponseSingleDTO taskDto = new TaskResponseSingleDTO();
            taskDto.setName(task.getName());
            taskDto.setDescription(task.getDescription());
            taskDto.setActions(task.getActionList());
            taskDto.setChecksum(task.getChecksum());
            taskDto.setParameters(task.getTaskParameterList());
            taskDto.setConnections(task.getConnections());
            taskDto.setActive(task.isActive());

            return new ResponseEntity<>(taskDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/task/{checksum}/checksum")
    public ResponseEntity<TaskResponseSingleDTO> getTaskByChecksum(@PathVariable("checksum") String checksum) {
        Task task = taskService.getTaskByChecksum(checksum);
        if (task != null) {
            TaskResponseSingleDTO taskDto = new TaskResponseSingleDTO();
            taskDto.setName(task.getName());
            taskDto.setDescription(task.getDescription());
            taskDto.setActions(task.getActionList());
            taskDto.setChecksum(task.getChecksum());
            taskDto.setParameters(task.getTaskParameterList());
            taskDto.setConnections(task.getConnections());
            taskDto.setActive(task.isActive());


            return new ResponseEntity<>(taskDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/task/download")
    public ResponseEntity<SimpleChecksumResponseDto> pullTask(@RequestParam("taskName") String taskName) {
        Optional<Task> task = executionService.pullTask(taskName, taskService);
        if (task.isPresent()) {
            SimpleChecksumResponseDto checksumDto = new SimpleChecksumResponseDto();
            checksumDto.setChecksum(task.get().getChecksum());

            return new ResponseEntity<>(checksumDto, HttpStatus.OK);
        }
        SimpleChecksumResponseDto checksumResponseDto = new SimpleChecksumResponseDto();
        checksumResponseDto.setChecksum("Did not find task.");
        return new ResponseEntity<>(checksumResponseDto, HttpStatus.NOT_FOUND);
    }

    @GetMapping("/executions")
    public ResponseEntity<TaskExecutionListDTO> getExecutions() {
        List<TaskExecution> executions = taskService.getExecutions();
        List<TaskExecutionDTO> executionDTOList = new ArrayList<>();
        executions.forEach(item -> {
            TaskExecutionDTO dto = new TaskExecutionDTO();
            dto.setStatus(item.getStatus().name());
            dto.setStartDate(item.getStartdate());
            dto.setEndDate(item.getEnddate());
            dto.setExecutionId(Long.toString(item.getId()));
            dto.setTaskName(item.getTask().getName() != null ? item.getTask().getName() : Long.toString(item.getTask().getId()));
            executionDTOList.add(dto);
        });

        TaskExecutionListDTO executionListDTO = new TaskExecutionListDTO();
        executionListDTO.setExecutions(executionDTOList);

        return new ResponseEntity<>(executionListDTO, HttpStatus.OK);
    }

    @PostMapping("/login")
    public ResponseEntity<SimpleMessageResponseDTO> login(@RequestBody LoginParameters loginParameters) {

        boolean loggedIn = taskService.loginUser(loginParameters.getUsername(), loginParameters.getPassword());

        if (!loggedIn) {
            return new ResponseEntity<>(new SimpleMessageResponseDTO("Could not login. Check your credentials."), HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(new SimpleMessageResponseDTO("Successfully logged in"), HttpStatus.OK);
    }

    @DeleteMapping("/task/**")
    public ResponseEntity<SimpleMessageResponseDTO> deleteTask(HttpServletRequest request) {

        Task task = taskService.deleteTask(request.getRequestURI()
                .split(request.getContextPath() + "/task/")[1]);

        if (task != null) {
            SimpleMessageResponseDTO messageDto = new SimpleMessageResponseDTO("Task has been deleted " + task.getChecksum());

            return new ResponseEntity<>(messageDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(new SimpleMessageResponseDTO("Could not delete task"), HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/task/actions")
    public ResponseEntity<ActionListResponseDto> getAllActions() throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {


        Reflections reflections = new Reflections("com.dopplertask.dopplertask.domain.action");
        Set<Class<? extends Action>> classes = reflections.getSubTypesOf(Action.class);

        ActionListResponseDto actionListResponseDto = new ActionListResponseDto();
        for (Class<? extends Action> currentClass : classes) {
            if (Modifier.isAbstract(currentClass.getModifiers())) {
                continue;
            }
            Action instance = currentClass.getDeclaredConstructor().newInstance();
            boolean trigger = instance.getClass().getSuperclass().getSimpleName().equals(Trigger.class.getSimpleName()) ? true : false;
            actionListResponseDto.getActions().add(new ActionInfoDto(currentClass.getSimpleName(), instance.getDescription(), instance.getActionInfo(), trigger));
        }

        Collections.sort(actionListResponseDto.getActions(), (Comparator.comparing(ActionInfoDto::getName)));

        return new ResponseEntity<>(actionListResponseDto, HttpStatus.OK);
    }
}
