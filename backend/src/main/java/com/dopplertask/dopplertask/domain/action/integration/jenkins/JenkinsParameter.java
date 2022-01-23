package com.dopplertask.dopplertask.domain.action.integration.jenkins;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.persistence.*;

@Entity
@Table(name = "JenkinsParameter")
public class JenkinsParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @Column
    private String name;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String value;

    @ManyToOne
    @JoinColumn(nullable = false)
    @JsonIgnore
    private JenkinsAction jenkinsAction;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public JenkinsAction getJenkinsAction() {
        return jenkinsAction;
    }

    public void setJenkinsAction(JenkinsAction jenkinsAction) {
        this.jenkinsAction = jenkinsAction;
    }
}
