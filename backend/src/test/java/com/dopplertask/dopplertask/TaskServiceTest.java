package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.dao.TaskDao;
import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionLog;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.common.PrintAction;
import com.dopplertask.dopplertask.service.ExecutionService;
import com.dopplertask.dopplertask.service.TaskExecutionRequest;
import com.dopplertask.dopplertask.service.TaskServiceImpl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
public class TaskServiceTest {

    @InjectMocks
    private TaskServiceImpl taskService;

    @Mock
    private TaskDao taskDao;

    @Mock
    private TaskExecutionDao taskExecutionDao;

    @Mock
    private ExecutionService executionService;

    @Mock
    private JmsTemplate jmsTemplate;

    @Test
    public void runSimpleTaskWithNoActionsShouldReturnOk() {
        // Setup data
        Task task = new Task();
        task.setId(1928L);
        task.setName("doppler-test");

        TaskExecution taskExecution = new TaskExecution();
        taskExecution.setId(283471L);

        // Prepare behavior
        Optional<Task> taskOptional = Optional.of(task);
        when(taskDao.findFirstByNameOrderByCreatedDesc(eq("doppler-test"))).thenReturn(taskOptional);

        Optional<TaskExecution> executionOptional = Optional.of(taskExecution);
        when(taskExecutionDao.findById(eq(283471L))).thenReturn(executionOptional);

        // Run test
        TaskExecutionRequest request = new TaskExecutionRequest();
        request.setExecutionId(283471L);
        request.setTaskName("doppler-test");
        request.setParameters(new HashMap<>());

        doAnswer(invocation -> {
            TaskExecution taskExecutionRet = new TaskExecution();
            taskExecutionRet.setTask(task);
            taskExecutionRet.setId(taskExecution.getId());
            taskExecutionRet.setStatus(TaskExecutionStatus.STARTED);
            taskExecutionRet.addLog(new TaskExecutionLog());

            return taskExecutionRet;
        }).when(executionService).startExecution(eq(request), eq(taskService), eq(false));

        doAnswer(invocation -> {
            TaskExecution taskExecutionRet = new TaskExecution();
            taskExecutionRet.setTask(task);
            taskExecutionRet.setId(taskExecution.getId());
            taskExecutionRet.addLog(new TaskExecutionLog());
            taskExecutionRet.addLog(new TaskExecutionLog());

            return taskExecutionRet;
        }).when(executionService).processActions(eq(task.getId()), eq(taskExecution.getId()), eq(taskService));
        TaskExecution taskExecutionReturned = taskService.runRequest(request);

        // Setup assert params
        Optional<Long> executionIdOptional = Optional.ofNullable(taskExecutionReturned.getId());
        long executionId = executionIdOptional.orElse(-1L);

        Optional<Long> taskIdOptional = Optional.ofNullable(taskExecutionReturned.getTask().getId());
        long taskId = taskIdOptional.orElse(-1L);

        Assert.assertNotNull(taskExecutionReturned);
        Assert.assertEquals(283471L, executionId);
        Assert.assertEquals(1928L, taskId);
        Assert.assertEquals(2, taskExecutionReturned.getLogs().size());

    }

    @Test
    public void runSimpleTaskWithPrintActionShouldReturnOk() {
        // Setup data
        Action action = new PrintAction("Automation is the future");

        Task task = new Task();
        task.setId(1928L);
        task.setName("doppler-test");
        task.getActionList().add(action);

        TaskExecution taskExecution = new TaskExecution();
        taskExecution.setId(283472L);

        // Prepare behavior
        Optional<Task> taskOptional = Optional.of(task);
        when(taskDao.findFirstByNameOrderByCreatedDesc(eq("doppler-test"))).thenReturn(taskOptional);

        Optional<TaskExecution> executionOptional = Optional.of(taskExecution);
        when(taskExecutionDao.findById(eq(283472L))).thenReturn(executionOptional);


        // Run test
        TaskExecutionRequest request = new TaskExecutionRequest();
        request.setExecutionId(283472L);
        request.setTaskName("doppler-test");
        request.setParameters(new HashMap<>());

        doAnswer(invocation -> {
            TaskExecution taskExecutionRet = new TaskExecution();
            taskExecutionRet.setTask(task);
            taskExecutionRet.setId(taskExecution.getId());
            taskExecutionRet.setStatus(TaskExecutionStatus.STARTED);
            taskExecutionRet.addLog(new TaskExecutionLog());

            return taskExecutionRet;
        }).when(executionService).startExecution(eq(request), eq(taskService), eq(false));

        doAnswer(invocation -> {
            TaskExecution taskExecutionRet = new TaskExecution();
            taskExecutionRet.setTask(task);
            taskExecutionRet.setId(taskExecution.getId());
            taskExecutionRet.addLog(new TaskExecutionLog());
            taskExecutionRet.addLog(new TaskExecutionLog());
            taskExecutionRet.addLog(new TaskExecutionLog());

            return taskExecutionRet;
        }).when(executionService).processActions(eq(task.getId()), eq(taskExecution.getId()), eq(taskService));
        TaskExecution taskExecutionReturned = taskService.runRequest(request);

        // Setup assert params
        Optional<Long> executionIdOptional = Optional.ofNullable(taskExecutionReturned.getId());
        long executionId = executionIdOptional.orElse(-1L);

        Optional<Long> taskIdOptional = Optional.ofNullable(taskExecutionReturned.getTask().getId());
        long taskId = taskIdOptional.orElse(-1L);

        Assert.assertNotNull(taskExecutionReturned);
        Assert.assertEquals(283472L, executionId);
        Assert.assertEquals(1928L, taskId);
        Assert.assertEquals(3, taskExecutionReturned.getLogs().size());

    }

}
