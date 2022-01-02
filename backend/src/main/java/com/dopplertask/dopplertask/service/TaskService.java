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

    /**
     * Get all tasks
     *
     * @return all tasks from database.
     */
    List<Task> getAllTasks();

    Long createTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum, boolean active);

    Long createTask(String name, List<TaskParameter> taskParameters, List<Action> actions, String description, List<Connection> connections, String checksum, boolean active, boolean buildTask);

    List<TaskExecution> getExecutions();

    Task getTask(long id);

    boolean loginUser(String username, String password);

    boolean pushTask(String taskName);

    /**
     * Deletes a task by checksum or name. It searches first for checksum.
     *
     * @param taskNameOrChecksum
     * @return
     */
    Task deleteTask(String taskNameOrChecksum);

    Task getTaskByName(String taskName);

    Task getTaskByChecksum(String checksum);
}
