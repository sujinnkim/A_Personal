package com.example.stubserver.model;

import lombok.Getter;
import lombok.Setter;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IV-04: 외부 서버별 장애 상태 관리
 * - faultEnabled: true → 장애 모드 (503 응답 또는 지연)
 * - delayMs: 지연 시간 (ms)
 * - errorMode: true → HTTP 503, false → 지연만
 */
@Getter
@Setter
public class FaultState {

    private final String serverName;
    private final AtomicBoolean faultEnabled = new AtomicBoolean(false);
    private final AtomicInteger delayMs = new AtomicInteger(0);
    private final AtomicBoolean errorMode = new AtomicBoolean(true);

    public FaultState(String serverName) {
        this.serverName = serverName;
    }

    public boolean isFaultEnabled() {
        return faultEnabled.get();
    }

    public int getDelayMs() {
        return delayMs.get();
    }

    public boolean isErrorMode() {
        return errorMode.get();
    }

    public void enableFault(int delayMs, boolean errorMode) {
        this.delayMs.set(delayMs);
        this.errorMode.set(errorMode);
        this.faultEnabled.set(true);
    }

    public void disableFault() {
        this.faultEnabled.set(false);
        this.delayMs.set(0);
    }
}
