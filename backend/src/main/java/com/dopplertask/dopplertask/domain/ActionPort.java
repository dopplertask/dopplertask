package com.dopplertask.dopplertask.domain;

import com.dopplertask.dopplertask.domain.action.Action;
import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.util.List;

@Entity
@Table
public class ActionPort {

    @Id
    // Saved ports already took their ids from this sequence, so a new one would start again at ids that are in use.
    @SequenceGenerator(name = "hibernate_sequence", sequenceName = "hibernate_sequence", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "hibernate_sequence")
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
