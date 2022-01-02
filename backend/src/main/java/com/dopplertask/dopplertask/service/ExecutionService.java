package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ExecutionService {
    /**
     * Initiates execution based on a request.
     *
     * @param taskExecutionRequest with information about what task to run and with what parameters.
     * @param startedByTrigger
     * @return task execution containing task info and initial logs.
     */
    TaskExecution startExecution(TaskExecutionRequest taskExecutionRequest, TaskService taskService, boolean startedByTrigger);

    /**
     * Pulls task from online repository, compares it to local tasks to determine if there is a need to save it.
     *
     * @param taskName    of the task.
     * @param taskService to allow for task creation.
     * @return a Task if one is found online or locally, otherwise nothing.
     */
    Optional<Task> pullTask(String taskName, TaskService taskService);

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

    Optional<Task> findOrDownloadByName(String taskName, TaskService taskService);

    Optional<TaskExecution> getExecution(long id);

    void deleteExecution(long id);

    @Transactional
    TaskExecution processActions(Long taskId, Long executionId, TaskService taskService, TriggerInfo triggerInfo);
}
