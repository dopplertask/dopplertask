package com.dopplertask.dopplertask.domain.action.integration.rockmelon;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "RockmelonParameter")
public class RockmelonParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String value;

    @ManyToOne
    @JoinColumn(nullable = false)
    @JsonIgnore
    private RockmelonAction rockmelonAction;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public RockmelonAction getRockmelonAction() {
        return rockmelonAction;
    }

    public void setRockmelonAction(RockmelonAction rockmelonAction) {
        this.rockmelonAction = rockmelonAction;
    }
}
