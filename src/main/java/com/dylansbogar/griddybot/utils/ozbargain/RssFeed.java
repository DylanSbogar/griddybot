package com.dylansbogar.griddybot.utils.ozbargain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RssFeed {
    public Channel channel;
}
