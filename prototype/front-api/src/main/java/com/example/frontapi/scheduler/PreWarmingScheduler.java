package com.example.frontapi.scheduler;

import com.example.frontapi.config.PeakDetector;
import com.example.frontapi.domain.auth.AuthRepository;
import com.example.frontapi.domain.auth.MemberPermission;
import com.example.frontapi.integration.ac.AcServerGateway;
import com.example.frontapi.integration.copilot.CopilotAdminGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AS-05: Pre-warming Scheduler
 * - 1분 주기로 활성 멤버 권한 데이터를 Redis(L2)에 선제 적재
 * - preWarmExecutor에서 실행
 * IV-03: 피크 전 캐시 선적재 → 피크 시 캐시 hit율 90% 이상 달성
 *
 * AS-06 연동(설계원칙 1, 피크 감지 공유): 동일한 PeakDetector를 공유한다.
 * 운영에서는 예약 회의 기반 워밍 트리거가 피크 임박 시 PeakDetector.setActive(true)로
 * AS-06 스로틀링을 선제 활성화한다. PoC에서는 PeakDetector가 고정 시간창을 자체 평가하며,
 * 본 스케줄러는 공유 피크 상태를 로깅해 연동을 드러낸다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreWarmingScheduler {

    private final AuthRepository authRepository;
    private final AcServerGateway acServerGateway;
    private final CopilotAdminGateway copilotAdminGateway;
    private final CacheManager redisCacheManager;
    private final CacheManager caffeineCacheManager;
    private final PeakDetector peakDetector;

    @Scheduled(cron = "${scheduler.pre-warming.cron:0 * * * * *}")
    @Async("preWarmExecutor")
    public void preWarmPermissions() {
        log.info("[PreWarming] 권한 사전 적재 시작 (공유 피크 상태 peakActive={})", peakDetector.isPeakActive());
        long start = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;

        try {
            // 최근 활성 멤버 목록 조회 (최대 500명 제한)
            List<MemberPermission> members = authRepository.findAll()
                .stream().limit(500).toList();

            Cache redisCache = redisCacheManager.getCache("member-permissions");
            Cache caffeineCache = caffeineCacheManager.getCache("member-permissions");

            for (MemberPermission member : members) {
                try {
                    String email = member.getEmail();
                    String acRole = acServerGateway.fetchAcRole(email);
                    Boolean copilotEnabled = copilotAdminGateway.fetchCopilotEnabled(email);

                    // null 폴백 처리
                    if (acRole == null) acRole = member.getAcRole() != null ? member.getAcRole() : "VIEWER";
                    if (copilotEnabled == null) copilotEnabled = member.getCopilotEnabled() != null ? member.getCopilotEnabled() : false;

                    Map<String, Object> permissionData = new HashMap<>();
                    permissionData.put("email", email);
                    permissionData.put("acRole", acRole);
                    permissionData.put("copilotEnabled", copilotEnabled);
                    permissionData.put("permissionLevel", member.getPermissionLevel());

                    // L2 Redis 적재
                    if (redisCache != null) {
                        redisCache.put(email, permissionData);
                    }
                    // L1 Caffeine도 적재
                    if (caffeineCache != null) {
                        caffeineCache.put(email, permissionData);
                    }

                    successCount++;
                } catch (Exception e) {
                    log.warn("[PreWarming] 멤버 적재 실패 email={} cause={}", member.getEmail(), e.getMessage());
                    failCount++;
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("[PreWarming] 권한 사전 적재 완료 success={} fail={} elapsed={}ms",
                successCount, failCount, elapsed);
        } catch (Exception e) {
            log.error("[PreWarming] 스케줄러 오류", e);
        }
    }
}
