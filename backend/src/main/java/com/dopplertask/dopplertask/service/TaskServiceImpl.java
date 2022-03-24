package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.dao.TaskDao;
import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.ActionPort;
import com.dopplertask.dopplertask.domain.ActionPortType;
import com.dopplertask.dopplertask.domain.Connection;
import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import com.dopplertask.dopplertask.domain.TaskParameter;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.common.LinkedTaskAction;
import com.dopplertask.dopplertask.dto.TaskCreationDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TaskServiceImpl implements TaskService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskServiceImpl.class);
    private static final String DOPPLERTASK_WORKFLOW_UPLOAD = "https://www.dopplertask.com/submitworkflow.php";
    private static final String DOPPLERTASK_LOGIN = "https://www.dopplertask.com/logincheck.php";

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private TaskDao taskDao;

    @Autowired
    private TaskExecutionDao taskExecutionDao;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private TriggerListenerServiceImpl triggerListenerService;

    @Value("${dopplertask.encryptionKey}")
    private String encryptionKey;


    @Override
    public TaskExecution delegate(TaskRequest request) {
        LOG.info("Delegating action");

        TaskExecution execution = new TaskExecution();
        // Create execution record
        taskExecutionDao.save(execution);

        TaskExecutionRequest taskExecutionRequest = new TaskExecutionRequest();
        taskExecutionRequest.setTaskName(request.getTaskName());
        taskExecutionRequest.setParameters(request.getParameters());
        taskExecutionRequest.setExecutionId(execution.getId());
        taskExecutionRequest.setChecksum(request.getChecksum());
        taskExecutionRequest.setRemoveTaskAfterExecution(request.isRemoveTaskAfterExecution());
        taskExecutionRequest.setTriggerInfo(request.getTriggerInfo());

        jmsTemplate.convertAndSend("automation_destination", taskExecutionRequest);

        return execution;
    }

    @Override
    @JmsListener(destination = "automation_destination", containerFactory = "jmsFactory")
    public void handleAutomationRequest(TaskExecutionRequest automationRequest) {
        runRequest(automationRequest);
    }

    @Override
    public TaskExecution runRequest(TaskExecutionRequest taskExecutionRequest) {

        boolean isTrigger = taskExecutionRequest.getTriggerInfo() != null && taskExecutionRequest.getTriggerInfo().getTriggerName() != null && !taskExecutionRequest.getTriggerInfo().getTriggerName().isEmpty();

        TaskExecution execution = executionService.startExecution(taskExecutionRequest, this, isTrigger);

        if (execution != null) {
            if (execution.getStatus() == TaskExecutionStatus.FAILED) {

                // If set to true, then remove the task after finish executing. This is used for mainly for unsaved workflows.
                if (taskExecutionRequest.isRemoveTaskAfterExecution()) {
                    this.deleteTask(taskExecutionRequest.getTaskName());
                }

                return execution;
            } else {
                TaskExecution taskExecution;
                if (isTrigger) {
                    taskExecution = executionService.processActions(execution.getTask().getId(), execution.getId(), this, taskExecutionRequest.getTriggerInfo());
                } else {
                    taskExecution = executionService.processActions(execution.getTask().getId(), execution.getId(), this);
                }
                // If set to true, then remove the task after finish executing. This is used for mainly for unsaved workflows.
                if (taskExecutionRequest.isRemoveTaskAfterExecution()) {
                    this.deleteTask(taskExecutionRequest.getTaskName());
                }
                return taskExecution;
            }
        }
        return null;
    }

    @Override
    @Transactional
    public List<Task> getAllTasks() {
        List<Task> tasks = taskDao.findAll();
        tasks.forEach(task -> {


        });
        return tasks;
    }

    @Override
    @Transactional
    public Long createTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum, boolean active) {
        return createTask(name, taskParameters, actions, description, connections, checksum, active, true);
    }

    @Override
    @Transactional
    public Long createTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum, boolean active, boolean buildTask) {

        if (name.contains(" ")) {
            throw new WhiteSpaceInNameException("Could not create task. Task name contains whitespace.");
        }

        // If we try to add the same task again, we just return the current id.
        Optional<Task> existingTask = taskDao.findByChecksum(checksum);
        if (existingTask.isPresent()) {
            existingTask.get().setCreated(new Date());
            existingTask.get().setActive(active);

            // Update threadpools
            triggerListenerService.updateTriggers(existingTask.get().getId());
            return existingTask.get().getId();
        }

        Task task = new Task();

        task.setName(name);

        Map<String, ActionPort> portMap = new HashMap<>();
        actions.forEach(action -> {
            action.setTask(task);

            // Find task or download it if necessary and assign checksum
            if (action instanceof LinkedTaskAction) {
                if (((LinkedTaskAction) action).getName() != null && !((LinkedTaskAction) action).getName().isEmpty()) {
                    Optional<Task> linkedTask = executionService.findOrDownloadByName(((LinkedTaskAction) action).getName(), this);

                    if (linkedTask.isPresent()) {
                        ((LinkedTaskAction) action).setChecksum(linkedTask.get().getChecksum());
                    } else {
                        throw new LinkedTaskNotFoundException("Linked Task could not be found locally or in the public hub [name=" + ((LinkedTaskAction) action).getName() + "]");
                    }
                } else {
                    throw new LinkedTaskNotFoundException("Linked Task does not have any task name associated");
                }
            }


            for (ActionPort actionPort : action.getPorts()) {
                actionPort.setAction(action);
                portMap.put(actionPort.getExternalId(), actionPort);
            }

        });

        if (taskParameters != null) {
            taskParameters.forEach(taskParameter -> taskParameter.setTask(task));
            task.setTaskParameterList(taskParameters);
        }
        task.setActionList(actions);

        task.setActive(active);

        // Check if action is available
        if (task.getWebhookStartAction() == null) {
            throw new NoStartActionFoundException("No start action is found in the task. Please create one.");
        }

        task.setCreated(new Date());
        task.setChecksum(checksum);
        task.setDescription(description);

        // Prepare connections
        connections.forEach(connection -> {
            connection.setTask(task);
            if (connection.getSource() != null && connection.getTarget() != null && connection.getSource().getExternalId() != null && connection.getTarget().getExternalId() != null) {
                ActionPort sourcePort = portMap.get(connection.getSource().getExternalId());
                ActionPort targetPort = portMap.get(connection.getTarget().getExternalId());
                if (sourcePort.getPortType() == ActionPortType.OUTPUT && targetPort.getPortType() == ActionPortType.INPUT) {
                    connection.setSource(sourcePort);
                    connection.setTarget(targetPort);
                } else {
                    throw new IncompleteConnectionException("Source port is not an output port or the target port is not an input port.");
                }
            } else {
                throw new IncompleteConnectionException("The connection lacks a source or target port.");
            }
        });

        task.setConnections(connections);

        // Save the new task
        taskDao.save(task);

        // Update threadpools
        triggerListenerService.updateTriggers(task.getId());

        return task.getId();
    }

    @Override
    public List<TaskExecution> getExecutions() {
        return taskExecutionDao.findAllByTaskNotNull();
    }

    @Override
    @Transactional
    public Task getTask(long id) {
        Optional<Task> taskOptional = taskDao.findById(id);
        if (taskOptional.isPresent()) {
            Task task = taskOptional.get();
            return task;
        }
        return null;
    }

    @Override
    @Transactional
    public boolean loginUser(String username, String password) {
        try {

            String base64credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(DOPPLERTASK_LOGIN))
                    .timeout(Duration.ofMinutes(1)).setHeader("Authorization", "Basic " + base64credentials);
            builder = builder.GET();
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = builder.build();
            // Get JSON from Hub
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 && response.body().equals("{\"message\": \"Logged in\"}")) {
                // Update the credentials file
                PrintWriter writer = new PrintWriter(System.getProperty("user.home") + "/.dopplercreds", "UTF-8");
                writer.print(base64credentials);
                writer.close();

                return true;
            }

        } catch (IOException | InterruptedException e) {
            return false;
        }

        return false;
    }

    @Override
    @Transactional
    public boolean pushTask(String taskName) {
        Optional<Task> taskOptional = taskDao.findFirstByNameOrderByCreatedDesc(taskName);
        if (taskOptional.isPresent()) {
            Task task = taskOptional.get();

            TaskCreationDTO dto = new TaskCreationDTO(task.getName(), task.getConnections(), task.getTaskParameterList(), task.getActionList(), task.getDescription(), task.isActive());

            ObjectMapper mapper = new ObjectMapper();
            try {
                String compactJSON = mapper.writeValueAsString(dto);

                // Read credentials
                String credentials = Files.readString(Paths.get(System.getProperty("user.home") + "/.dopplercreds")).replace("\n", "");

                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(DOPPLERTASK_WORKFLOW_UPLOAD))
                        // TODO: Make this authentication based on input from the CLI.
                        .timeout(Duration.ofMinutes(1)).setHeader("Authorization", "Basic " + credentials);
                builder = builder.POST(HttpRequest.BodyPublishers.ofString(compactJSON));
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = builder.build();
                // Get JSON from Hub
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200 && response.body().equals("{\"message\": \"Successfully uploaded workflow.\"}")) {
                    // Successful upload
                    return true;
                } else if (response.body().equals("{\"message\": \"Could not authenticate user.\"}")) {
                    throw new AuthenticationException("Task could not be uploaded. You've provided wrong credentials.");
                } else if (response.body().equals("{\"message\": \"A workflow with the same checksum exists. Aborting.\"}")) {
                    throw new TaskAlreadyUploadedException("This task is already uploaded.");
                } else if (response.body().equals("{\"message\": \"A workflow name must start with a username followed by a forward slash followed by the task name.\"}")) {
                    throw new TaskAlreadyUploadedException("A workflow name must start with a username followed by a forward slash followed by the task name.");
                }

            } catch (IOException | InterruptedException e) {
                throw new UploadNotSuccessfulException("Task could not be uploaded. Check that you've logged in, or that you have an internet connection.");
            }
        }
        throw new TaskNotFoundException("Task could not be found");
    }

    @Override
    @Transactional
    public Task deleteTask(String taskNameOrChecksum) {
        // Find task by checksum if input is longer than 1 characters.
        if (taskNameOrChecksum.length() > 1) {
            Optional<Task> taskByChecksum = taskDao.findFirstByChecksumStartingWith(taskNameOrChecksum);
            if (taskByChecksum.isPresent()) {
                taskDao.delete(taskByChecksum.get());
                return taskByChecksum.get();
            }
        }

        // Find task by name
        Optional<Task> taskByName = taskDao.findFirstByNameOrderByCreatedDesc(taskNameOrChecksum);
        if (taskByName.isPresent()) {
            taskDao.delete(taskByName.get());
            return taskByName.get();
        }

        throw new TaskNotFoundException("Task could not be found.");
    }

    @Override
    @Transactional
    public Task getTaskByName(String taskName) {
        Optional<Task> task = taskDao.findFirstByNameOrderByCreatedDesc(taskName);

        if (task.isPresent()) {
            Task taskObj = task.get();
            return taskObj;
        }
        return null;
    }

    @Override
    @Transactional
    public Task getTaskByChecksum(String checksum) {
        Optional<Task> task = taskDao.findFirstByChecksumStartingWith(checksum);

        if (task.isPresent()) {
            Task taskObj = task.get();
            return taskObj;
        }
        return null;
    }

    @Override
    public TaskExecution runRequest(TaskRequest request) {
        TaskExecution execution = new TaskExecution();
        execution.setDepth(request.getDepth());
        // Create execution record
        // Always save to allow consumer to get a message
        taskExecutionDao.save(execution);

        TaskExecutionRequest taskExecutionRequest = new TaskExecutionRequest();
        taskExecutionRequest.setTaskName(request.getTaskName());
        taskExecutionRequest.setParameters(request.getParameters());
        taskExecutionRequest.setExecutionId(execution.getId());
        taskExecutionRequest.setDepth(request.getDepth());
        taskExecutionRequest.setChecksum(request.getChecksum());
        taskExecutionRequest.setTriggerInfo(request.getTriggerInfo());

        return this.runRequest(taskExecutionRequest);
    }

    public String getEncryptionKey() {
        return encryptionKey;
    }
}
