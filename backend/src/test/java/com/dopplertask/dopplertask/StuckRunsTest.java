package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import com.dopplertask.dopplertask.service.CustomerScope;
import com.dopplertask.dopplertask.service.ExecutionService;
import com.dopplertask.dopplertask.service.RunHousekeeping;
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
        return new RunHousekeeping(executions, 0, maxRunMinutes, service, new StaticListableBeanFactory().getBeanProvider(CustomerScope.class));
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
        TaskExecution execution = going(1);
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
        Assert.assertEquals(TaskExecutionStatus.STARTED, execution.getStatus());
        housekeeping.finished(1);
        Assert.assertEquals(TaskExecutionStatus.FAILED, execution.getStatus());
        Assert.assertTrue(execution.getFailureReason().contains("60 minutes"));
    }

    @Test
    public void testARunThatIgnoresTheInterruptIsStillEndedSuccess() {
        RunHousekeeping housekeeping = housekeeping(60);
        housekeeping.watch(2, new Thread());
        Instant later = Instant.now().plus(Duration.ofMinutes(61));

        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", later);
        ReflectionTestUtils.invokeMethod(housekeeping, "stopRunsOverTheLimit", later);

        Mockito.verify(service, Mockito.timeout(5_000)).failRun(ArgumentMatchers.eq(2L), ArgumentMatchers.contains("60 minutes"));
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
        RunHousekeeping housekeeping = new RunHousekeeping(executions, 0, 60, service, beans.getBeanProvider(CustomerScope.class));
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
