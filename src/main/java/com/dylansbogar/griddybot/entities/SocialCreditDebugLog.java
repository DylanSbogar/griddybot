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

    // ---- Run-level stats (eval row only; null on per-user rows) ----
    // Spot cap pressure here: if fetchedCount/trackedCount regularly sit at or
    // above MAX_MESSAGES_TOTAL (capApplied = true), the weekly conversation is
    // being truncated and the cap should be raised.
    private Integer fetchedCount;   // raw messages fetched from Discord (pre-filter)
    private Integer trackedCount;   // after filtering to tracked authors (pre-cap)
    private Boolean capApplied;     // trackedCount exceeded MAX_MESSAGES_TOTAL

    // ---- Per-user delta breakdown (per-user rows only; null on the eval row) ----
    // rawDelta  = the model's delta after clamping.
    // parsedDelta = the final delta actually applied (after inactivity adjustments).
    // A gap between the two explains "why fewer points than expected".
    // newTotal  = the running total after this week's delta was applied.
    private Integer rawDelta;
    private Integer newTotal;
}
