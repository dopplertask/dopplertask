package com.dopplertask.dopplertask;

import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionLog;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import com.dopplertask.dopplertask.service.RunHousekeeping;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.junit4.SpringRunner;

import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@RunWith(SpringRunner.class)
@DataJpaTest(properties = "spring.datasource.url=jdbc:h2:mem:runhousekeeping")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = RunHousekeepingTest.DatabaseOnly.class)
public class RunHousekeepingTest {

    // Only the database, without the broker and the rest of the application.
    @Configuration
    @EntityScan("com.dopplertask.dopplertask.domain")
    @EnableJpaRepositories("com.dopplertask.dopplertask.dao")
    static class DatabaseOnly {
    }

    @Autowired
    private TaskExecutionDao taskExecutionDao;

    @Autowired
    private EntityManager entityManager;

    private Long saveRun(TaskExecutionStatus status, Date ended) {
        TaskExecution execution = new TaskExecution();
        execution.setStatus(status);
        execution.setEnddate(ended);
        TaskExecutionLog log = new TaskExecutionLog();
        log.setTaskExecution(execution);
        log.setOutput("output");
        execution.addLog(log);
        Long id = taskExecutionDao.save(execution).getId();
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private Date daysAgo(int days) {
        return Date.from(Instant.now().minus(Duration.ofDays(days)));
    }

    @Test
    public void testInterruptedRunsAreMarkedAsFailedWithTheReasonSuccess() {
        Long started = saveRun(TaskExecutionStatus.STARTED, null);
        Long queued = saveRun(TaskExecutionStatus.CREATED, null);
        Long finished = saveRun(TaskExecutionStatus.FINISHED, new Date());

        new RunHousekeeping(taskExecutionDao, 0, 0, null, new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(com.dopplertask.dopplertask.service.CustomerScope.class)).failInterruptedRuns();
        entityManager.flush();
        entityManager.clear();

        TaskExecution interrupted = taskExecutionDao.findById(started).orElseThrow();
        Assert.assertEquals(TaskExecutionStatus.FAILED, interrupted.getStatus());
        Assert.assertFalse(interrupted.isSuccess());
        Assert.assertNotNull(interrupted.getEnddate());
        Assert.assertTrue(interrupted.getLogs().stream().anyMatch(log -> log.getOutput().contains("the server stopped")));
        Assert.assertEquals(2, interrupted.getLogs().size());
        Assert.assertEquals(TaskExecutionStatus.FAILED, taskExecutionDao.findById(queued).orElseThrow().getStatus());
        Assert.assertEquals(TaskExecutionStatus.FINISHED, taskExecutionDao.findById(finished).orElseThrow().getStatus());
    }

    @Test
    public void testOldRunsAreDeletedWithTheirOutputWhenAskedForSuccess() {
        Long old = saveRun(TaskExecutionStatus.FINISHED, daysAgo(40));
        Long recent = saveRun(TaskExecutionStatus.FINISHED, daysAgo(5));
        Long going = saveRun(TaskExecutionStatus.STARTED, null);

        new RunHousekeeping(taskExecutionDao, 30, 0, null, new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(com.dopplertask.dopplertask.service.CustomerScope.class)).deleteOldRuns();
        entityManager.flush();
        entityManager.clear();

        Assert.assertFalse(taskExecutionDao.existsById(old));
        Assert.assertTrue(taskExecutionDao.existsById(recent));
        Assert.assertTrue(taskExecutionDao.existsById(going));
        Assert.assertEquals(2L, entityManager.createQuery("select count(l) from TaskExecutionLog l", Long.class).getSingleResult().longValue());
    }

    @Test
    public void testAllRunsAreKeptUnlessAskedForSuccess() {
        Long old = saveRun(TaskExecutionStatus.FINISHED, daysAgo(4000));
        new RunHousekeeping(taskExecutionDao, 0, 0, null, new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(com.dopplertask.dopplertask.service.CustomerScope.class)).deleteOldRuns();
        entityManager.flush();
        Assert.assertTrue(taskExecutionDao.existsById(old));
    }
}
