package com.dylansbogar.griddybot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "social_credit_debug_log")
public class SocialCreditDebugLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Instant runAt;

    private String userId;

    private String userTag;

    private boolean sternBias;

    private int messageCount;

    @Column(columnDefinition = "TEXT")
    private String gatheredMessages;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(columnDefinition = "TEXT")
    private String llmResponse;

    private Integer parsedDelta;

    @Column(columnDefinition = "TEXT")
    private String parsedReasoning;

    @Column(columnDefinition = "TEXT")
    private String error;
}
