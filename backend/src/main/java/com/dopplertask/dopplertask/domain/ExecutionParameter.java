package com.dopplertask.dopplertask.domain;

import javax.persistence.*;
import java.nio.charset.StandardCharsets;

@Entity
@Table(name = "ExecutionParameter")
public class ExecutionParameter {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Column(name = "paramName")
    private String paramName;

    @Lob
    @Column(name = "paramValue", columnDefinition = "LONGBLOB NOT NULL")
    private byte[] paramValue;

    // When adding parameters, the default value type is String (non-binary)
    private boolean isBinary = false;

    public ExecutionParameter() {
    }

    public ExecutionParameter(String paramName, byte[] paramValue, boolean isBinary) {
        this.paramName = paramName;
        this.paramValue = paramValue;
        this.isBinary = isBinary;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getParamName() {
        return paramName;
    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public byte[] getParamValue() {
        return paramValue;
    }

    public void setParamValue(byte[] paramValue) {
        this.paramValue = paramValue;
    }

    public boolean isBinary() {
        return isBinary;
    }

    public void setBinary(boolean binary) {
        isBinary = binary;
    }

    @Override
    public String toString() {
        return new String(paramValue, StandardCharsets.UTF_8);
    }

}
