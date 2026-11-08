package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.action.trigger.Webhook;

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
import com.dopplertask.dopplertask.domain.action.trigger.Trigger;
import com.dopplertask.dopplertask.domain.action.common.LinkedTaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TaskServiceImpl implements TaskService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskServiceImpl.class);

    @Autowired
    private JmsTemplate jmsTemplate;

    @Autowired
    private ObjectProvider<RunQueue> runQueue;

    @Autowired
    private TaskDao taskDao;

    @Autowired
    private TaskExecutionDao taskExecutionDao;

    @Autowired
    private ExecutionService executionService;

    @Autowired
    private RunHousekeeping runHousekeeping;

    @Autowired
    private TriggerListenerServiceImpl triggerListenerService;


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
        taskExecutionRequest.setResumeFromExecutionId(request.getResumeFromExecutionId());
        taskExecutionRequest.setStartedByAgent(request.isStartedByAgent());

        RunQueue queue = runQueue.getIfAvailable();
        if (queue != null) {
            try {
                queue.submit(taskExecutionRequest);
            } catch (RuntimeException refused) {
                // Otherwise the run would say it waits, until a restart says the server stopped during it.
                execution.fail("This run was not started: " + refused.getMessage());
                taskExecutionDao.save(execution);
                throw refused;
            }
        } else {
            jmsTemplate.convertAndSend("automation_destination", taskExecutionRequest);
        }

        return execution;
    }

    @Override
    @JmsListener(destination = "automation_destination", containerFactory = "jmsFactory")
    public void handleAutomationRequest(TaskExecutionRequest automationRequest) {
        runRequest(automationRequest);
    }

    @Override
    public TaskExecution runRequest(TaskExecutionRequest taskExecutionRequest) {
        try {
            return runToTheEnd(taskExecutionRequest);
        } catch (RuntimeException | Error e) {
            Long executionId = taskExecutionRequest.getExecutionId();
            if (executionId == null) {
                throw e;
            }
            // Otherwise the queue would run it again, with everything it has already done, and then give up on it.
            LOG.error("A run stopped on an unexpected problem [executionId={}]", executionId, e);
            return executionService.failRun(executionId, "This run stopped on an unexpected problem: " + e);
        } finally {
            if (taskExecutionRequest.getExecutionId() != null) {
                runHousekeeping.finished(taskExecutionRequest.getExecutionId());
            }
        }
    }

    private TaskExecution runToTheEnd(TaskExecutionRequest taskExecutionRequest) {

        boolean isTrigger = taskExecutionRequest.getTriggerInfo() != null && taskExecutionRequest.getTriggerInfo().getTriggerName() != null && !taskExecutionRequest.getTriggerInfo().getTriggerName().isEmpty();

        TaskExecution execution = executionService.startExecution(taskExecutionRequest, this, isTrigger);

        if (execution != null) {
            runHousekeeping.watch(execution.getId(), Thread.currentThread());
            if (execution.getStatus() != TaskExecutionStatus.STARTED) {

                // If set to true, then remove the task after finish executing. This is used for mainly for unsaved workflows.
                if (taskExecutionRequest.isRemoveTaskAfterExecution()) {
                    this.deleteTask(taskExecutionRequest.getTaskName());
                }

                return execution;
            } else {
                TaskExecution taskExecution;
                try {
                    if (isTrigger) {
                        taskExecution = executionService.processActions(execution.getTask().getId(), execution.getId(), this, taskExecutionRequest.getTriggerInfo());
                    } else {
                        taskExecution = executionService.processActions(execution.getTask().getId(), execution.getId(), this);
                    }
                } finally {
                    executionService.forgetLiveSteps(execution.getId());
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
        return taskDao.findAll();
    }

    @Override
    @Transactional
    public Long createTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum, boolean active) {
        return createTask(name, taskParameters, actions, description, connections, checksum, active, false);
    }

    @Override
    @Transactional
    public Long createTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum, boolean active, boolean autoSaved) {
        return saveTask(name, taskParameters, actions, description, connections, checksum, active, autoSaved, false);
    }

    @Override
    @Transactional
    public Long proposeTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum) {
        return saveTask(name, taskParameters, actions, description, connections, checksum, false, false, true);
    }

    @Override
    @Transactional
    public boolean approveTask(String checksum, String approvedBy) {
        Optional<Task> proposal = taskDao.findByChecksum(checksum);
        if (proposal.isEmpty() || !proposal.get().isProposed()) {
            return false;
        }
        // It takes over from the current version, so a schedule that is on stays on.
        boolean active = taskDao.findFirstByNameAndProposedFalseOrderByCreatedDesc(proposal.get().getName()).map(Task::isActive).orElse(false);
        if (active) {
            requireWorkingTriggers(proposal.get().getActionList());
        }
        proposal.get().setProposed(false);
        proposal.get().setApprovedBy(approvedBy);
        proposal.get().setActive(active);
        proposal.get().setCreated(new Date());
        triggerListenerService.updateTriggers(proposal.get().getId());
        return true;
    }

    /** A trigger that cannot start the task is reported when the task is turned on, rather than each time it would start. */
    private static void requireWorkingTriggers(List<Action> actions) {
        for (Action action : actions) {
            String problem = action instanceof Trigger trigger ? trigger.problem() : null;
            if (problem != null) {
                String name = action.getActionName();
                boolean named = name != null && !name.isBlank() && !name.equals(action.getClass().getSimpleName());
                String step = named ? name : "The trigger";
                throw new BrokenTriggerException(step + " cannot start the task. " + problem);
            }
        }
    }

    private Long saveTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum, boolean active, boolean autoSaved, boolean proposed) {

        // Auto save only adds versions to a task the user has saved, so it cannot create drafts or revive deleted tasks.
        if (autoSaved && taskDao.findAllByName(name).isEmpty()) {
            return null;
        }

        if (name.contains(" ")) {
            throw new WhiteSpaceInNameException("Could not create task. Task name contains whitespace.");
        }

        // If we try to add the same task again, we just return the current id.
        Optional<Task> existingTask = taskDao.findByChecksum(checksum);
        if (active) {
            requireWorkingTriggers(existingTask.map(Task::getActionList).orElse(actions));
        }
        if (existingTask.isPresent()) {
            // An agent saving what already exists changes nothing, and a person saving a proposal accepts it.
            if (proposed) {
                return existingTask.get().getId();
            }
            existingTask.get().setProposed(false);
            existingTask.get().setCreated(new Date());
            existingTask.get().setActive(active);
            existingTask.get().setAutoSaved(existingTask.get().isAutoSaved() && autoSaved);

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
                    Optional<Task> linkedTask = taskDao.findFirstByNameAndProposedFalseOrderByCreatedDesc(((LinkedTaskAction) action).getName());

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
        task.setAutoSaved(autoSaved);
        task.setProposed(proposed);
        task.setMadeByAgent(proposed);

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

        // A proposal must not disturb what is running.
        if (!proposed) {
            triggerListenerService.updateTriggers(task.getId());
        }

        return task.getId();
    }

    @Override
    public List<TaskExecution> getExecutions() {
        return taskExecutionDao.findAllByTaskNotNull();
    }

    @Override
    @Transactional
    public List<TaskExecution> getExecutions(String taskName, int limit) {
        return taskExecutionDao.findByTask_NameOrderByIdDesc(taskName, PageRequest.of(0, limit));
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
    public Task deleteTask(String taskNameOrChecksum) {
        // Find task by checksum if input is longer than 1 characters.
        if (taskNameOrChecksum.length() > 1) {
            Optional<Task> taskByChecksum = taskDao.findFirstByChecksumStartingWith(taskNameOrChecksum);
            if (taskByChecksum.isPresent()) {
                return deleteVersion(taskByChecksum.get());
            }
        }

        // Find task by name
        Optional<Task> taskByName = taskDao.findFirstByNameAndProposedFalseOrderByCreatedDesc(taskNameOrChecksum);
        if (taskByName.isPresent()) {
            return deleteVersion(taskByName.get());
        }

        throw new TaskNotFoundException("Task could not be found.");
    }

    private Task deleteVersion(Task task) {
        taskDao.delete(task);

        if (taskDao.findAllByName(task.getName()).isEmpty()) {
            triggerListenerService.stopTriggers(task.getName());
        }
        return task;
    }

    @Override
    @Transactional(readOnly = true)
    public String webhookReplyMode(String taskName, String triggerName, String triggerSuffix, String path, String method) {
        Webhook webhook = webhookOf(taskName, triggerName, triggerSuffix, path, method);
        return webhook == null ? null : webhook.getResponseMode();
    }

    @Override
    @Transactional(readOnly = true)
    public String webhookReplyCode(String taskName, String triggerName, String triggerSuffix, String path, String method) {
        Webhook webhook = webhookOf(taskName, triggerName, triggerSuffix, path, method);
        return webhook == null ? null : webhook.getResponseCode();
    }

    private Webhook webhookOf(String taskName, String triggerName, String triggerSuffix, String path, String method) {
        Task task = getTaskByName(taskName);
        return task == null ? null : task.getWebhookStartAction(triggerName, triggerSuffix, path, method) instanceof Webhook webhook ? webhook : null;
    }

    @Override
    @Transactional
    public Task getTaskByName(String taskName) {
        Optional<Task> task = taskDao.findFirstByNameAndProposedFalseOrderByCreatedDesc(taskName);

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
        taskExecutionRequest.setResumeFromExecutionId(request.getResumeFromExecutionId());
        taskExecutionRequest.setStartedByAgent(request.isStartedByAgent());

        return this.runRequest(taskExecutionRequest);
    }
}
