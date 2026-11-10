package com.dylansbogar.griddybot.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "daily_games", uniqueConstraints = @UniqueConstraint(columnNames = "name_key"))
public class DailyGame {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_key", nullable = false)
    private String nameKey;

    @Column(nullable = false, length = 500)
    private String url;
}
