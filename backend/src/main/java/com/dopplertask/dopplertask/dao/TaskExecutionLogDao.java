package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.TaskExecution;
import com.dopplertask.dopplertask.domain.TaskExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskExecutionLogDao extends JpaRepository<TaskExecutionLog, Long> {
    List<TaskExecutionLog> findByTaskExecution(TaskExecution taskExecution);
}
