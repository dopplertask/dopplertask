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

    /**
     * A step can swallow the interrupt that a stop sends, so the run also asks whoever was told. Any other interrupt, such
     * as a schedule being turned off, still ends the run as before.
     */
    private boolean toldToStop() {
        RunHousekeeping housekeeping = runHousekeeping == null ? null : runHousekeeping.getIfAvailable();
        return Thread.currentThread().isInterrupted() || housekeeping != null && housekeeping.stopAskedHere();
    }

    @Transactional
    public TaskExecution startExecution(TaskExecutionRequest taskExecutionRequest, TaskService taskService, boolean startedByTrigger) {
        // Look up by checksum first
        Optional<Task> taskRequest = lookupTask(taskExecutionRequest, taskService);

        // A run that waits is locked while it starts, so that a stop at the same moment waits for it. A linked run never
        // waits, and runs inside its parent's transaction, which a lock would hold until the parent ends.
        Optional<TaskExecution> executionReq = taskExecutionRequest.getDepth() > 0 ? taskExecutionDao.findById(taskExecutionRequest.getExecutionId())
                : taskExecutionDao.findForUpdate(taskExecutionRequest.getExecutionId());
        // Only a run that waits starts. One that was stopped meanwhile, or failed, stays as it is, even if its task is gone.
        if (executionReq.isPresent() && executionReq.get().getStatus() != TaskExecutionStatus.CREATED) {
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

            Long resumedFrom = execution.getResumedFrom();
            TaskExecution earlierRun = resumedFrom == null ? null : taskExecutionDao.findById(resumedFrom).orElse(null);
            String cannotResume = resumedFrom == null ? null : prepareResume(execution, task, resumedFrom, earlierRun, taskExecutionRequest.getParameters());
            if (cannotResume != null) {
                execution.fail(cannotResume);
                return execution;
            }

            // Check if task is started by trigger when the active is set to false, if so, fail.
            if (startedByTrigger && !task.isActive()) {
                execution.fail("Task is not active. Please set task to active to respond to webhooks");
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
                String missingParametersAsString = Arrays.toString(missingParameters.toArray());
                execution.fail("Task execution failed, missing parameters: " + missingParametersAsString + " [taskId=" + task.getId() + ", executionId=" + execution.getId() + "]");
                LOG.info("Task execution failed, missing parameters: {} [taskId={}, executionId={}]", missingParametersAsString, task.getId(), execution.getId());

                return execution;
            }

            // The earlier run's steps, and a waiting run's answer, go to this run only when it starts, so that a run that
            // cannot start leaves the answer there and is not one that can be resumed itself.
            if (earlierRun != null && earlierRun.isAnswered()) {
                String arrived = earlierRun.getResumeEvent();
                if (taskExecutionDao.takeAnswer(earlierRun.getId(), TaskExecutionStatus.FINISHED, new Date()) == 0) {
                    execution.fail("Could not resume. Execution " + earlierRun.getId() + " has already continued or was stopped.");
                    return execution;
                }
                execution.setResumeEvent(arrived);
            }
            if (earlierRun != null) {
                continueSteps(execution, earlierRun);
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
            // A file never takes the place of a value the run already has.
            triggerInfo.getFiles().forEach((name, bytes) -> {
                if (!execution.getParameters().containsKey(name)) {
                    RunFiles.INSTANCE.keep(execution, name, bytes);
                }
            });

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

        execution.getSteps().forEach(step -> keepForLaterSteps(execution, task.getActionByPort(step.getPortId()), step));
        execution.restoreRunningLoops();
        while (execution.getCurrentAction() != null && !toldToStop()) {
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

            // What arrived is only for the step the run continues at.
            execution.setResumeEvent(null);

            if (actionResult.getStatusCode() == StatusCode.FAILURE && !currentAction.isContinueOnFailure()) {
                addLog(execution, actionResult.getErrorMsg(), actionResult.getOutputType(), actionResult.getOutputVariables(), true, false, false);
                break;
            }

            // A step that waits stops the run. Its key continues the run at that step, which then reads the answer.
            if (actionResult.getWaitKey() != null) {
                execution.keepRunningLoops();
                execution.setWaitKey(actionResult.getWaitKey());
                execution.setStatus(TaskExecutionStatus.WAITING);
                addLog(execution, "The run waits for an answer at " + currentAction.getActionName() + ".", OutputType.STRING, Map.of(), true, false);
                return execution;
            }

            // A pass of a loop ends where its steps end, and the loop goes on with its next item.
            if (execution.getCurrentAction() == null && execution.getInnermostLoop() != null) {
                execution.setCurrentAction(task.getActionByPort(execution.getInnermostLoop().getStep()));
            }

        }

        execution.keepRunningLoops();

        // The run was told to stop. The interrupt that woke its step goes, so that it disturbs nothing else on this thread.
        if (toldToStop()) {
            execution.setSuccess(false);
        }
        Thread.interrupted();

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
     * Sets up the execution to continue an earlier run with its values, at the step where it failed or was stopped, or
     * at the step where it waits, which then reads what arrived. Its finished steps follow when the run starts.
     *
     * @return why the run cannot be resumed, or null when it can.
     */
    private String prepareResume(TaskExecution execution, Task task, Long resumeFromExecutionId, TaskExecution earlierRun, Map<String, ExecutionParameter> given) {
        if (earlierRun == null || earlierRun.getTask() == null || !earlierRun.getTask().getName().equals(task.getName())) {
            return "Could not resume. Execution " + resumeFromExecutionId + " is not a run of " + task.getName() + ".";
        }
        // A waiting run continues only when its address was called. It, and every run that goes on after a person's
        // answer, continues with the values the person saw who answered and with the current version of the task.
        boolean answered = earlierRun.isAnswered();
        boolean keepsValues = answered || earlierRun.isAfterAnswer();
        if (keepsValues && changesParameters(task, earlierRun, given)) {
            return "Could not resume. Execution " + resumeFromExecutionId + " goes on after an answer, so it continues with the values it had. Leave the parameters out or give the same values.";
        }
        if (keepsValues && !taskDao.findFirstByNameAndProposedFalseOrderByCreatedDesc(task.getName()).map(current -> current.getId().equals(task.getId())).orElse(false)) {
            return "Could not resume. Execution " + resumeFromExecutionId + " goes on after an answer, so it continues with the current version of " + task.getName() + ".";
        }
        List<TaskExecutionStep> steps = earlierRun.getSteps();
        // A run that a person stopped ends at the step that was going, in the same way as one that failed there.
        boolean endedAtAStep = earlierRun.getStatus() == TaskExecutionStatus.FAILED || earlierRun.getStatus() == TaskExecutionStatus.ABORTED;
        if (!answered && (steps.isEmpty() || steps.get(steps.size() - 1).getStatus() != StatusCode.FAILURE || !endedAtAStep)) {
            return "Could not resume. Execution " + resumeFromExecutionId + (earlierRun.getStatus() == TaskExecutionStatus.WAITING ? " waits for its answer." : " did not fail or stop at a step.");
        }
        String resumePort = steps.get(steps.size() - 1).getPortId();
        if (resumePort == null || task.getActionByPort(resumePort) == null) {
            String removed = "Could not resume. The step to continue at is no longer in this task, so run the whole task instead.";
            // Its key is taken, so a waiting run that cannot continue ends here, and what arrived is not kept.
            if (answered && taskExecutionDao.takeAnswer(earlierRun.getId(), TaskExecutionStatus.FAILED, new Date()) == 1) {
                earlierRun.setResumeEvent(null);
                earlierRun.fail(removed);
            }
            return removed;
        }

        // A run that goes on after an answer has exactly its own values, with its files and what its trigger gave, and
        // none that the resume brings. Other runs keep their values unless the resume gives new ones.
        if (keepsValues) {
            execution.getParameters().clear();
        }
        earlierRun.getParameters().forEach((name, parameter) -> execution.getParameters().putIfAbsent(name, new ExecutionParameter(name, parameter.getParamValue(), parameter.isBinary())));
        execution.setAfterAnswer(keepsValues);
        execution.setResumePortId(resumePort);
        return null;
    }

    /**
     * Whether a resume gives one of the task's parameters another value than the earlier run had. The editor sends the
     * same values again, with the run's files as text, which is no change, and a file is not a value a person gives.
     */
    private static boolean changesParameters(Task task, TaskExecution earlierRun, Map<String, ExecutionParameter> given) {
        return task.getTaskParameterList().stream().map(TaskParameter::getName).filter(given::containsKey).anyMatch(name -> {
            ExecutionParameter had = earlierRun.getParameters().get(name);
            return had == null || !had.isBinary() && !Arrays.equals(had.getParamValue(), given.get(name).getParamValue());
        });
    }

    /** Takes over the finished steps and the loops of the earlier run, once the run continuing it starts. */
    private static void continueSteps(TaskExecution execution, TaskExecution earlierRun) {
        List<TaskExecutionStep> steps = earlierRun.getSteps();
        steps.subList(0, steps.size() - 1).forEach(step -> execution.addStep(step.copyTo(execution)));
        execution.continueLoopsOf(earlierRun);
        execution.continuePreviewOf(earlierRun);
    }

    private static Action stepOnFirstOutput(Action action) {
        List<ActionPort> outputs = action.getOutputPorts();
        if (outputs == null || outputs.isEmpty() || outputs.get(0) == null || outputs.get(0).getConnectionSource() == null) {
            return null;
        }
        return outputs.get(0).getConnectionSource().getTarget().getAction();
    }

    private void recordStep(TaskExecution execution, Action action, ActionResult result, long durationMs) {
        result.setMissingValues(execution.takeMissingValues());
        TaskExecutionStep step = new TaskExecutionStep(execution, action.getPortId(), action.getStepTypeName(), result, durationMs);
        step.keepOutputs(action.getOutputs().keySet(), result.getOutputVariables());
        keepForLaterSteps(execution, action, step);
        if (execution.getWebhookReply() != null) {
            execution.getWebhookReply().setLastOutput(result.getOutput());
        }
        execution.addStep(step);
        liveSteps.computeIfAbsent(live(execution.getId()), run -> new CopyOnWriteArrayList<>()).add(step);
    }

    /** A later step reads this one by its name, such as $steps["Get order"].data.total. */
    private static void keepForLaterSteps(TaskExecution execution, Action action, TaskExecutionStep step) {
        if (action != null) {
            execution.getStepOutputs().put(action.getActionName(), new StepOutput(step.getOutput(), step.getOutputVariables()));
        }
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
        Preview preview = execution.getPreview();
        boolean madeToFail = preview != null && preview.getMadeToFail().contains(currentAction.getPortId());

        int tries = 0;
        do {
            try {
                // Only wait if we are in a retry. A preview shows the retries and does not wait for them.
                if (tries > 0 && preview == null) {
                    String retryWaitAmount = variableExtractorUtil.extract(currentAction.getRetryWait(), execution, null, currentAction.getScriptLanguage(), Map.of("retryCount", tries + 1));
                    if (retryWaitAmount != null && !retryWaitAmount.isEmpty()) {
                        Thread.sleep(Long.parseLong(retryWaitAmount));
                    }
                }
                actionResult = madeToFail ? ActionResult.Companion.failure("This step was made to fail in the preview.")
                        : credentialService.runWith(currentAction, execution, () -> currentAction.run(taskService, execution, variableExtractorUtil, (output, outputType) -> addLog(execution, output, outputType, Map.of(), true)));
                // A held step got no answer. The steps after it read the example the person gave for it.
                if (actionResult.getHeld() != null) {
                    actionResult.setOutput(preview.getExampleAnswers().getOrDefault(currentAction.getPortId(), ""));
                }

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
        } while (actionResult.getStatusCode() == StatusCode.FAILURE && currentAction.getRetries() >= tries && !currentAction.isContinueOnFailure() && !toldToStop());
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
