package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskDao extends JpaRepository<Task, Long> {
    Optional<Task> findFirstByNameOrderByCreatedDesc(String taskName);

    Optional<Task> findByChecksum(String checksum);

    Optional<Task> findFirstByChecksumStartingWith(String checksum);

    Optional<Task> findByNameOrderByCreatedDesc(String taskName);

    List<Task> findAllByName(String name);

    Optional<Task> findByIdAndActive(long id, boolean active);

    List<Task> findAllByActive(boolean active);
}
