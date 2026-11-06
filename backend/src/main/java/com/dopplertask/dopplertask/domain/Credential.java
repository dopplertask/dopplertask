package com.dopplertask.dopplertask.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import java.util.Date;

/**
 * What a kind of step needs to sign in, kept apart from tasks. Steps name a credential and never hold its values.
 */
@Entity
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    @Column(nullable = false)
    private String stepType;

    @Lob
    @Column(nullable = false)
    private String encryptedValues;

    private Date updated;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public String getEncryptedValues() {
        return encryptedValues;
    }

    public void setEncryptedValues(String encryptedValues) {
        this.encryptedValues = encryptedValues;
    }

    public Date getUpdated() {
        return updated;
    }

    public void setUpdated(Date updated) {
        this.updated = updated;
    }
}
