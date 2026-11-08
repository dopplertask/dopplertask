package com.dopplertask.dopplertask;

import org.springframework.transaction.support.TransactionOperations;
import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import com.dopplertask.dopplertask.service.CustomerScope;
import com.dopplertask.dopplertask.service.ExecutionService;
import com.dopplertask.dopplertask.service.RunAlreadyEndedException;
import com.dopplertask.dopplertask.service.RunHousekeeping;
import com.dopplertask.dopplertask.service.RunQueue;
import com.dopplertask.dopplertask.service.TaskExecutionRequest;
import com.dopplertask.dopplertask.service.TaskServiceImpl;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class StuckRunsTest {
    private final TaskExecutionDao executions = Mockito.mock(TaskExecutionDao.class);
    private final ExecutionService service = Mockito.mock(ExecutionService.class);

    private RunHousekeeping housekeeping(long maxRunMinutes) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        return new RunHousekeeping(executions, 0, maxRunMinutes, service, beans.getBeanProvider(CustomerScope.class), beans.getBeanProvider(RunQueue.class), TransactionOperations.withoutTransaction());
    }

    private TaskExecution going(long id) {
        TaskExecution execution = new TaskExecution();
        execution.setId(id);
        execution.setStatus(TaskExecutionStatus.STARTED);
        Mockito.when(executions.findById(id)).thenReturn(Optional.of(execution));
        return execution;
    }

    @Test
    public void testAProblemDuringARunEndsItOnceSuccess() {
        TaskExecution failed = new TaskExecution();
        Mockito.when(service.startExecution(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.anyBoolean())).thenThrow(new OutOfMemoryError("full"));
        Mockito.when(service.failRun(ArgumentMatchers.eq(7L), ArgumentMatchers.contains("full"))).thenReturn(failed);
        TaskServiceImpl tasks = new TaskServiceImpl();
        ReflectionTestUtils.setField(tasks, "executionService", service);
        ReflectionTestUtils.setField(tasks, "runHousekeeping", housekeeping(0));
        TaskExecutionRequest request = new TaskExecutionRequest();
        request.setExecutionId(7L);

        Assert.assertSame(failed, tasks.runRequest(request));
    }

    @Test
    public void testARunOverTheLimitIsInterruptedSuccess() throws Exception {
        RunHousekeeping housekeeping = housekeeping(60);
        Thread step = new Thread(() -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        step.start();
        housekeeping.watch(1, step);

        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", Instant.now().plus(Duration.ofMinutes(61)));
        step.join(5_000);

        Assert.assertFalse(step.isAlive());
        Mockito.verify(service, Mockito.never()).failRun(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString());
        housekeeping.finished(1);
        Mockito.verify(service).failRun(ArgumentMatchers.eq(1L), ArgumentMatchers.contains("60 minutes"));
    }

    @Test
    public void testARunThatIgnoresTheInterruptIsStillEndedSuccess() {
        RunHousekeeping housekeeping = housekeeping(60);
        housekeeping.watch(2, new Thread());
        Instant later = Instant.now().plus(Duration.ofMinutes(61));

        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", later);
        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", later.plus(Duration.ofMinutes(1)));

        Mockito.verify(service, Mockito.timeout(5_000)).failRun(ArgumentMatchers.eq(2L), ArgumentMatchers.contains("60 minutes"));
    }

    @Test
    public void testAPersonStopsAGoingRunAndItKeepsWhoSuccess() throws Exception {
        RunHousekeeping housekeeping = housekeeping(60);
        Thread step = new Thread(() -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        step.start();
        housekeeping.watch(8, step);

        housekeeping.stop(8, "This run was stopped by ann@example.com.");
        step.join(5_000);
        housekeeping.finished(8);

        Assert.assertFalse(step.isAlive());
        Mockito.verify(service).abortRun(8L, "This run was stopped by ann@example.com.");
        Mockito.verify(service, Mockito.never()).failRun(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString());
    }

    @Test
    public void testAStoppedRunWhoseStepDoesNotListenEndsAWholeCheckLaterSuccess() {
        RunHousekeeping housekeeping = housekeeping(0);
        housekeeping.watch(9, new Thread());
        housekeeping.stop(9, "This run was stopped.");

        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", Instant.now());
        Mockito.verify(service, Mockito.never()).abortRun(ArgumentMatchers.anyLong(), ArgumentMatchers.anyString());
        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", Instant.now().plus(Duration.ofMinutes(2)));

        Mockito.verify(service, Mockito.timeout(5_000)).abortRun(9L, "This run was stopped.");
    }

    @Test
    public void testAStopThatComesAsTheRunEndsLeavesTheThreadCleanSuccess() {
        RunHousekeeping housekeeping = housekeeping(60);
        housekeeping.watch(10, Thread.currentThread());
        housekeeping.stop(10, "This run was stopped.");
        housekeeping.finished(10);

        Assert.assertFalse("The next run on this thread must not inherit the stop", Thread.currentThread().isInterrupted());
        Mockito.verify(service).abortRun(10L, "This run was stopped.");
        Assert.assertThrows(RunAlreadyEndedException.class, () -> housekeeping.stop(10, "Too late."));
        Assert.assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    public void testTheSameRunNumberOfTwoCustomersIsKeptApartSuccess() throws Exception {
        String[] customer = {"first"};
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("customers", new CustomerScope() {
            @Override
            public String current() {
                return customer[0];
            }
        });
        RunHousekeeping housekeeping = new RunHousekeeping(executions, 0, 60, service, beans.getBeanProvider(CustomerScope.class), beans.getBeanProvider(RunQueue.class), TransactionOperations.withoutTransaction());
        Thread firstStep = new Thread(() -> {
        });
        Thread secondStep = Mockito.spy(new Thread(() -> {
        }));
        housekeeping.watch(5, firstStep);
        customer[0] = "second";
        housekeeping.watch(5, secondStep);

        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", Instant.now().plus(Duration.ofMinutes(61)));

        Mockito.verify(secondStep).interrupt();
    }

    @Test
    public void testARunWithinTheLimitIsLeftAloneSuccess() {
        TaskExecution execution = going(3);
        RunHousekeeping housekeeping = housekeeping(60);
        Thread step = Thread.currentThread();
        housekeeping.watch(3, step);

        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", Instant.now().plus(Duration.ofMinutes(59)));
        housekeeping.finished(3);

        Assert.assertFalse(Thread.currentThread().isInterrupted());
        Assert.assertEquals(TaskExecutionStatus.STARTED, execution.getStatus());
    }

    @Test
    public void testNoLimitMeansNothingStopsSuccess() {
        TaskExecution execution = going(4);
        RunHousekeeping housekeeping = housekeeping(0);
        housekeeping.watch(4, Thread.currentThread());

        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", Instant.now().plus(Duration.ofDays(365)));
        housekeeping.finished(4);

        Assert.assertFalse(Thread.currentThread().isInterrupted());
        Assert.assertEquals(TaskExecutionStatus.STARTED, execution.getStatus());
    }
}
