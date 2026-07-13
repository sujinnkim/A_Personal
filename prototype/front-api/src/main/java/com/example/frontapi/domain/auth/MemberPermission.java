package com.example.frontapi.domain.auth;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "member_permissions")
@Getter
@Setter
@NoArgsConstructor
public class MemberPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "permission_level", nullable = false)
    private Integer permissionLevel = 1;

    @Column(name = "ac_role", length = 50)
    private String acRole;

    @Column(name = "copilot_enabled")
    private Boolean copilotEnabled = false;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public MemberPermission(String email, Integer permissionLevel) {
        this.email = email;
        this.permissionLevel = permissionLevel;
    }
}
