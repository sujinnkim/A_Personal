package com.example.frontapi.domain.auth;

import com.example.frontapi.config.DataSourceContextHolder;
import com.example.frontapi.config.DataSourceType;
import com.example.frontapi.integration.ac.AcServerGateway;
import com.example.frontapi.integration.copilot.CopilotAdminGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * AS-03: L1/L2 캐시 계층 권한 갱신
 * - @Cacheable은 L1 Caffeine CacheManager에 적용 (Primary)
 * - CB 폴백 시 L2 Redis → DB 계층적 폴백 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthRepository authRepository;
    private final AcServerGateway acServerGateway;
    private final CopilotAdminGateway copilotAdminGateway;
    private final CacheManager caffeineCacheManager;
    private final CacheManager redisCacheManager;

    /**
     * 사용자 권한 갱신 (GET /members/{email}?type=detail)
     * L1 캐시 hit → L1 반환
     * L1 캐시 miss → 외부 서버 조회 후 L1+L2 저장
     */
    @Cacheable(value = "member-permissions", key = "#email", cacheManager = "caffeineCacheManager")
    @Transactional
    public Map<String, Object> getMemberDetail(String email) {
        log.debug("[AuthService] 권한 갱신 시작 email={}", email);

        // generalDataSource 사용
        DataSourceContextHolder.set(DataSourceType.GENERAL);

        // AC서버 조회 (CB 적용)
        String acRole = acServerGateway.fetchAcRole(email);
        // Copilot Admin 조회 (CB 적용)
        Boolean copilotEnabled = copilotAdminGateway.fetchCopilotEnabled(email);

        // AC서버 CB Open → DB 저장값 폴백
        if (acRole == null) {
            acRole = authRepository.findByEmail(email)
                .map(MemberPermission::getAcRole)
                .orElse("VIEWER");
            log.info("[AuthService] AC서버 폴백 → DB 저장값 사용 email={} acRole={}", email, acRole);
        }

        // Copilot CB Open → L2 Redis 폴백 → DB 폴백
        if (copilotEnabled == null) {
            copilotEnabled = getCopilotFromFallback(email);
        }

        // DB 갱신
        final String finalAcRole = acRole;
        final Boolean finalCopilotEnabled = copilotEnabled;
        MemberPermission permission = authRepository.findByEmail(email)
            .orElseGet(() -> new MemberPermission(email, 1));
        permission.setAcRole(finalAcRole);
        permission.setCopilotEnabled(finalCopilotEnabled);
        permission.setUpdatedAt(LocalDateTime.now());
        authRepository.save(permission);

        Map<String, Object> result = new HashMap<>();
        result.put("email", email);
        result.put("acRole", finalAcRole);
        result.put("copilotEnabled", finalCopilotEnabled);
        result.put("permissionLevel", permission.getPermissionLevel());

        // L2 Redis에도 적재
        Cache redisCache = redisCacheManager.getCache("member-permissions");
        if (redisCache != null) {
            redisCache.put(email, result);
        }

        log.debug("[AuthService] 권한 갱신 완료 email={}", email);
        return result;
    }

    /**
     * Copilot 계층적 폴백: L2 Redis → DB
     */
    private Boolean getCopilotFromFallback(String email) {
        // L2 Redis 조회
        Cache redisCache = redisCacheManager.getCache("copilot-permissions");
        if (redisCache != null) {
            Cache.ValueWrapper wrapper = redisCache.get(email);
            if (wrapper != null) {
                log.info("[AuthService] Copilot L2 캐시 hit email={}", email);
                return (Boolean) wrapper.get();
            }
        }
        // DB 폴백
        return authRepository.findByEmail(email)
            .map(MemberPermission::getCopilotEnabled)
            .orElse(false);
    }
}
