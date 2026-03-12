package com.zqzqq.proxyhub.core.metrics;

public final class ProxyFailureReason {

    public static final String ACL_DENIED = "ACL_DENIED";
    public static final String CONNECTION_QUOTA_EXCEEDED = "CONNECTION_QUOTA_EXCEEDED";
    public static final String INVALID_TARGET = "INVALID_TARGET";
    public static final String HTTP_PENDING_BUFFER_EXCEEDED = "HTTP_PENDING_BUFFER_EXCEEDED";
    public static final String AUTH_REQUIRED = "AUTH_REQUIRED";
    public static final String AUTH_METHOD_UNACCEPTED = "AUTH_METHOD_UNACCEPTED";
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";
    public static final String UPSTREAM_CONNECT_FAILED = "UPSTREAM_CONNECT_FAILED";
    public static final String UPSTREAM_WRITE_FAILED = "UPSTREAM_WRITE_FAILED";
    public static final String CLIENT_IO_ERROR = "CLIENT_IO_ERROR";
    public static final String LISTENER_START_FAILED = "LISTENER_START_FAILED";
    public static final String LISTENER_RELOAD_FAILED = "LISTENER_RELOAD_FAILED";
    public static final String EXTERNAL_ENGINE_PRECHECK_FAILED = "EXTERNAL_ENGINE_PRECHECK_FAILED";
    public static final String EXTERNAL_ENGINE_EXITED_UNEXPECTEDLY = "EXTERNAL_ENGINE_EXITED_UNEXPECTEDLY";

    private ProxyFailureReason() {
    }
}
