package com.zqzqq.proxyhub.management.dto;

import java.util.List;

public record RuntimeStatusResponse(
        boolean running,
        List<ListenerStatus> listeners) {

    public record ListenerStatus(String name, boolean running) {
    }
}
