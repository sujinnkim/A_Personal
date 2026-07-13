package com.example.stubserver.controller;

/**
 * Stub 장애 주입 시 던지는 예외 — HTTP 503으로 변환
 */
public class StubFaultException extends RuntimeException {

    public StubFaultException(String message) {
        super(message);
    }
}
