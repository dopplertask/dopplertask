package com.dopplertask.dopplertask.controller;

import com.dopplertask.dopplertask.domain.ActionResult;
import com.dopplertask.dopplertask.domain.Preview;
import com.dopplertask.dopplertask.domain.StepOutput;
import com.dopplertask.dopplertask.domain.ExecutionParameter;
import com.dopplertask.dopplertask.domain.StatusCode;
import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionLog;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.common.LoopAction;
import com.dopplertask.dopplertask.domain.action.common.LoopState;
import com.dopplertask.dopplertask.domain.action.trigger.Trigger;
import com.dopplertask.dopplertask.domain.action.StepTypes;
import com.dopplertask.dopplertask.dto.ActionInfoDto;
import com.dopplertask.dopplertask.dto.ActionTestRequestDTO;
import com.dopplertask.dopplertask.dto.ActionListResponseDto;
import com.dopplertask.dopplertask.dto.SimpleChecksumResponseDto;
import com.dopplertask.dopplertask.dto.SimpleIdResponseDto;
import com.dopplertask.dopplertask.dto.SimpleMessageResponseDTO;
import com.dopplertask.dopplertask.dto.TaskCreationDTO;
import com.dopplertask.dopplertask.dto.TaskExecutionDTO;
import com.dopplertask.dopplertask.dto.TaskExecutionListDTO;
import com.dopplertask.dopplertask.dto.TaskExecutionLogResponseDTO;
import com.dopplertask.dopplertask.dto.TaskRequestDTO;
import com.dopplertask.dopplertask.dto.TaskResponseSingleDTO;
import com.dopplertask.dopplertask.service.ExecutionService;
import com.dopplertask.dopplertask.service.HttpMethod;
import com.dopplertask.dopplertask.service.TaskRequest;
import com.dopplertask.dopplertask.service.TaskService;
import com.dopplertask.dopplertask.service.TriggerInfo;
import com.dopplertask.dopplertask.service.StepRules;
import com.dopplertask.dopplertask.service.VariableExtractorUtil;
import com.dopplertask.dopplertask.service.WebhookTriggerInfo;
import com.dopplertask.dopplertask.service.WebhookReply;
import com.dopplertask.dopplertask.service.CustomerScope;
import org.springframework.beans.factory.ObjectProvider;
import com.dopplertask.dopplertask.domain.action.common.WebhookReplyAction;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.jetbrains.annotations.NotNull;
import com.dopplertask.dopplertask.filter.AccessFilter;
import com.dopplertask.dopplertask.filter.Caller;
import com.dopplertask.dopplertask.service.ApprovalRequiredException;
import com.dopplertask.dopplertask.service.CredentialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TaskController {

    private static final Logger LOG = LoggerFactory.getLogger(TaskController.class);

    private final TaskService taskService;
    private final ExecutionService executionService;
    private final CredentialService credentialService;

    public TaskController(@Autowired TaskService taskService, @Autowired ExecutionService executionService, @Autowired CredentialService credentialService) {
        this.credentialService = credentialService;
        this.taskService = taskService;
        this.executionService = executionService;
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private void requireApprovedForAgent(TaskRequestDTO request, HttpServletRequest servletRequest) {
        if (AccessFilter.callerOf(servletRequest) == Caller.AGENT && (request.getTask() != null || taskService.getTaskByName(request.getTaskName()) == null)) {
            throw new ApprovalRequiredException("Task " + request.getTaskName() + " has no approved version yet. A person has to approve it before it can run.");
        }
    }

    @Autowired
    private VariableExtractorUtil variableExtractorUtil;

    @Autowired
    private ObjectProvider<CustomerScope> customerScope;

    // Webhook runs on a thread beside the request, for every Reply but the run log. All together kept below the 60
    // database connections, so that the editor and other runs keep some, and each customer scope to its own share, so
    // that one cannot take all of them.
    private static final int MAX_REPLYING_RUNS = 40;
    private final Semaphore replyingRuns = new Semaphore(MAX_REPLYING_RUNS);
    @Value("${dopplertask.maxReplyingRunsPerScope:40}")
    private int maxReplyingRunsPerScope;
    private final Map<String, Semaphore> replyingRunsOfScope = new ConcurrentHashMap<>();

    @PostMapping(path = "/schedule/task")
    public ResponseEntity<SimpleIdResponseDto> scheduleTask(@RequestBody TaskRequestDTO taskRequestDTO, HttpServletRequest servletRequest) {
        requireApprovedForAgent(taskRequestDTO, servletRequest);
        boolean removeTaskAfterExecution = false;
        String token = "_UNSAVED_" + (int) (Math.random() * 1000000);
        if (taskRequestDTO.getTask() != null) {
            credentialService.adoptInlineSecrets(taskRequestDTO.getTask().getActions(), AccessFilter.callerOf(servletRequest) == Caller.AGENT);
            this.taskService.createTask(taskRequestDTO.getTask().getName() + token, taskRequestDTO.getTask().getParameters(), taskRequestDTO.getTask().getActions(), taskRequestDTO.getTask().getDescription(), taskRequestDTO.getTask().getConnections(), taskRequestDTO.getTask().getName() + token, taskRequestDTO.getTask().isActive());
            removeTaskAfterExecution = true;
        }

        Map<String, ExecutionParameter> parameters = new HashMap<>();
        taskRequestDTO.getParameters().forEach((parameterName, parameterValue) -> parameters.put(parameterName, new ExecutionParameter(parameterName, parameterValue.getBytes(StandardCharsets.UTF_8), false)));

        TaskRequest request = new TaskRequest(removeTaskAfterExecution ? taskRequestDTO.getTaskName() + token : taskRequestDTO.getTaskName(), parameters, removeTaskAfterExecution);
        request.setChecksum(taskRequestDTO.getTaskName());
        request.setResumeFromExecutionId(taskRequestDTO.getResumeFromExecutionId());
        request.setPreview(taskRequestDTO.getPreview());
        request.setStartedByAgent(AccessFilter.callerOf(servletRequest) == Caller.AGENT);


        TaskExecution taskExecution = taskService.delegate(request);

        if (taskExecution != null) {
            SimpleIdResponseDto idResponseDto = new SimpleIdResponseDto();
            idResponseDto.setId(String.valueOf(taskExecution.getId()));
            return new ResponseEntity<>(idResponseDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }


    @PostMapping(path = "/schedule/directtask")
    public ResponseEntity<TaskExecutionLogResponseDTO> runTask(@RequestBody TaskRequestDTO taskRequestDTO, HttpServletRequest servletRequest) {
        requireApprovedForAgent(taskRequestDTO, servletRequest);
        Map<String, ExecutionParameter> parameters = new HashMap<>();
        taskRequestDTO.getParameters().forEach((parameterName, parameterValue) -> parameters.put(parameterName, new ExecutionParameter(parameterName, parameterValue.getBytes(StandardCharsets.UTF_8), false)));

        TaskRequest request = new TaskRequest(taskRequestDTO.getTaskName(), parameters);
        request.setChecksum(taskRequestDTO.getTaskName());
        request.setResumeFromExecutionId(taskRequestDTO.getResumeFromExecutionId());
        request.setPreview(taskRequestDTO.getPreview());
        request.setStartedByAgent(AccessFilter.callerOf(servletRequest) == Caller.AGENT);
        TaskExecution execution = taskService.runRequest(request);
        ResponseEntity<TaskExecutionLogResponseDTO> result = runResult(execution, taskRequestDTO.getTaskName());
        if (execution != null) {
            result.getBody().setError(execution.getFailureReason());
            execution.getLogs().stream().map(TaskExecutionLog::getOutput)
                    .filter(output -> output != null && !output.isEmpty())
                    .forEach(result.getBody().getLogs()::add);
        }
        return result;
    }

    @PostMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<?> runWebhookPost(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.POST);
    }

    @PutMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<?> runWebhookPut(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.PUT);
    }

    @DeleteMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<?> runWebhookDelete(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.DELETE);
    }

    @PatchMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<?> runWebhookPatch(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.PATCH);
    }

    @GetMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}/{path}")
    public ResponseEntity<?> runWebhookGet(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix, @PathVariable("path") String path) {
        return runWebhookTrigger(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, path, HttpMethod.GET);
    }

    @NotNull
    private ResponseEntity<?> runWebhookTrigger(Map<String, String> allRequestParams, Map<String, String> headers, String body, String taskName, String triggerName, String triggerSuffix, String path, HttpMethod httpMethod) {
        TaskRequest request = new TaskRequest(taskName, Collections.emptyMap());

        Map<String, String> triggerParameters = new HashMap<>(headers);
        triggerParameters.put("body", body);
        allRequestParams.forEach((key, value) -> triggerParameters.put("query_" + key, value));

        TriggerInfo triggerInfo = new WebhookTriggerInfo(triggerName, path, triggerSuffix, triggerParameters, httpMethod);
        request.setTriggerInfo(triggerInfo);

        String mode = taskService.webhookReplyMode(taskName, triggerName, triggerSuffix, path, httpMethod.name());
        if (mode == null || mode.isBlank() || WebhookReply.RUN_LOG.equals(mode)) {
            // A Reply to webhook step still learns that this call waits for the run log, not for it.
            ((WebhookTriggerInfo) triggerInfo).setReply(new WebhookReply(WebhookReply.RUN_LOG));
            return runResult(taskService.runRequest(request), taskName);
        }
        return runAndReply(request, (WebhookTriggerInfo) triggerInfo, mode, taskName);
    }

    /**
     * Runs the task beside the request, so that the caller can be answered before it ends: at once, by a Reply to webhook
     * step, or by what the last step gave. A failure gives only the status and the run id, since anyone can call a webhook,
     * and the reason stays in the editor.
     */
    private ResponseEntity<?> runAndReply(TaskRequest request, WebhookTriggerInfo triggerInfo, String mode, String taskName) {
        // A full server answers at once rather than holding a request thread, so that the editor stays reachable.
        CustomerScope scope = customerScope.getIfAvailable(() -> CustomerScope.ONE_CUSTOMER);
        Semaphore ownPlaces = replyingRunsOfScope.computeIfAbsent(scope.current(), name -> new Semaphore(maxReplyingRunsPerScope));
        if (!ownPlaces.tryAcquire()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "10").body("Too many webhook calls are running for this task's owner. Try again in a moment.");
        }
        if (!replyingRuns.tryAcquire()) {
            ownPlaces.release();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header(HttpHeaders.RETRY_AFTER, "10").body("The server is busy with other webhook calls. Try again in a moment.");
        }
        WebhookReply reply = new WebhookReply(mode);
        triggerInfo.setReply(reply);
        CompletableFuture<TaskExecution> ended = new CompletableFuture<>();
        try {
            Thread.ofVirtual().name("webhook-" + taskName).start(scope.bound(() -> {
                try {
                    ended.complete(taskService.runRequest(request));
                } catch (RuntimeException | Error e) {
                    LOG.error("A webhook run of {} stopped on an unexpected problem", taskName, e);
                    ended.completeExceptionally(e);
                } finally {
                    replyingRuns.release();
                    ownPlaces.release();
                }
            }));
        } catch (RuntimeException | Error e) {
            replyingRuns.release();
            ownPlaces.release();
            throw e;
        }
        try {
            CompletableFuture.anyOf(reply.answered(), ended).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("The server stopped before the task answered.");
        } catch (ExecutionException e) {
            return failed(null);
        }
        if (reply.isGiven()) {
            return answerOf(reply.answered().join());
        }
        TaskExecution execution = ended.join();
        if (execution == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("There is no task called " + taskName + ".");
        }
        if (execution.getStatus() == TaskExecutionStatus.FAILED) {
            return failed(execution.getId());
        }
        // The run continues later, when someone answers, so there is no reply yet.
        if (execution.getStatus() == TaskExecutionStatus.WAITING) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body("The run waits for an answer at one of its steps.");
        }
        if (WebhookReply.LAST_STEP.equals(mode)) {
            String output = reply.getLastOutput() == null ? "" : reply.getLastOutput();
            int status = WebhookReplyAction.statusOf(taskService.webhookReplyCode(taskName, triggerInfo.getTriggerName(), triggerInfo.getTriggerSuffix(), triggerInfo.getPath(), triggerInfo.getMethod().name()), 200);
            return answerOf(new WebhookReply.Answer(status, Map.of("Content-Type", WebhookReplyAction.contentTypeOf(output)), output));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("The task ended without a Reply to webhook step, so there was nothing to answer with.");
    }

    private static ResponseEntity<TaskExecutionLogResponseDTO> failed(Long executionId) {
        TaskExecutionLogResponseDTO answer = new TaskExecutionLogResponseDTO();
        answer.setStatus(TaskExecutionStatus.FAILED.name());
        answer.setExecutionId(executionId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(answer);
    }

    private ResponseEntity<?> answerOf(WebhookReply.Answer answer) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(answer.getStatus());
        answer.getHeaders().forEach(builder::header);
        return answer.getBody() == null ? builder.build() : builder.body(answer.getBody());
    }

    private ResponseEntity<TaskExecutionLogResponseDTO> runResult(TaskExecution execution, String taskName) {
        TaskExecutionLogResponseDTO responseDTO = new TaskExecutionLogResponseDTO();
        if (execution == null) {
            responseDTO.setStatus(TaskExecutionStatus.FAILED.name());
            responseDTO.setError("Task could not be found [name=" + taskName + "]");
            return new ResponseEntity<>(responseDTO, HttpStatus.BAD_REQUEST);
        }

        int i = 0;
        int logSize = execution.getLogs().size();
        for (TaskExecutionLog log : execution.getLogs()) {
            if(i != 0 && log.isBroadcasted() && i != (logSize-1)) {
                responseDTO.getOutput().add(log.getOutput());
            }
            i++;
        }
        responseDTO.setStatus(execution.getStatus().name());
        responseDTO.setExecutionId(execution.getId());

        return new ResponseEntity<>(responseDTO, HttpStatus.OK);
    }

    @PostMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<?> runWebhookPost(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookPost(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @PutMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<?> runWebhookPut(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookPut(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @DeleteMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<?> runWebhookDelete(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookDelete(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @PatchMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<?> runWebhookPatch(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookPatch(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @GetMapping(path = "/webhook/{taskName}/{triggerName}/{triggerSuffix}")
    public ResponseEntity<?> runWebhookGet(@RequestParam Map<String, String> allRequestParams, @RequestHeader(required = false) Map<String, String> headers, @RequestBody(required = false) String body, @PathVariable("taskName") String taskName, @PathVariable("triggerName") String triggerName, @PathVariable("triggerSuffix") String triggerSuffix) {
        return runWebhookGet(allRequestParams, headers, body, taskName, triggerName, triggerSuffix, "");

    }

    @PostMapping(path = "/task")
    public ResponseEntity<SimpleChecksumResponseDto> createTask(@RequestBody String body, @RequestParam(defaultValue = "false") boolean autoSaved, HttpServletRequest servletRequest) throws IOException, NoSuchAlgorithmException {

        // Translate JSON to object
        boolean byAgent = AccessFilter.callerOf(servletRequest) == Caller.AGENT;
        TaskCreationDTO taskCreationDTO = TaskCreationDTO.fromJson(body);
        credentialService.adoptInlineSecrets(taskCreationDTO.getActions(), byAgent);

        // Generate compact JSON
        String compactJSON = taskCreationDTO.toJson();

        // Create checksum
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(compactJSON.getBytes(StandardCharsets.UTF_8));
        String sha256Hex = bytesToHex(encodedhash);

        if (byAgent && taskCreationDTO.isActive()) {
            throw new ApprovalRequiredException("An agent cannot turn a task on. Save it with active set to false, and a person can turn it on.");
        }
        Long id = byAgent
                ? taskService.proposeTask(taskCreationDTO.getName(), taskCreationDTO.getParameters(), taskCreationDTO.getActions(), taskCreationDTO.getDescription(), taskCreationDTO.getConnections(), sha256Hex)
                : taskService.createTask(taskCreationDTO.getName(), taskCreationDTO.getParameters(), taskCreationDTO.getActions(), taskCreationDTO.getDescription(), taskCreationDTO.getConnections(), sha256Hex, taskCreationDTO.isActive(), autoSaved);

        if (id != null) {
            SimpleChecksumResponseDto checksumResponseDto = new SimpleChecksumResponseDto();
            checksumResponseDto.setChecksum(sha256Hex);
            return new ResponseEntity<>(checksumResponseDto, HttpStatus.OK);
        }

        return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @PostMapping(path = "/task/{checksum}/approve")
    public ResponseEntity<SimpleChecksumResponseDto> approveTask(@PathVariable("checksum") String checksum, HttpServletRequest request) {
        String approvedBy = AccessFilter.personOf(request).orElse(null);
        if (!taskService.approveTask(checksum, approvedBy)) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        SimpleChecksumResponseDto response = new SimpleChecksumResponseDto();
        response.setChecksum(checksum);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping(path = "/task/action")
    public ResponseEntity<ActionResult> runAction(@RequestBody Action action) throws IOException {
        return runAction(action, new TaskExecution());
    }

    /**
     * Runs one action with the values it would have seen in a task, without saving an execution.
     */
    @PostMapping(path = "/task/action/test")
    public ResponseEntity<ActionResult> testAction(@RequestBody ActionTestRequestDTO request) throws IOException {
        TaskExecution execution = new TaskExecution();
        request.getParameters().forEach((name, value) -> execution.getParameters().put(name, new ExecutionParameter(name, value.getBytes(StandardCharsets.UTF_8), false)));
        TaskExecutionLog lastLog = new TaskExecutionLog();
        lastLog.setOutput(request.getLastOutput());
        lastLog.setOutputVariables(request.getLastOutputVariables());
        execution.addLog(lastLog);
        request.getSteps().forEach((stepName, given) -> execution.getStepOutputs().put(stepName, new StepOutput(given.getOutput(), given.getOutputVariables())));
        if (request.isPreview()) {
            execution.setPreview(new Preview());
        }
        if (request.getLoopItem() != null) {
            // Only the item of this pass is known, so the other places in the list stay empty.
            List<Object> items = new ArrayList<>(Collections.nCopies(Math.max(request.getLoopCount(), request.getLoopIndex() + 1), null));
            items.set(request.getLoopIndex(), LoopAction.valueOf(request.getLoopItem()));
            LoopState pass = new LoopState("test", "Loop", items, 1, request.getLoopIndex(), -1, new ArrayList<>());
            pass.next();
            execution.startLoop(pass);
        }
        ResponseEntity<ActionResult> answer = runAction(request.getAction(), execution);
        if (answer.getBody() != null) {
            answer.getBody().setMissingValues(execution.takeMissingValues());
        }
        return answer;
    }

    private ResponseEntity<ActionResult> runAction(Action action, TaskExecution execution) throws IOException {
        try {
            ActionResult actionResult = credentialService.runWith(action, execution, () -> action.run(taskService, execution, variableExtractorUtil, (output, outputType) -> {
            }));
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
            taskDto.setDescription(task.getDescription());
            taskDto.setAutoSaved(task.isAutoSaved());
            taskDto.setProposed(task.isProposed());
            taskDto.setMadeByAgent(task.isMadeByAgent());
            taskDto.setApprovedBy(task.getApprovedBy());

            if (!groupedTasks.containsKey(task.getName())) {
                groupedTasks.put(task.getName(), new ArrayList<>());
            }

            groupedTasks.get(task.getName()).add(taskDto);
        }

        // Sort tasks
        groupedTasks.forEach((s, taskResponseSingleDTOS) -> Collections.sort(taskResponseSingleDTOS, Comparator.comparing(TaskResponseSingleDTO::getCreated).reversed()));

        return new ResponseEntity<>(groupedTasks, HttpStatus.OK);
    }

    private static ResponseEntity<TaskResponseSingleDTO> singleTask(Task task) {
        if (task == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
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

    @GetMapping("/task/{name}/taskname")
    public ResponseEntity<TaskResponseSingleDTO> getTask(@PathVariable("name") String id) {
        Task task = taskService.getTaskByName(id);
        return singleTask(task);
    }

    @GetMapping("/task/{checksum}/checksum")
    public ResponseEntity<TaskResponseSingleDTO> getTaskByChecksum(@PathVariable("checksum") String checksum) {
        Task task = taskService.getTaskByChecksum(checksum);
        return singleTask(task);
    }

    @GetMapping("/executions")
    public ResponseEntity<TaskExecutionListDTO> getExecutions(@RequestParam(required = false) String taskName, @RequestParam(defaultValue = "100") int limit) {
        List<TaskExecution> executions = taskName == null ? taskService.getExecutions(limit) : taskService.getExecutions(taskName, limit);
        List<TaskExecutionDTO> executionDTOList = new ArrayList<>();
        executions.forEach(item -> {
            TaskExecutionDTO dto = new TaskExecutionDTO();
            dto.setStatus(item.getStatus().name());
            dto.setStartDate(item.getStartdate());
            dto.setEndDate(item.getEnddate());
            dto.setExecutionId(Long.toString(item.getId()));
            dto.setStartedByAgent(item.isStartedByAgent());
            dto.setPreview(item.getPreview() != null);
            dto.setTaskName(item.getTask().getName() != null ? item.getTask().getName() : Long.toString(item.getTask().getId()));
            executionDTOList.add(dto);
        });

        TaskExecutionListDTO executionListDTO = new TaskExecutionListDTO();
        executionListDTO.setExecutions(executionDTOList);

        return new ResponseEntity<>(executionListDTO, HttpStatus.OK);
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
    public ResponseEntity<ActionListResponseDto> getAllActions() {
        ActionListResponseDto actionListResponseDto = new ActionListResponseDto();
        for (Action step : StepTypes.INSTANCE.all()) {
            if (StepRules.active().offers(step.getClass())) {
                String note = StepRules.active().noteOn(step.getClass());
                String description = note == null ? step.getDescription() : step.getDescription() + ". " + note;
                actionListResponseDto.getActions().add(new ActionInfoDto(step.getStepTypeName(), step.getStepLabel(), step.getStepIcon(), description,
                        step.getActionInfo(), step instanceof Trigger, step.getOutputs(), step.getCredentialType()));
            }
        }

        Collections.sort(actionListResponseDto.getActions(), (Comparator.comparing(ActionInfoDto::name)));

        return new ResponseEntity<>(actionListResponseDto, HttpStatus.OK);
    }
}
