package com.dylansbogar.griddybot.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "daily_results", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_id", "played_on"}))
public class DailyResult {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private DailyGame game;

    @Column(name = "played_on", nullable = false)
    private LocalDate playedOn;

    private OffsetDateTime postedAt;

    @Column(columnDefinition = "text", nullable = false)
    private String resultText;

    private String puzzleNumber;
}
