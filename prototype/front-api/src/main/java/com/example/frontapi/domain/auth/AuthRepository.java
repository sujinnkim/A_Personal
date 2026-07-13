package com.example.frontapi.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * generalDataSource 주입 — 권한 갱신 (읽기 + 업데이트)
 */
public interface AuthRepository extends JpaRepository<MemberPermission, Long> {

    Optional<MemberPermission> findByEmail(String email);
}
