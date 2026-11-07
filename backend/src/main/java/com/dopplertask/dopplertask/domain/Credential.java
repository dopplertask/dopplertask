package com.dopplertask.dopplertask.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import java.util.Date;

/**
 * What steps need to sign in to a service, kept apart from tasks. Steps name a credential and never hold its values.
 */
@Entity
public class Credential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    // The column keeps its first name, so that credentials that are already saved stay where they are.
    @Column(name = "step_type", nullable = false)
    private String type;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
