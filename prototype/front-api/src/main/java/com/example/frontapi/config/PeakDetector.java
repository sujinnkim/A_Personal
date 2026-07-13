package com.example.frontapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * AS-06 설계원칙 1(피크 감지 공유):
 * 시간대 기반 고정 피크(오전/오후 각 1시간)와 예약 회의 기반 감지를 결합해
 * 현재가 피크 구간인지 판정한다. AS-05 Pre-warming과 동일한 피크 감지원을 공유한다.
 *
 * PoC 범위: 고정 시간창(기본 08:30~09:30, 12:30~13:30)을 1분 주기로 평가한다.
 *  - 예약 회의 기반 동적 감지는 운영 환경에서 Pre-warming 트리거와 연동되며,
 *    피크 임박 시 {@link #setActive(boolean)}로 강제 활성화하는 지점이 된다.
 *  - 검증 시에도 setActive 수동 오버라이드로 피크 구간을 고정할 수 있다.
 */
@Slf4j
@Component
public class PeakDetector {

    private final AtomicBoolean peakActive = new AtomicBoolean(false);

    /** null = 시간창 자동 판정, non-null = 수동 오버라이드 우선 */
    private volatile Boolean manualOverride = null;

    private final LocalTime morningStart;
    private final LocalTime morningEnd;
    private final LocalTime afternoonStart;
    private final LocalTime afternoonEnd;

    public PeakDetector(
        @Value("${throttling.peak.morning-start:08:30}") String morningStart,
        @Value("${throttling.peak.morning-end:09:30}") String morningEnd,
        @Value("${throttling.peak.afternoon-start:12:30}") String afternoonStart,
        @Value("${throttling.peak.afternoon-end:13:30}") String afternoonEnd
    ) {
        this.morningStart = LocalTime.parse(morningStart);
        this.morningEnd = LocalTime.parse(morningEnd);
        this.afternoonStart = LocalTime.parse(afternoonStart);
        this.afternoonEnd = LocalTime.parse(afternoonEnd);
    }

    public boolean isPeakActive() {
        return peakActive.get();
    }

    /**
     * 검증·운영 연동용 수동 오버라이드.
     * 운영에서는 Pre-warming 트리거가 피크 임박 시 setActive(true)로 스로틀링을 선제 활성화한다.
     */
    public void setActive(boolean active) {
        this.manualOverride = active;
        this.peakActive.set(active);
        log.info("[PeakDetector] 수동 오버라이드 peakActive={}", active);
    }

    /** 수동 오버라이드 해제 → 시간창 자동 판정으로 복귀 */
    public void clearManualOverride() {
        this.manualOverride = null;
        log.info("[PeakDetector] 수동 오버라이드 해제 → 자동 판정 복귀");
    }

    @Scheduled(cron = "${throttling.peak.evaluate-cron:30 * * * * *}")
    public void evaluatePeakWindow() {
        if (manualOverride != null) {
            return; // 수동 오버라이드가 걸려 있으면 시간창 판정을 건너뛴다
        }
        LocalTime now = LocalTime.now();
        boolean inPeak = isWithin(now, morningStart, morningEnd)
            || isWithin(now, afternoonStart, afternoonEnd);
        boolean changed = peakActive.getAndSet(inPeak) != inPeak;
        if (changed) {
            log.info("[PeakDetector] 피크 구간 판정 변경 peakActive={} now={}", inPeak, now);
        }
    }

    private boolean isWithin(LocalTime now, LocalTime start, LocalTime end) {
        return !now.isBefore(start) && now.isBefore(end);
    }
}
