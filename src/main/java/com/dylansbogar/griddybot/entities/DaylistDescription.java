package com.dylansbogar.griddybot.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "daylist_descriptions")
public class DaylistDescription {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String userId;

    @ManyToOne
    @ToString.Exclude
    @JoinColumn(name = "daylist_id", nullable = false)
    private Daylist daylist;

    private String description;
}
