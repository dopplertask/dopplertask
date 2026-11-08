package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /** Starting a run and stopping it while it waits both read its state through this, so that one waits for the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select execution from TaskExecution execution where execution.id = :id")
    Optional<TaskExecution> findForUpdate(@Param("id") long id);
}
