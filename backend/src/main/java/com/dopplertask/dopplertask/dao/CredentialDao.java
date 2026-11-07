package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.Credential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CredentialDao extends JpaRepository<Credential, Long> {
    Optional<Credential> findByName(String name);

    List<Credential> findByType(String type);

    // Only the newest version of a task counts, since older ones cannot run unless they are opened and saved again.
    @Query("select distinct a.task.name from Action a where a.credential = ?1 and a.task.created = (select max(t.created) from Task t where t.name = a.task.name)")
    List<String> findTaskNamesUsing(String credentialName);
}
