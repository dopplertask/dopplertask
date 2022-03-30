package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.Task;
import com.dopplertask.dopplertask.domain.TaskExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskExecutionDao extends JpaRepository<TaskExecution, Long> {
    List<TaskExecution> findAllByTaskNotNull();

    List<TaskExecution> findAllByTask(Task s);
}
