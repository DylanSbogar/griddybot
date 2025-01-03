package com.dylansbogar.griddybot.entities;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "reminders")
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String messageId;

    private String userId;

    private String message;

    private String date;
}
