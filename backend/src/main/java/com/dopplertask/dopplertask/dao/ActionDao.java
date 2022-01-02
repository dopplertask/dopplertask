package com.dopplertask.dopplertask.dao;

import com.dopplertask.dopplertask.domain.action.Action;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActionDao extends JpaRepository<Action, Long> {
}
