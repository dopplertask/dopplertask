package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.domain.Connection;
import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskParameter;
import com.dopplertask.dopplertask.domain.action.Action;

import java.util.List;

/**
 * This service represents the scheduling and execution of tasks
 */
public interface TaskService {

    /**
     * Schedule a task
     *
     * @param request containing the information about what to execute.
     */
    TaskExecution delegate(TaskRequest request);


    /**
     * Consumes scheduled tasks and executes them.
     *
     * @param taskRequest containing the information about what to execute.
     */
    void handleAutomationRequest(TaskExecutionRequest taskRequest);

    /**
     * Runs task with provided execution.
     *
     * @param automationRequest containing the information about what to execute.
     * @return execution containing the results.
     */
    TaskExecution runRequest(TaskExecutionRequest taskExecutionRequest);

    /**
     * Creates execution and runs task.
     *
     * @param request containing the information about what to execute.
     * @return execution containing the results.
     */
    TaskExecution runRequest(TaskRequest request);

    /** Whether a run waits for the key. */
    boolean isWaiting(String waitKey);

    /**
     * Continues the run that waits for the key, once.
     *
     * @param resumeEvent what arrived, which the waiting step reads.
     * @return the run that continues it, or null when no run waits for the key.
     */
    TaskExecution resumeWaiting(String waitKey, String resumeEvent);

    /** Continues each run whose answer arrived before the server stopped, once the server starts again. */
    void continueAnsweredRuns();

    /**
     * Get all tasks
     *
     * @return all tasks from database.
     */
    List<Task> getAllTasks();

    /**
     * Saves a version from an agent. It stays out of runs, webhooks and schedules until a person approves it.
     */
    Long proposeTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum);

    /**
     * Makes a proposal the current version.
     *
     * @param approvedBy who approved it, or null if that is not known.
     * @return false if there is no such proposal.
     */
    boolean approveTask(String checksum, String approvedBy);

    Long createTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum, boolean active);
    Long createTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum, boolean active, boolean autoSaved);

    List<TaskExecution> getExecutions();

    /**
     * The latest runs of one task, newest first, across all its versions.
     */
    List<TaskExecution> getExecutions(String taskName, int limit);

    Task getTask(long id);

    /**
     * Deletes a task by checksum or name. It searches first for checksum.
     *
     * @param taskNameOrChecksum
     * @return
     */
    Task deleteTask(String taskNameOrChecksum);

    Task getTaskByName(String taskName);

    Task getTaskByChecksum(String checksum);

    /** How the webhook that a call reaches answers it, or null when there is no such webhook. */
    String webhookReplyMode(String taskName, String triggerName, String triggerSuffix, String path, String method);

    /** The status a webhook answers with when it answers with what the last step gave. */
    String webhookReplyCode(String taskName, String triggerName, String triggerSuffix, String path, String method);
}
