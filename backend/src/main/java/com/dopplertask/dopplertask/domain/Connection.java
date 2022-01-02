package com.dopplertask.dopplertask.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;

@Entity
@Table
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @OneToOne
    @JoinColumn(name = "source")
    private ActionPort source;

    @ManyToOne
    @JoinColumn(name = "target")
    private ActionPort target;

    @ManyToOne
    @JoinColumn(name = "task")
    @JsonIgnore
    private Task task;

    public Connection() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ActionPort getSource() {
        return source;
    }

    public void setSource(ActionPort source) {
        this.source = source;
    }

    public ActionPort getTarget() {
        return target;
    }

    public void setTarget(ActionPort target) {
        this.target = target;
    }

    public Task getTask() {
        return task;
    }

    public void setTask(Task task) {
        this.task = task;
    }
}
