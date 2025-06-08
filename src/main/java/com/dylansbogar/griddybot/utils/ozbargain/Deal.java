package com.dylansbogar.griddybot.utils.ozbargain;

import lombok.Data;

import java.time.ZonedDateTime;

@Data
public class Deal {
    private String id;
    private String title;
    private String link;
    private String category;
    private int votesPos;
    private int votesNeg;
    private String dealUrl;
    private String imageUrl;
    private int commentCount;
    private boolean isExpired;
    private ZonedDateTime pubDate;
}
