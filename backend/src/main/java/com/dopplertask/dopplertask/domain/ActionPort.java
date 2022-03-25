package com.dopplertask.dopplertask.domain;

import com.dopplertask.dopplertask.domain.action.Action;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table
public class ActionPort {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @JsonIgnore
    private Long id;

    @Column
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column
    private ActionPortType portType = ActionPortType.INPUT;

    @JoinColumn(name = "action_id", referencedColumnName = "id")
    @ManyToOne
    @JsonIgnore
    private Action action;

    @OneToMany(mappedBy = "target", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY)
    @JsonIgnore
    private List<Connection> connectionTarget;

    @OneToOne(mappedBy = "source", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY)
    @JsonIgnore
    private Connection connectionSource;


    public ActionPort() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ActionPortType getPortType() {
        return portType;
    }

    public void setPortType(ActionPortType portType) {
        this.portType = portType;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }


    public Action getAction() {
        return action;
    }

    public void setAction(Action action) {
        this.action = action;
    }


    public Connection getConnectionSource() {
        return connectionSource;
    }

    public void setConnectionSource(Connection connectionSource) {
        this.connectionSource = connectionSource;
    }

    public List<Connection> getConnectionTarget() {
        return connectionTarget;
    }

    public void setConnectionTarget(List<Connection> connectionTarget) {
        this.connectionTarget = connectionTarget;
    }
}
