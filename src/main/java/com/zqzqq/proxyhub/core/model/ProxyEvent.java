package com.zqzqq.proxyhub.core.model;

import java.time.Instant;

public class ProxyEvent {

    private final Instant timestamp;
    private final String level;
    private final String message;

    public ProxyEvent(String level, String message) {
        this.timestamp = Instant.now();
        this.level = level;
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}
