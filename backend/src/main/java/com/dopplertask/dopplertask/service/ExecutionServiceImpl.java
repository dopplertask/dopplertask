package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.dao.TaskDao;
import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.*;
import com.dopplertask.dopplertask.domain.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExecutionServiceImpl implements ExecutionService {
    private static final Logger LOG = LoggerFactory.getLogger(ExecutionServiceImpl.class);
    private static final int MAX_ACTION_ACCESS_LIMIT = 2000;

    // A run commits when it ends, so these are what a reader can see of a run in progress.
    private final Map<RunOfCustomer, List<TaskExecutionStep>> liveSteps = new ConcurrentHashMap<>();
    private static final String TRIGGER_PARAMETER_PREFIX = "TRIGGER";

    private JmsTemplate jmsTemplate;
    private CredentialService credentialService;
    private TaskDao taskDao;
    private VariableExtractorUtil variableExtractorUtil;
    private TaskExecutionDao taskExecutionDao;
    private CustomerScope customerScope = CustomerScope.ONE_CUSTOMER;
    private ObjectProvider<RunHousekeeping> runHousekeeping;

    @Autowired
    public ExecutionServiceImpl(JmsTemplate jmsTemplate, TaskDao taskDao, VariableExtractorUtil variableExtractorUtil, CredentialService credentialService, TaskExecutionDao taskExecutionDao) {
        this.jmsTemplate = jmsTemplate;
        this.credentialService = credentialService;
        this.taskDao = taskDao;
        this.variableExtractorUtil = variableExtractorUtil;
        this.taskExecutionDao = taskExecutionDao;
    }

    @Autowired(required = false)
    public void setCustomerScope(CustomerScope customerScope) {
        this.customerScope = customerScope;
    }

    /** Through a provider, since housekeeping in turn ends runs through this service. */
    @Autowired
    public void setRunHousekeeping(ObjectProvider<RunHousekeeping> runHousekeeping) {
        this.runHousekeeping = runHousekeeping;
    }

    /** A step can swallow the interrupt that a stop sends, so the run asks whoever was told. */
    private boolean askedToStop(TaskExecution execution) {
        RunHousekeeping housekeeping = runHousekeeping == null ? null : runHousekeeping.getIfAvailable();
        return housekeeping != null && execution.getId() != null && housekeeping.askedToStop(execution.getId());
    }

    @Transactional
    public TaskExecution startExecution(TaskExecutionRequest taskExecutionRequest, TaskService taskService, boolean startedByTrigger) {
        // Look up by checksum first
        Optional<Task> taskRequest = lookupTask(taskExecutionRequest, taskService);

        Optional<TaskExecution> executionReq = taskExecutionDao.findForUpdate(taskExecutionRequest.getExecutionId());
        // Stopped while it waited, even if its task is gone since.
        if (executionReq.isPresent() && executionReq.get().getStatus() == TaskExecutionStatus.ABORTED) {
            return executionReq.get();
        }
        if (taskRequest.isPresent() && executionReq.isPresent()) {
            Task task = taskRequest.get();
            TaskExecution execution = executionReq.get();

            // Assign task to execution
            execution.setTask(task);

            // Populate variables
            execution.getParameters().putAll(taskExecutionRequest.getParameters());

            execution.setStartdate(new Date());
            execution.setStartedByAgent(taskExecutionRequest.isStartedByAgent());

            String cannotResume = taskExecutionRequest.getResumeFromExecutionId() == null ? null : prepareResume(execution, task, taskExecutionRequest.getResumeFromExecutionId());
            if (cannotResume != null) {
                execution.setStatus(TaskExecutionStatus.FAILED);
                TaskExecutionLog resumeFailed = new TaskExecutionLog();
                resumeFailed.setTaskExecution(execution);
                resumeFailed.setOutput(cannotResume);
                resumeFailed.setBroadcasted(true);
                execution.addLog(resumeFailed);
                execution.setSuccess(false);
                execution.setFailureReason(cannotResume);
                return execution;
            }

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
                executionFailed.setBroadcasted(true);
                execution.addLog(executionFailed);
                execution.setSuccess(false);
                execution.setFailureReason(executionFailed.getOutput());
                LOG.info("Task execution failed, missing parameters: {} [taskId={}, executionId={}]", missingParametersAsString, task.getId(), execution.getId());

                return execution;
            }

            execution.setStatus(TaskExecutionStatus.STARTED);

            TaskExecutionLog executionStarted = new TaskExecutionLog();
            executionStarted.setTaskExecution(execution);
            executionStarted.setOutput("Task execution started [taskId=" + task.getId() + ", executionId=" + execution.getId() + "]");
            executionStarted.setBroadcasted(true);
            execution.addLog(executionStarted);

            taskExecutionDao.save(execution);

            LOG.info("Task execution started [taskId={}, executionId={}]", task.getId(), execution.getId());

            return execution;
        } else {
            LOG.warn("Task could not be found [taskId={}]", taskExecutionRequest.getTaskName());

            if (executionReq.isPresent()) {
                executionReq.get().fail("Task could not be found [taskId=" + taskExecutionRequest.getTaskName() + "]");
            }
            return null;
        }
    }

    private Optional<Task> lookupTask(TaskExecutionRequest taskExecutionRequest, TaskService taskService) {
        Optional<Task> taskRequest = Optional.empty();
        if (taskExecutionRequest.getChecksum() != null && !taskExecutionRequest.getChecksum().isEmpty()) {
            taskRequest = taskDao.findFirstByChecksumStartingWithAndProposedFalse(taskExecutionRequest.getChecksum());

            // Search in the local database by taskName
            if (!taskRequest.isPresent() && taskExecutionRequest.getTaskName() != null && !taskExecutionRequest.getTaskName().isEmpty()) {
                taskRequest = taskDao.findFirstByNameAndProposedFalseOrderByCreatedDesc(taskExecutionRequest.getTaskName());
            }
        } else if (taskExecutionRequest.getTaskName() != null && !taskExecutionRequest.getTaskName().isEmpty()) {
            taskRequest = taskDao.findFirstByNameAndProposedFalseOrderByCreatedDesc(taskExecutionRequest.getTaskName());
        }
        return taskRequest;
    }

    @Override
    @Transactional
    public TaskExecution failRun(long id, String reason) {
        TaskExecution execution = taskExecutionDao.findById(id).orElseThrow();
        execution.fail(reason);
        return execution;
    }

    @Override
    @Transactional
    public TaskExecution abortRun(long id, String reason) {
        TaskExecution execution = taskExecutionDao.findById(id).orElseThrow();
        if (execution.getStatus() != TaskExecutionStatus.FINISHED) {
            execution.abort(reason);
        }
        return execution;
    }

    @Override
    public Optional<TaskExecution> getExecution(long id) {
        return taskExecutionDao.findById(id);
    }

    @Override
    @Transactional
    public TaskExecution processActions(Long taskId, Long executionId, TaskService taskService) {
        Optional<Task> taskRequest = taskDao.findById(taskId);
        Optional<TaskExecution> executionReq = taskExecutionDao.findById(executionId);
        if (taskRequest.isPresent() && executionReq.isPresent()) {
            Task task = taskRequest.get();
            TaskExecution execution = executionReq.get();

            execution.setCurrentAction(execution.getResumePortId() != null ? task.getActionByPort(execution.getResumePortId()) : task.getWebhookStartAction());

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
                execution.setWebhookReply(info.getReply());
            } else {
                action = task.getStartAction(triggerInfo.getTriggerName(), triggerInfo.getTriggerSuffix());
            }


            // Check if the trigger exists
            if (action == null) {
                execution.setStatus(TaskExecutionStatus.FAILED);
                addLog(execution, "The selected trigger or webhook was not found. Cannot start task", OutputType.STRING, Map.of(), true, false);
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
        // A resumed step reads what the step before it gave, which this run did not produce. This is done here since
        // the variables of a log are not stored, so they would not survive until the run starts.
        if (execution.getResumePortId() != null && !execution.getSteps().isEmpty()) {
            TaskExecutionLog previousOutput = new TaskExecutionLog();
            previousOutput.setTaskExecution(execution);
            TaskExecutionStep previousStep = execution.getSteps().get(execution.getSteps().size() - 1);
            previousOutput.setOutput(previousStep.getOutput());
            previousOutput.setOutputVariables(previousStep.getOutputVariables());
            previousOutput.setBroadcasted(true);
            execution.addLog(previousOutput);
        }

        execution.restoreRunningLoops();
        while (execution.getCurrentAction() != null && !askedToStop(execution)) {
            Action currentAction = execution.getCurrentAction();

            execution.addActionAccessCountByOne(currentAction.getId());
            if (checkActionOverfloodAccess(execution, currentAction)) {
                break;
            }

            // The step on the first output follows, unless the step chooses another output, as If, Switch and Loop do.
            execution.setCurrentAction(stepOnFirstOutput(currentAction));

            long actionStart = System.currentTimeMillis();
            ActionResult actionResult = runAction(taskService, execution, currentAction);
            recordStep(execution, currentAction, actionResult, System.currentTimeMillis() - actionStart);

            // The output stays out of the log: it can be large and hold secrets, and it is stored with the run.
            LOG.info("Ran current action: {} with status code: {}", currentAction.getClass().getSimpleName(), actionResult.getStatusCode());

            if (actionResult.getStatusCode() == StatusCode.FAILURE && !currentAction.isContinueOnFailure()) {
                addLog(execution, actionResult.getErrorMsg(), actionResult.getOutputType(), actionResult.getOutputVariables(), true, false, false);
                break;
            }

            // A pass of a loop ends where its steps end, and the loop goes on with its next item.
            if (execution.getCurrentAction() == null && execution.getInnermostLoop() != null) {
                execution.setCurrentAction(task.getActionByPort(execution.getInnermostLoop().getStep()));
            }
        }

        execution.keepRunningLoops();

        // A person or the time limit asked the run to stop. The interrupt that woke its step goes, so that it disturbs
        // nothing else on this thread.
        Thread.interrupted();
        if (askedToStop(execution)) {
            execution.setSuccess(false);
        }

        TaskExecutionLog executionCompleted = new TaskExecutionLog();
        executionCompleted.setTaskExecution(execution);
        executionCompleted.setOutput("Task execution completed [taskId=" + task.getId() + ", executionId=" + execution.getId() + ", success=" + execution.isSuccess() + "]");
        executionCompleted.setBroadcasted(true);
        execution.addLog(executionCompleted);

        LOG.info("Task execution completed [taskId={}, executionId={}]", task.getId(), execution.getId());

        execution.setEnddate(new Date());
        execution.setStatus(execution.isSuccess() ? TaskExecutionStatus.FINISHED : TaskExecutionStatus.FAILED);

        return execution;
    }

    /**
     * Sets up the execution to continue where an earlier run failed: with its values and its finished steps.
     *
     * @return why the run cannot be resumed, or null when it can.
     */
    private String prepareResume(TaskExecution execution, Task task, Long resumeFromExecutionId) {
        TaskExecution earlierRun = taskExecutionDao.findById(resumeFromExecutionId).orElse(null);
        if (earlierRun == null || earlierRun.getTask() == null || !earlierRun.getTask().getName().equals(task.getName())) {
            return "Could not resume. Execution " + resumeFromExecutionId + " is not a run of " + task.getName() + ".";
        }
        List<TaskExecutionStep> steps = earlierRun.getSteps();
        // A run that a person stopped ends at the step that was going, in the same way as one that failed there.
        boolean endedAtAStep = earlierRun.getStatus() == TaskExecutionStatus.FAILED || earlierRun.getStatus() == TaskExecutionStatus.ABORTED;
        if (steps.isEmpty() || steps.get(steps.size() - 1).getStatus() != StatusCode.FAILURE || !endedAtAStep) {
            return "Could not resume. Execution " + resumeFromExecutionId + " did not fail or stop at a step.";
        }
        String failedPort = steps.get(steps.size() - 1).getPortId();
        if (failedPort == null || task.getActionByPort(failedPort) == null) {
            return "Could not resume. The step that failed is no longer in this task, so run the whole task instead.";
        }

        earlierRun.getParameters().forEach((name, parameter) -> execution.getParameters().putIfAbsent(name, new ExecutionParameter(name, parameter.getParamValue(), parameter.isBinary())));
        steps.subList(0, steps.size() - 1).forEach(step -> execution.addStep(step.copyTo(execution)));
        execution.continueLoopsOf(earlierRun);
        execution.setResumePortId(failedPort);
        return null;
    }

    private static Action stepOnFirstOutput(Action action) {
        List<ActionPort> outputs = action.getOutputPorts();
        if (outputs == null || outputs.isEmpty() || outputs.get(0) == null || outputs.get(0).getConnectionSource() == null) {
            return null;
        }
        return outputs.get(0).getConnectionSource().getTarget().getAction();
    }

    private void recordStep(TaskExecution execution, Action action, ActionResult result, long durationMs) {
        String portId = action.getPorts() == null || action.getPorts().isEmpty() ? null : action.getPorts().get(0).getExternalId();
        TaskExecutionStep step = new TaskExecutionStep(execution, portId, action.getClass().getSimpleName(), result, durationMs);
        step.keepOutputs(action.getOutputs().keySet(), result.getOutputVariables());
        if (execution.getWebhookReply() != null) {
            execution.getWebhookReply().setLastOutput(result.getOutput());
        }
        execution.addStep(step);
        liveSteps.computeIfAbsent(live(execution.getId()), run -> new CopyOnWriteArrayList<>()).add(step);
    }

    @Override
    public List<TaskExecutionStep> getLiveSteps(Long executionId) {
        return liveSteps.getOrDefault(live(executionId), List.of());
    }

    @Override
    public void forgetLiveSteps(Long executionId) {
        liveSteps.remove(live(executionId));
    }

    private RunOfCustomer live(long executionId) {
        return new RunOfCustomer(customerScope.current(), executionId);
    }

    private ActionResult runAction(TaskService taskService, TaskExecution execution, Action currentAction) {
        ActionResult actionResult = new ActionResult();

        int tries = 0;
        do {
            try {
                // Only wait if we are in a retry.
                if (tries > 0) {
                    String retryWaitAmount = variableExtractorUtil.extract(currentAction.getRetryWait(), execution, null, currentAction.getScriptLanguage(), Map.of("retryCount", tries + 1));
                    if (retryWaitAmount != null && !retryWaitAmount.isEmpty()) {
                        Thread.sleep(Long.parseLong(retryWaitAmount));
                    }
                }
                actionResult = credentialService.runWith(currentAction, execution, () -> currentAction.run(taskService, execution, variableExtractorUtil, (output, outputType) -> addLog(execution, output, outputType, Map.of(), true)));

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

                        addLog(execution, actionResult.getErrorMsg(), actionResult.getOutputType(), actionResult.getOutputVariables(), false);
                    }
                }
            } catch (Exception e) {
                LOG.error("Exception occurred: {}", e);
                actionResult.setErrorMsg(e.toString());
                actionResult.setStatusCode(StatusCode.FAILURE);

                addLog(execution, actionResult.getOutput(), actionResult.getOutputType(), actionResult.getOutputVariables(), true);
            }

            tries++;
        } while (actionResult.getStatusCode() == StatusCode.FAILURE && currentAction.getRetries() >= tries && !currentAction.isContinueOnFailure() && !askedToStop(execution));
        return actionResult;
    }

    private boolean checkActionOverfloodAccess(TaskExecution execution, Action currentAction) {
        if (execution.getActionAccessCount(currentAction.getId()) > MAX_ACTION_ACCESS_LIMIT + execution.getLoopVisits()) {
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

        if(broadcastLog) {
            log.setBroadcasted(true);
        }

        execution.setSuccess(success);
        if (!success) {
            execution.setFailureReason(output);
        }


        // Add log to the execution
        execution.addLog(log);

        if (broadcastLog) {
        }

        return log;
    }

    public TaskExecutionLog addLog(TaskExecution execution, String output, OutputType outputType, Map<String, Object> outputVariables, boolean broadcastLog, boolean success) {
        return addLog(execution, output, outputType, outputVariables, broadcastLog, success, false);
    }

    public TaskExecutionLog addLog(TaskExecution execution, String output, OutputType outputType, Map<String, Object> outputVariables, boolean broadcastLog) {
        return addLog(execution, output, outputType, outputVariables, broadcastLog, true);
    }

}
