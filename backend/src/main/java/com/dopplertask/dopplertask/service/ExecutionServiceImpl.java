package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.dao.TaskDao;
import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.*;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.dto.TaskCreationDTO;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Service
public class ExecutionServiceImpl implements ExecutionService {


    public static final String CHECKSUM_ALGORITHM = "SHA-256";
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionServiceImpl.class);
    private static final String DOPPLERTASK_WORKFLOW_DOWNLOAD = "https://www.dopplertask.com/getworkflow.php";
    private static final int MAX_ACTION_ACCESS_LIMIT = 2000;
    private static final String TRIGGER_PARAMETER_PREFIX = "TRIGGER";

    private JmsTemplate jmsTemplate;
    private TaskDao taskDao;
    private VariableExtractorUtil variableExtractorUtil;
    private TaskExecutionDao taskExecutionDao;
    private Executor executor;

    @Autowired
    public ExecutionServiceImpl(JmsTemplate jmsTemplate, TaskDao taskDao, VariableExtractorUtil variableExtractorUtil, TaskExecutionDao taskExecutionDao) {
        this.jmsTemplate = jmsTemplate;
        this.taskDao = taskDao;
        this.variableExtractorUtil = variableExtractorUtil;
        this.taskExecutionDao = taskExecutionDao;
        this.executor = Executors.newSingleThreadExecutor();
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

    @Transactional
    public TaskExecution startExecution(TaskExecutionRequest taskExecutionRequest, TaskService taskService, boolean startedByTrigger) {
        // Look up by checksum first
        Optional<Task> taskRequest = lookupTask(taskExecutionRequest, taskService);

        Optional<TaskExecution> executionReq = taskExecutionDao.findById(taskExecutionRequest.getExecutionId());
        if (taskRequest.isPresent() && executionReq.isPresent()) {
            Task task = taskRequest.get();
            TaskExecution execution = executionReq.get();

            // Assign task to execution
            execution.setTask(task);

            // Populate variables
            execution.getParameters().putAll(taskExecutionRequest.getParameters());

            execution.setStartdate(new Date());

            // Check if task is started by trigger when the active is set to false, if so, fail.
            if (startedByTrigger && !task.isActive()) {
                execution.setSuccess(false);
                execution.setStatus(TaskExecutionStatus.FAILED);
                addLog(execution, "Task is not active. Please set task to active to respond to webhooks", OutputType.STRING, Map.of(), true, false);
                return execution;
            }

            // Check that all required parameters are present
            List<String> missingParameters = new ArrayList<>();
            for (TaskParameter taskParameter : task.getTaskParameterList()) {
                if (taskParameter.isRequired() && execution.getParameters().get(taskParameter.getName()) == null) {
                    missingParameters.add(taskParameter.getName());
                } else if (execution.getParameters().get(taskParameter.getName()) == null && taskParameter.getDefaultValue() != null) {
                    // Add default value to parameter if it exists
                    execution.getParameters().put(taskParameter.getName(), new ExecutionParameter(taskParameter.getName(), taskParameter.getDefaultValue().getBytes(StandardCharsets.UTF_8), false));
                }
            }

            if (!missingParameters.isEmpty()) {
                execution.setStatus(TaskExecutionStatus.FAILED);
                String missingParametersAsString = Arrays.toString(missingParameters.toArray());
                TaskExecutionLog executionFailed = new TaskExecutionLog();
                executionFailed.setTaskExecution(execution);
                executionFailed.setOutput("Task execution failed, missing parameters: " + missingParametersAsString + " [taskId=" + task.getId() + ", executionId=" + execution.getId() + "]");
                execution.addLog(executionFailed);
                execution.setSuccess(false);
                LOG.info("Task execution failed, missing parameters: {} [taskId={}, executionId={}]", missingParametersAsString, task.getId(), execution.getId());
                broadcastResults(executionFailed, true);

                return execution;
            }

            execution.setStatus(TaskExecutionStatus.STARTED);

            TaskExecutionLog executionStarted = new TaskExecutionLog();
            executionStarted.setTaskExecution(execution);
            executionStarted.setOutput("Task execution started [taskId=" + task.getId() + ", executionId=" + execution.getId() + "]");
            execution.addLog(executionStarted);

            taskExecutionDao.save(execution);

            LOG.info("Task execution started [taskId={}, executionId={}]", task.getId(), execution.getId());

            broadcastResults(executionStarted);

            return execution;
        } else {
            LOG.warn("Task could not be found [taskId={}]", taskExecutionRequest.getTaskName());

            if (executionReq.isPresent()) {
                TaskExecution taskExecution = executionReq.get();
                taskExecution.setId(executionReq.get().getId());
                taskExecution.setSuccess(false);
                TaskExecutionLog noTaskLog = new TaskExecutionLog();
                noTaskLog.setDate(new Date());
                noTaskLog.setOutput("Task could not be found [taskId=" + taskExecutionRequest.getTaskName() + "]");
                noTaskLog.setTaskExecution(taskExecution);
                broadcastResults(noTaskLog, true);
            }
            return null;
        }
    }

    private Optional<Task> lookupTask(TaskExecutionRequest taskExecutionRequest, TaskService taskService) {
        Optional<Task> taskRequest = Optional.empty();
        if (taskExecutionRequest.getChecksum() != null && !taskExecutionRequest.getChecksum().isEmpty()) {
            taskRequest = findOrDownloadByChecksum(taskExecutionRequest.getChecksum(), taskService);

            // Search in the local database by taskName
            if (!taskRequest.isPresent() && taskExecutionRequest.getTaskName() != null && !taskExecutionRequest.getTaskName().isEmpty()) {
                taskRequest = findOrDownloadByName(taskExecutionRequest.getTaskName(), taskService);
            }
        } else if (taskExecutionRequest.getTaskName() != null && !taskExecutionRequest.getTaskName().isEmpty()) {
            taskRequest = findOrDownloadByName(taskExecutionRequest.getTaskName(), taskService);
        }
        return taskRequest;
    }

    public Optional<Task> findOrDownloadByName(String taskName, TaskService taskService) {
        Optional<Task> task = taskDao.findFirstByNameOrderByCreatedDesc(taskName);

        if (task.isPresent()) {
            LOG.info("Found task with with name: {}", taskName);
            return task;
        } else {
            // Try to download it
            LOG.info("Trying to download task with name: {}", taskName);

            // Persist with checksum
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(DOPPLERTASK_WORKFLOW_DOWNLOAD + "?name=" + taskName))
                    .timeout(Duration.ofMinutes(1));
            builder = builder.GET();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = builder.build();
            try {
                // Get JSON from Hub
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty() && taskService != null) {

                    // Translate JSON to object
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                    TaskCreationDTO taskCreationDTO = mapper.readValue(response.body(), TaskCreationDTO.class);

                    // Create checksum
                    MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
                    byte[] encodedhash = digest.digest(response.body().getBytes(StandardCharsets.UTF_8));
                    String sha256 = bytesToHex(encodedhash);

                    //TODO: check that there is no other checksum with the same value in the DB
                    Long onlineTaskId = taskService.createTask(taskCreationDTO.getName(), taskCreationDTO.getParameters(), taskCreationDTO.getActions(), taskCreationDTO.getDescription(), taskCreationDTO.getConnections(), sha256, taskCreationDTO.isActive());

                    return taskDao.findById(onlineTaskId);
                }
            } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
                LOG.error("Exception: {}", e);
            }

            return Optional.empty();
        }

    }

    @Override
    public Optional<TaskExecution> getExecution(long id) {
        return taskExecutionDao.findById(id);
    }

    @Override
    @Transactional
    public void deleteExecution(long id) {
        Optional<TaskExecution> execution = taskExecutionDao.findById(id);
        if (execution.isPresent()) {
            taskExecutionDao.delete(execution.get());
            return;
        }

        throw new ExecutionNotFoundException("Execution could not be found.");
    }

    @Override
    public Optional<Task> pullTask(String taskName, TaskService taskService) {
        // Try to download it
        LOG.info("Pulling task with name: {}", taskName);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(DOPPLERTASK_WORKFLOW_DOWNLOAD + "?name=" + taskName))
                .timeout(Duration.ofMinutes(1));
        builder = builder.GET();
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = builder.build();
        try {
            // Get JSON from Hub
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty() && taskService != null) {
                // Translate JSON to object
                ObjectMapper mapper = new ObjectMapper();
                mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                TaskCreationDTO taskCreationDTO = mapper.readValue(response.body(), TaskCreationDTO.class);

                // Create checksum
                MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
                byte[] encodedhash = digest.digest(response.body().getBytes(StandardCharsets.UTF_8));
                String sha256 = bytesToHex(encodedhash);

                // Check current database for existing task with checksum.
                Optional<Task> existingTask = taskDao.findFirstByChecksumStartingWith(sha256);
                if (!existingTask.isPresent()) {
                    Long onlineTaskId = taskService.createTask(taskCreationDTO.getName(), taskCreationDTO.getParameters(), taskCreationDTO.getActions(), taskCreationDTO.getDescription(), taskCreationDTO.getConnections(), sha256, taskCreationDTO.isActive(), false);
                    return taskDao.findById(onlineTaskId);
                } else {
                    return existingTask;
                }
            }
        } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
            LOG.error("Exception: {}", e);
        }


        return taskDao.findFirstByNameOrderByCreatedDesc(taskName);
    }

    private Optional<Task> findOrDownloadByChecksum(String checksum, TaskService taskService) {
        Optional<Task> task = taskDao.findFirstByChecksumStartingWith(checksum);

        if (task.isPresent()) {
            LOG.info("Found task with checksum: {}", checksum);
            return task;
        } else if (!checksum.isEmpty() && checksum.length() == 255) {
            // Try to download it
            LOG.info("Trying to download task with checksum: {}", checksum);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(DOPPLERTASK_WORKFLOW_DOWNLOAD + "?checksum=" + checksum))
                    .timeout(Duration.ofMinutes(1));
            builder = builder.GET();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = builder.build();
            try {
                // Get JSON from Hub
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty() && taskService != null) {

                    // Translate JSON to object
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                    TaskCreationDTO taskCreationDTO = mapper.readValue(response.body(), TaskCreationDTO.class);

                    // Create checksum
                    MessageDigest digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
                    byte[] encodedhash = digest.digest(response.body().getBytes(StandardCharsets.UTF_8));
                    String sha256 = bytesToHex(encodedhash);

                    Long onlineTaskId = taskService.createTask(taskCreationDTO.getName(), taskCreationDTO.getParameters(), taskCreationDTO.getActions(), taskCreationDTO.getDescription(), taskCreationDTO.getConnections(), sha256, taskCreationDTO.isActive());

                    return taskDao.findById(onlineTaskId);
                }
            } catch (IOException | InterruptedException | NoSuchAlgorithmException e) {
                LOG.error("Exception: {}", e);
            }

            return Optional.empty();
        }
        return Optional.empty();
    }

    @Override
    @Transactional
    public TaskExecution processActions(Long taskId, Long executionId, TaskService taskService) {
        Optional<Task> taskRequest = taskDao.findById(taskId);
        Optional<TaskExecution> executionReq = taskExecutionDao.findById(executionId);
        if (taskRequest.isPresent() && executionReq.isPresent()) {
            Task task = taskRequest.get();
            TaskExecution execution = executionReq.get();

            execution.setCurrentAction(task.getWebhookStartAction());

            return executeActions(taskService, task, execution);
        }
        return null;
    }

    @Override
    @Transactional
    public TaskExecution processActions(Long taskId, Long executionId, TaskService taskService, TriggerInfo triggerInfo) {
        Optional<Task> taskRequest = taskDao.findById(taskId);
        Optional<TaskExecution> executionReq = taskExecutionDao.findById(executionId);
        if (taskRequest.isPresent() && executionReq.isPresent()) {
            Task task = taskRequest.get();
            TaskExecution execution = executionReq.get();

            Action action;
            // Check if this is a general trigger or a webhook
            if (triggerInfo instanceof WebhookTriggerInfo info) {
                action = task.getWebhookStartAction(info.getTriggerName(), info.getTriggerSuffix(), info.getPath(), info.getMethod().name());
            } else {
                action = task.getStartAction(triggerInfo.getTriggerName(), triggerInfo.getTriggerSuffix());
            }


            // Check if the trigger exists
            if (action == null) {
                addLog(execution, "The selected trigger or webhook was not found. Cannot start task", OutputType.STRING, Map.of(), true);
                return execution;
            }

            // Set params
            if (triggerInfo.getTriggerParameters() != null) {
                triggerInfo.getTriggerParameters().forEach((key, value) -> execution.getParameters().put(TRIGGER_PARAMETER_PREFIX + "_" + key, new ExecutionParameter(TRIGGER_PARAMETER_PREFIX + "_" + key, value != null ? value.getBytes(StandardCharsets.UTF_8) : new byte[0], false)));
            }

            execution.setCurrentAction(action);

            return executeActions(taskService, task, execution);
        }
        return null;
    }

    private TaskExecution executeActions(TaskService taskService, Task task, TaskExecution execution) {
        boolean running = true;
        if (execution.getCurrentAction() != null) {
            while (running) {
                // Start processing task
                Action currentAction = execution.getCurrentAction();

                // Add count to action
                execution.addActionAccessCountByOne(currentAction.getId());

                // Prevent overflood (Hard limit)
                if (checkActionOverfloodAccess(execution, currentAction)) {
                    break;
                }

                // If output port does not have a connection then we've reached the end of the execution.
                boolean outputPortAvailable = currentAction.getOutputPorts() != null && !currentAction.getOutputPorts().isEmpty() && currentAction.getOutputPorts().get(0) != null;
                if (currentAction.getOutputPorts() == null || currentAction.getOutputPorts() != null && currentAction.getOutputPorts().isEmpty() || outputPortAvailable && currentAction.getOutputPorts().get(0).getConnectionSource() == null) {
                    running = false;
                } else {
                    // There is a port so lets pick the first one.
                    ActionPort targetPort = currentAction.getOutputPorts().get(0).getConnectionSource().getTarget();
                    Action nextAction = targetPort.getAction();
                    execution.setCurrentAction(nextAction);
                }

                ActionResult actionResult = runAction(taskService, execution, currentAction);

                LOG.info("Ran current action: {} with status code: {} and with result: {}", currentAction.getClass().getSimpleName(), actionResult.getStatusCode(), actionResult.getOutput() != null && !actionResult.getOutput().isEmpty() ? actionResult.getOutput() : actionResult.getErrorMsg());
                broadcastResults(execution.getId(), "Ran current action: " + currentAction.getClass().getSimpleName() + " with status code: " + actionResult.getStatusCode(), actionResult.getOutputType());

                // If action did not go well
                if (actionResult.getStatusCode() == StatusCode.FAILURE && !currentAction.isContinueOnFailure()) {
                    addLog(execution, actionResult.getErrorMsg(), actionResult.getOutputType(), actionResult.getOutputVariables(), true, false, false);
                    break;
                }


            }
        }

        TaskExecutionLog executionCompleted = new TaskExecutionLog();
        executionCompleted.setTaskExecution(execution);
        executionCompleted.setOutput("Task execution completed [taskId=" + task.getId() + ", executionId=" + execution.getId() + ", success=" + execution.isSuccess() + "]");
        execution.addLog(executionCompleted);
        broadcastResults(executionCompleted, true);

        LOG.info("Task execution completed [taskId={}, executionId={}]", task.getId(), execution.getId());

        execution.setEnddate(new Date());
        execution.setStatus(execution.isSuccess() ? TaskExecutionStatus.FINISHED : TaskExecutionStatus.FAILED);

        return execution;
    }

    private void broadcastResults(Long executionId, String output, OutputType outputType) {
        BroadcastResult result = new BroadcastResult(output, outputType);

        executor.execute(() -> jmsTemplate.convertAndSend("taskexecution_destination", result, message -> {
            message.setLongProperty("executionId", executionId);
            message.setBooleanProperty("lastMessage", false);
            return message;
        }));
    }

    private ActionResult runAction(TaskService taskService, TaskExecution execution, Action currentAction) {
        ActionResult actionResult = new ActionResult();

        int tries = 0;
        do {
            try {
                // Only wait if we are in a retry.
                if (tries > 0) {
                    String retryWaitAmount = variableExtractorUtil.extract(currentAction.getRetryWait(), execution, null, currentAction.getScriptLanguage(), Map.of("retryCount", tries + 1));
                    Thread.sleep(Long.parseLong(retryWaitAmount));
                }
                actionResult = currentAction.run(taskService, execution, variableExtractorUtil, (output, outputType) -> addLog(execution, output, outputType, Map.of(), true));

                if (!actionResult.getOutput().isEmpty()) {
                    addLog(execution, actionResult.getOutput(), actionResult.getOutputType(), actionResult.getOutputVariables(), actionResult.isBroadcastMessage());
                } else {
                    addLog(execution, actionResult.getErrorMsg(), actionResult.getOutputType(), actionResult.getOutputVariables(), actionResult.isBroadcastMessage());
                }
                // Handle failOn
                if (currentAction.getFailOn() != null && !currentAction.getFailOn().isEmpty()) {
                    String failOn = variableExtractorUtil.extract(currentAction.getFailOn(), execution, actionResult, currentAction.getScriptLanguage(), Map.of("retryCount", tries + 1));
                    if (failOn != null && !failOn.isEmpty()) {
                        actionResult.setErrorMsg("Failed on: " + failOn);
                        actionResult.setStatusCode(StatusCode.FAILURE);

                        addLog(execution, actionResult.getErrorMsg(), actionResult.getOutputType(), actionResult.getOutputVariables(), true);
                    }
                }
            } catch (Exception e) {
                LOG.error("Exception occurred: {}", e);
                actionResult.setErrorMsg(e.toString());
                actionResult.setStatusCode(StatusCode.FAILURE);

                addLog(execution, actionResult.getOutput(), actionResult.getOutputType(), actionResult.getOutputVariables(), true);
            }

            tries++;
        } while (actionResult.getStatusCode() == StatusCode.FAILURE && currentAction.getRetries() >= tries && !currentAction.isContinueOnFailure());
        return actionResult;
    }

    private boolean checkActionOverfloodAccess(TaskExecution execution, Action currentAction) {
        if (execution.getActionAccessCount(currentAction.getId()) > MAX_ACTION_ACCESS_LIMIT) {
            addLog(execution, "Action access amount has reached it's maximum limit. Please consider using less loops." + currentAction.getClass().getName(), OutputType.STRING, Map.of(), true, false, false);
            return true;
        }
        return false;
    }

    public TaskExecutionLog addLog(TaskExecution execution, String output, OutputType outputType, Map<String, Object> outputVariables, boolean broadcastLog, boolean success, boolean lastBroadcastMessage) {
        TaskExecutionLog log = new TaskExecutionLog();
        log.setTaskExecution(execution);

        log.setOutput(output);
        log.setOutputType(outputType);
        log.setDate(new Date());
        log.setOutputVariables(outputVariables);

        execution.setSuccess(success);

        // Add log to the execution
        execution.addLog(log);

        if (broadcastLog) {
            broadcastResults(log, lastBroadcastMessage);
        }

        return log;
    }

    public TaskExecutionLog addLog(TaskExecution execution, String output, OutputType outputType, Map<String, Object> outputVariables, boolean broadcastLog, boolean success) {
        return addLog(execution, output, outputType, outputVariables, broadcastLog, success, false);
    }

    public TaskExecutionLog addLog(TaskExecution execution, String output, OutputType outputType, Map<String, Object> outputVariables, boolean broadcastLog) {
        return addLog(execution, output, outputType, outputVariables, broadcastLog, true);
    }

    private void broadcastResults(TaskExecutionLog taskExecutionLog) {
        broadcastResults(taskExecutionLog, false);
    }

    private void broadcastResults(TaskExecutionLog taskExecutionLog, boolean lastMessage) {
        BroadcastResult result = new BroadcastResult(taskExecutionLog.getOutput(), taskExecutionLog.getOutputType());

        executor.execute(() -> jmsTemplate.convertAndSend("taskexecution_destination", result, message -> {
            message.setLongProperty("executionId", taskExecutionLog.getTaskExecution().getId());
            message.setBooleanProperty("lastMessage", lastMessage);
            return message;
        }));
    }

}
