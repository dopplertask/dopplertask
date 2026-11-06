package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionLog;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * What keeps the history of runs true and its size known on a server that never stops.
 */
@Service
public class RunHousekeeping {

    private static final Logger LOG = LoggerFactory.getLogger(RunHousekeeping.class);
    private static final String INTERRUPTED = "This run failed, since the server stopped before it finished. Run it again.";
    private static final long CHECK_MILLISECONDS = 60_000;

    private final TaskExecutionDao taskExecutionDao;
    private final int keepRunsDays;

    public RunHousekeeping(TaskExecutionDao taskExecutionDao, @Value("${dopplertask.keepRunsDays:0}") int keepRunsDays) {
        this.taskExecutionDao = taskExecutionDao;
        this.keepRunsDays = keepRunsDays;
    }

    /**
     * Runs live in this one process and its queue in memory, so at startup nothing can still be going. A run that says
     * so was stopped by a crash or a restart, and whoever waits for it would otherwise wait forever.
     */
    @Transactional
    public void failInterruptedRuns() {
        List<TaskExecution> interrupted = taskExecutionDao.findByStatusIn(List.of(TaskExecutionStatus.CREATED, TaskExecutionStatus.STARTED));
        for (TaskExecution execution : interrupted) {
            TaskExecutionLog log = new TaskExecutionLog();
            log.setTaskExecution(execution);
            log.setOutput(INTERRUPTED);
            log.setDate(new Date());
            execution.addLog(log);
            execution.setStatus(TaskExecutionStatus.FAILED);
            execution.setSuccess(false);
            execution.setEnddate(new Date());
        }
        if (!interrupted.isEmpty()) {
            LOG.warn("Marked {} runs as failed, since the server stopped during them.", interrupted.size());
        }
    }

    /**
     * Off unless asked for, since keeping everything that happened is what the product promises. Without it the database
     * grows with every run. One batch at a time, so that a large first cleaning does not get in the way of running work.
     */
    @Scheduled(fixedDelay = CHECK_MILLISECONDS, initialDelay = CHECK_MILLISECONDS)
    @Transactional
    public void deleteOldRuns() {
        if (keepRunsDays <= 0) {
            return;
        }
        Date cutoff = Date.from(Instant.now().minus(Duration.ofDays(keepRunsDays)));
        taskExecutionDao.deleteAll(taskExecutionDao.findTop1000ByEnddateBefore(cutoff));
    }
}
