package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionDao extends JpaRepository<Action, Long> {
    @Query("select distinct h.httpAction from HttpHeader h where lower(cast(h.headerName as string)) like '%authorization%' or lower(cast(h.headerName as string)) like '%key%' or lower(cast(h.headerName as string)) like '%token%' or lower(cast(h.headerName as string)) like '%secret%' or lower(cast(h.headerName as string)) like '%password%'")
    List<HttpAction> findHttpActionsWithSecretHeaders();
}
