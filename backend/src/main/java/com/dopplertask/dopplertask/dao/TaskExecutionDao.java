package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface TaskExecutionDao extends JpaRepository<TaskExecution, Long> {
    List<TaskExecution> findAllByTaskNotNull();

    List<TaskExecution> findByTask_NameOrderByIdDesc(String taskName, Pageable pageable);

    List<TaskExecution> findAllByTask(Task s);

    List<TaskExecution> findByStatusIn(List<TaskExecutionStatus> statuses);

    List<TaskExecution> findTop1000ByEnddateBefore(Date cutoff);
}
