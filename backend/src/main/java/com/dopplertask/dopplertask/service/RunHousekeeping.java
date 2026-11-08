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
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final ObjectProvider<RunQueue> runQueue;
    private final TransactionOperations transactions;
    private final Map<RunOfCustomer, GoingRun> going = new ConcurrentHashMap<>();

    /** Why a run was asked to stop. A person's stop aborts it, the time limit fails it. */
    private record Stop(String reason, boolean byPerson, Instant askedAt) { }

    private final class GoingRun {
        final long executionId;
        final Thread thread;
        /** From any thread, in the run's own customer scope. */
        final Runnable endAsAsked;
        final Instant started = Instant.now();
        volatile Stop stop;

        GoingRun(long executionId, Thread thread) {
            this.executionId = executionId;
            this.thread = thread;
            this.endAsAsked = customerScope.bound(this::end);
        }

        void askToStop(Stop why) {
            if (stop == null) {
                stop = why;
                thread.interrupt();
            }
        }

        private void end() {
            if (stop.byPerson()) {
                executionService.abortRun(executionId, stop.reason());
            } else {
                executionService.failRun(executionId, stop.reason());
            }
        }
    }

    public RunHousekeeping(TaskExecutionDao taskExecutionDao, @Value("${dopplertask.keepRunsDays:0}") int keepRunsDays,
                           @Value("${dopplertask.maxRunMinutes:1440}") long maxRunMinutes,
                           ExecutionService executionService, ObjectProvider<CustomerScope> customers, ObjectProvider<RunQueue> runQueue,
                           TransactionOperations transactions) {
        this.taskExecutionDao = taskExecutionDao;
        this.keepRunsDays = keepRunsDays;
        this.maxRunMinutes = maxRunMinutes;
        this.executionService = executionService;
        this.customerScope = customers.getIfAvailable(() -> CustomerScope.ONE_CUSTOMER);
        this.runQueue = runQueue;
        this.transactions = transactions;
    }

    /** From here on a run counts against the time limit, and can be stopped. */
    public void watch(long executionId, Thread thread) {
        going.putIfAbsent(new RunOfCustomer(customerScope.current(), executionId), new GoingRun(executionId, thread));
    }

    /** A run that had to be interrupted says why, since its last step can only say that it was disturbed. */
    public void finished(long executionId) {
        GoingRun run = going.remove(new RunOfCustomer(customerScope.current(), executionId));
        if (run != null && run.stop != null) {
            run.endAsAsked.run();
        }
    }

    /**
     * A person stops a run. One that is going stops before its next step, or during a step that listens, and one that
     * still waits ends before it starts.
     */
    public void stop(long executionId, String reason) {
        GoingRun run = going.get(new RunOfCustomer(customerScope.current(), executionId));
        if (run != null) {
            run.askToStop(new Stop(reason, true, Instant.now()));
            return;
        }
        boolean waiting = Boolean.TRUE.equals(transactions.execute(status -> taskExecutionDao.findById(executionId)
                .filter(execution -> execution.getStatus() == TaskExecutionStatus.CREATED)
                .map(execution -> {
                    execution.abort(reason);
                    return true;
                })
                .orElse(false)));
        if (!waiting) {
            throw new RunAlreadyEndedException();
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
     * A step that never returns would hold one of the places for runs for good. It is asked to stop first. If it has not
     * listened a whole check later, the run is at least ended for whoever waits for it, even though its thread stays busy.
     * The same goes for a run that a person stopped.
     */
    void stopRunsOverTheLimit(Instant now) {
        Instant cutoff = now.minus(Duration.ofMinutes(maxRunMinutes));
        Instant askedBefore = now.minusMillis(CHECK_MILLISECONDS);
        going.forEach((key, run) -> {
            Stop stop = run.stop;
            if (stop == null) {
                if (maxRunMinutes > 0 && !run.started.isAfter(cutoff)) {
                    run.askToStop(new Stop(tooLong(), false, now));
                }
                return;
            }
            if (stop.askedAt().isAfter(askedBefore)) {
                return;
            }
            LOG.error("Run {} did not stop when asked, so it is ended while its thread stays busy.", key.executionId());
            going.remove(key);
            // On a thread of its own, since the run that hangs can hold the row and the other runs must still get checked.
            Thread.startVirtualThread(run.endAsAsked);
        });
    }

    /**
     * Runs live in this one process, so at startup nothing can still be going. A run that says so was stopped by a crash
     * or a restart, and whoever waits for it would otherwise wait forever. The built-in queue is in memory and loses its
     * runs, but a queue that keeps them starts them later, so those are not interrupted.
     */
    public void failInterruptedRuns() {
        forEachCustomer(() -> {
            RunQueue queue = runQueue.getIfAvailable();
            Set<Long> waiting = queue == null ? Set.of() : queue.waiting();
            List<TaskExecution> interrupted = taskExecutionDao.findByStatusIn(List.of(TaskExecutionStatus.CREATED, TaskExecutionStatus.STARTED)).stream()
                    .filter(execution -> !waiting.contains(execution.getId()))
                    .toList();
            interrupted.forEach(execution -> execution.fail(INTERRUPTED));
            if (!interrupted.isEmpty()) {
                LOG.warn("Marked {} runs as failed, since the server stopped during them.", interrupted.size());
            }
        });
    }

    /**
     * Off unless asked for, since keeping everything that happened is what the product promises. Without it the database
     * grows with every run. One batch at a time, so that a large first cleaning does not get in the way of running work.
     */
    @Scheduled(fixedDelay = CHECK_MILLISECONDS, initialDelay = CHECK_MILLISECONDS)
    public void deleteOldRuns() {
        if (keepRunsDays <= 0) {
            return;
        }
        Date cutoff = Date.from(Instant.now().minus(Duration.ofDays(keepRunsDays)));
        forEachCustomer(() -> taskExecutionDao.deleteAll(taskExecutionDao.findTop1000ByEnddateBefore(cutoff)));
    }

    /** Each customer's runs are its own, so each gets a transaction of its own, and a failure in one stops no other. */
    private void forEachCustomer(Runnable work) {
        customerScope.forEachCustomer(() -> {
            try {
                transactions.executeWithoutResult(status -> work.run());
            } catch (RuntimeException e) {
                LOG.error("Housekeeping failed for customer scope '{}', and goes on with the others.", customerScope.current(), e);
            }
        });
    }
}
