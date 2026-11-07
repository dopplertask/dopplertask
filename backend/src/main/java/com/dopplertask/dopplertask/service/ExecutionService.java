package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStep;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import java.util.List;

public interface ExecutionService {
    /**
     * The steps a run has finished so far, which its execution does not show until the run ends.
     */
    List<TaskExecutionStep> getLiveSteps(Long executionId);

    void forgetLiveSteps(Long executionId);

    /**
     * Initiates execution based on a request.
     *
     * @param taskExecutionRequest with information about what task to run and with what parameters.
     * @param startedByTrigger
     * @return task execution containing task info and initial logs.
     */
    TaskExecution startExecution(TaskExecutionRequest taskExecutionRequest, TaskService taskService, boolean startedByTrigger);

    /**
     * Runs all actions in an execution.
     *
     * @param taskId         of the requested task.
     * @param executionId    of the execution.
     * @param taskParameters from the task request
     * @param taskService    to be provided to actions.
     * @return task execution with logs.
     */
    TaskExecution processActions(Long taskId, Long executionId, TaskService taskService);

    /** Ends a run as failed, and says why. */
    TaskExecution failRun(long id, String reason);

    Optional<TaskExecution> getExecution(long id);

    @Transactional
    TaskExecution processActions(Long taskId, Long executionId, TaskService taskService, TriggerInfo triggerInfo);
}
