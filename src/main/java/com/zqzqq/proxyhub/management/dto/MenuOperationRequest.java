package com.zqzqq.proxyhub.management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MenuOperationRequest {

    @NotBlank
    private String operationId;

    @Size(max = 128)
    private String argument;

    @Size(max = 8000)
    private String stdin;

    private boolean confirmRisk;

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getArgument() {
        return argument;
    }

    public void setArgument(String argument) {
        this.argument = argument;
    }

    public String getStdin() {
        return stdin;
    }

    public void setStdin(String stdin) {
        this.stdin = stdin;
    }

    public boolean isConfirmRisk() {
        return confirmRisk;
    }

    public void setConfirmRisk(boolean confirmRisk) {
        this.confirmRisk = confirmRisk;
    }
}
