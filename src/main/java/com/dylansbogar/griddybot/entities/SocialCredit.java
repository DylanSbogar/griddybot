package com.dylansbogar.griddybot.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "social_credit")
public class SocialCredit {
    @Id
    private String userId;

    private String userTag;

    private int totalPoints;

    private int lastWeekDelta;

    @Column(columnDefinition = "TEXT")
    private String lastWeekReason;

    private Instant lastEvaluatedAt;
}
