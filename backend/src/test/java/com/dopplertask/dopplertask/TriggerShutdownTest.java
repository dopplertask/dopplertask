package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.dao.TaskDao;
import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.trigger.IntervalTrigger;
import com.dopplertask.dopplertask.service.TaskRequest;
import com.dopplertask.dopplertask.service.TaskService;
import com.dopplertask.dopplertask.service.TriggerListenerServiceImpl;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TriggerShutdownTest {

    @Test
    public void testTriggerStopsWhenItsRunSwallowsTheInterrupt() throws Exception {
        IntervalTrigger trigger = new IntervalTrigger();
        trigger.setTime("0");
        trigger.setTimeUnit("SECONDS");
        Task task = new Task();
        task.setName("interval");
        task.setActive(true);
        task.setActionList(List.<Action>of(trigger));

        AtomicInteger runs = new AtomicInteger();
        TaskDao taskDao = Mockito.mock(TaskDao.class);
        Mockito.when(taskDao.findById(1L)).thenReturn(Optional.of(task));
        TaskService taskService = Mockito.mock(TaskService.class);
        Mockito.when(taskService.runRequest(Mockito.any(TaskRequest.class))).thenAnswer(invocation -> {
            runs.incrementAndGet();
            try {
                // Stands in for code in a run that catches the interrupt, as JDBC and sleeping steps can.
                Thread.sleep(50);
            } catch (InterruptedException swallowed) {
                return null;
            }
            return null;
        });

        TriggerListenerServiceImpl triggers = new TriggerListenerServiceImpl(taskDao, taskService);
        triggers.updateTriggers(1L);
        while (runs.get() == 0) {
            Thread.sleep(5);
        }

        task.setActive(false);
        triggers.updateTriggers(1L);
        Thread.sleep(100);
        int runsAfterStopping = runs.get();
        Thread.sleep(300);

        assertTrue(runsAfterStopping > 0);
        assertEquals(runsAfterStopping, runs.get());
    }

    @Test
    public void testTriggerKeepsGoingAfterARunFailsSuccess() throws Exception {
        IntervalTrigger trigger = new IntervalTrigger();
        trigger.setTime("0");
        trigger.setTimeUnit("SECONDS");
        Task task = new Task();
        task.setName("interval");
        task.setActive(true);
        task.setActionList(List.<Action>of(trigger));

        AtomicInteger runs = new AtomicInteger();
        TaskDao taskDao = Mockito.mock(TaskDao.class);
        Mockito.when(taskDao.findById(1L)).thenReturn(Optional.of(task));
        TaskService taskService = Mockito.mock(TaskService.class);
        Mockito.when(taskService.runRequest(Mockito.any(TaskRequest.class))).thenAnswer(invocation -> {
            if (runs.incrementAndGet() == 1) {
                throw new IllegalStateException("The database was busy");
            }
            return null;
        });

        TriggerListenerServiceImpl triggers = new TriggerListenerServiceImpl(taskDao, taskService);
        triggers.updateTriggers(1L);
        long giveUpAt = System.currentTimeMillis() + 10000;
        while (runs.get() < 2 && System.currentTimeMillis() < giveUpAt) {
            Thread.sleep(50);
        }
        task.setActive(false);
        triggers.updateTriggers(1L);

        assertTrue("A schedule must survive one failed run", runs.get() >= 2);
    }
}
