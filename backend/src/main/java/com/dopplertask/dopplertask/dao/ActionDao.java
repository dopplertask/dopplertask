package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.action.Action;
import com.dopplertask.dopplertask.domain.action.connection.HttpAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActionDao extends JpaRepository<Action, Long> {
    @Query("select distinct h.httpAction from HttpHeader h where lower(h.headerName) like '%authorization%' or lower(h.headerName) like '%key%' or lower(h.headerName) like '%token%' or lower(h.headerName) like '%secret%' or lower(h.headerName) like '%password%'")
    List<HttpAction> findHttpActionsWithSecretHeaders();
}
