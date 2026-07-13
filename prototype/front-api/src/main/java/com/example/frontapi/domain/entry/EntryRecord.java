package com.example.frontapi.domain.entry;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "entry_records")
@Getter
@Setter
@NoArgsConstructor
public class EntryRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id", nullable = false)
    private Long meetingId;

    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    @Column(name = "conference_token", length = 500)
    private String conferenceToken;

    public EntryRecord(Long meetingId, String email) {
        this.meetingId = meetingId;
        this.email = email;
    }
}
