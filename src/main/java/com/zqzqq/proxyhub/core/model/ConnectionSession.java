package com.zqzqq.proxyhub.core.model;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class ConnectionSession {

    private final String id;
    private final ProxyProtocol protocol;
    private final String listener;
    private final String clientAddress;
    private final String targetAddress;
    private final Instant startTime;
    private final AtomicLong bytesFromClient = new AtomicLong();
    private final AtomicLong bytesFromTarget = new AtomicLong();
    private volatile SessionStatus status = SessionStatus.OPEN;
    private volatile Instant endTime;

    public ConnectionSession(String id, ProxyProtocol protocol, String listener, String clientAddress, String targetAddress) {
        this.id = id;
        this.protocol = protocol;
        this.listener = listener;
        this.clientAddress = clientAddress;
        this.targetAddress = targetAddress;
        this.startTime = Instant.now();
    }

    public void addBytesFromClient(long bytes) {
        if (bytes > 0) {
            bytesFromClient.addAndGet(bytes);
        }
    }

    public void addBytesFromTarget(long bytes) {
        if (bytes > 0) {
            bytesFromTarget.addAndGet(bytes);
        }
    }

    public void close(SessionStatus status) {
        this.status = status;
        this.endTime = Instant.now();
    }

    public String getId() {
        return id;
    }

    public ProxyProtocol getProtocol() {
        return protocol;
    }

    public String getListener() {
        return listener;
    }

    public String getClientAddress() {
        return clientAddress;
    }

    public String getTargetAddress() {
        return targetAddress;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public long getBytesFromClient() {
        return bytesFromClient.get();
    }

    public long getBytesFromTarget() {
        return bytesFromTarget.get();
    }

    public SessionStatus getStatus() {
        return status;
    }

    public Instant getEndTime() {
        return endTime;
    }
}
