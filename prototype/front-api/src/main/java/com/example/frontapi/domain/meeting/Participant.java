package com.example.frontapi.domain.meeting;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "participants")
@Getter
@Setter
@NoArgsConstructor
public class Participant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;

    @Column(nullable = false, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantStatus status = ParticipantStatus.INVITED;

    @Column(name = "invited_at", nullable = false)
    private LocalDateTime invitedAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public Participant(Meeting meeting, String email) {
        this.meeting = meeting;
        this.email = email;
    }

    /**
     * ISSUE-07: cPaaS 피드백에 의한 참석자 상태 변경(write).
     * Primary(ServicePool)로 라우팅되어 write 집중 구간을 형성한다.
     */
    public void updateStatus(ParticipantStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }
}
