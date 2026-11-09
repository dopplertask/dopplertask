package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskExecutionDao extends JpaRepository<TaskExecution, Long> {
    List<TaskExecution> findAllByTaskNotNull();

    List<TaskExecution> findByTask_NameOrderByIdDesc(String taskName, Pageable pageable);

    List<TaskExecution> findAllByTask(Task s);

    List<TaskExecution> findByStatusIn(List<TaskExecutionStatus> statuses);

    List<TaskExecution> findTop1000ByEnddateBefore(Date cutoff);

    Optional<TaskExecution> findByWaitKey(String waitKey);

    /**
     * Keeps what arrived with the waiting run and takes its key, at once, so that the run continues once although its
     * address is called twice, and so that the answer is not lost when the server stops before the run continues.
     */
    @Modifying
    @Transactional
    @Query("update TaskExecution run set run.waitKey = null, run.resumeEvent = ?3 where run.id = ?1 and run.waitKey = ?2")
    int answerWaiting(Long id, String waitKey, String resumeEvent);

    /** Waiting runs that were answered, and that no run still queued is going to continue. */
    @Query("select run from TaskExecution run where run.status = com.dopplertask.dopplertask.domain.TaskExecutionStatus.WAITING"
            + " and run.waitKey is null and run.resumeEvent is not null and not exists (select queued.id from TaskExecution queued"
            + " where queued.resumedFrom = run.id and queued.status = com.dopplertask.dopplertask.domain.TaskExecutionStatus.CREATED)")
    List<TaskExecution> answeredRunsToContinue();

    /** The ids of the runs still queued to continue a run. */
    @Query("select execution.id from TaskExecution execution where execution.resumedFrom = :resumedFrom"
            + " and execution.status = com.dopplertask.dopplertask.domain.TaskExecutionStatus.CREATED")
    List<Long> queuedContinuationIds(@Param("resumedFrom") long resumedFrom);

    /**
     * Which run a queued run continues, without locking it. A stop then locks every such run, by id, before the run
     * they continue.
     */
    @Query("select execution.resumedFrom from TaskExecution execution where execution.id = :id"
            + " and execution.status = com.dopplertask.dopplertask.domain.TaskExecutionStatus.CREATED"
            + " and execution.resumedFrom is not null")
    Optional<Long> resumedFromOfQueuedRun(@Param("id") long id);

    /**
     * Takes the answer of a waiting run, which then ends as finished, when a run continues it, or as failed, when it
     * cannot continue. Only one run takes it.
     */
    @Modifying
    @Transactional
    @Query("update TaskExecution run set run.status = ?2, run.resumeEvent = null, run.enddate = ?3 where run.id = ?1"
            + " and run.status = com.dopplertask.dopplertask.domain.TaskExecutionStatus.WAITING and run.waitKey is null and run.resumeEvent is not null")
    int takeAnswer(Long id, TaskExecutionStatus endsAs, Date enddate);

    /** Starting a run and stopping it while it waits both read its state through this, so that one waits for the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from TaskExecution execution where execution.id = :id")
    Optional<TaskExecution> findForUpdate(@Param("id") long id);
}
