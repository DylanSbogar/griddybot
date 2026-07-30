package com.dylansbogar.griddybot.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "emotes")
public class Emote {
    @Id
    private String name;

    private String emoteId;

    // Which provider this emote's id belongs to, so we build the correct CDN URL.
    // null == legacy BetterTTV rows; "7TV" for emotes fetched via the 7TV API.
    private String source;
}
