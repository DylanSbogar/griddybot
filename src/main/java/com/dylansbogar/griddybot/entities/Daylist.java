package com.dylansbogar.griddybot.entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "daylists")
public class Daylist {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String userId;

    private String day;

    private String time;

    private OffsetDateTime timestamp;

    @ToString.Exclude
    @OneToMany(mappedBy = "daylist", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DaylistDescription> descriptions = new ArrayList<>();

}