package com.dylansbogar.griddybot.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "daily_preferences", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "game_id"}))
public class DailyPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private DailyGame game;
}
