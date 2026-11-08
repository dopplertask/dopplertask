package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
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

    /** Takes the key of a waiting run, so that it continues once although its address is called twice at the same time. */
    @Modifying
    @Transactional
    @Query("update TaskExecution run set run.waitKey = null where run.id = ?1 and run.waitKey = ?2")
    int takeWaitKey(Long id, String waitKey);
}
