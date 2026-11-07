package com.dopplertask.dopplertask.service;

import com.dopplertask.dopplertask.dao.TaskExecutionDao;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    private final long maxRunMinutes;
    private final ExecutionService executionService;
    private final CustomerScope customerScope;
    private final Map<RunOfCustomer, GoingRun> going = new ConcurrentHashMap<>();

    /** Two customers can have a run with the same number. */

    private static final class GoingRun {
        final Thread thread;
        final Runnable markFailed;
        final Instant started = Instant.now();
        volatile boolean interrupted;

        GoingRun(Thread thread, Runnable markFailed) {
            this.thread = thread;
            this.markFailed = markFailed;
        }
    }

    public RunHousekeeping(TaskExecutionDao taskExecutionDao, @Value("${dopplertask.keepRunsDays:0}") int keepRunsDays,
                           @Value("${dopplertask.maxRunMinutes:1440}") long maxRunMinutes,
                           ExecutionService executionService, ObjectProvider<CustomerScope> customers) {
        this.taskExecutionDao = taskExecutionDao;
        this.keepRunsDays = keepRunsDays;
        this.maxRunMinutes = maxRunMinutes;
        this.executionService = executionService;
        this.customerScope = customers.getIfAvailable(() -> CustomerScope.ONE_CUSTOMER);
    }

    /** From here on a run counts against the time limit. */
    public void watch(long executionId, Thread thread) {
        Runnable markFailed = customerScope.bound(() -> executionService.failRun(executionId, tooLong()));
        going.putIfAbsent(new RunOfCustomer(customerScope.current(), executionId), new GoingRun(thread, markFailed));
    }

    /** A run that had to be interrupted says so, since its last step can only say that it was disturbed. */
    public void finished(long executionId) {
        GoingRun run = going.remove(new RunOfCustomer(customerScope.current(), executionId));
        if (run != null && run.interrupted) {
            executionService.failRun(executionId, tooLong());
        }
    }

    private String tooLong() {
        return "This run was stopped, since it went on for longer than " + maxRunMinutes + " minutes. dopplertask.maxRunMinutes sets the limit.";
    }

    @Scheduled(fixedDelay = CHECK_MILLISECONDS, initialDelay = CHECK_MILLISECONDS)
    public void stopRunsOverTheLimit() {
        stopRunsOverTheLimit(Instant.now());
    }

    /**
     * A step that never returns would hold one of the places for runs for good. It is asked to stop first. If it does not
     * listen, the run is at least ended for whoever waits for it at the next check, even though its thread stays busy.
     */
    void stopRunsOverTheLimit(Instant now) {
        if (maxRunMinutes <= 0) {
            return;
        }
        Instant cutoff = now.minus(Duration.ofMinutes(maxRunMinutes));
        going.forEach((key, run) -> {
            if (run.started.isAfter(cutoff)) {
                return;
            }
            if (!run.interrupted) {
                run.interrupted = true;
                run.thread.interrupt();
                return;
            }
            LOG.error("Run {} did not stop when asked, so it is marked as failed while its thread stays busy.", key.executionId());
            going.remove(key);
            // On a thread of its own, since the run that hangs can hold the row and the other runs must still get checked.
            Thread.startVirtualThread(run.markFailed);
        });
    }

    /**
     * Runs live in this one process and its queue in memory, so at startup nothing can still be going. A run that says
     * so was stopped by a crash or a restart, and whoever waits for it would otherwise wait forever.
     */
    @Transactional
    public void failInterruptedRuns() {
        List<TaskExecution> interrupted = taskExecutionDao.findByStatusIn(List.of(TaskExecutionStatus.CREATED, TaskExecutionStatus.STARTED));
        interrupted.forEach(execution -> execution.fail(INTERRUPTED));
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
