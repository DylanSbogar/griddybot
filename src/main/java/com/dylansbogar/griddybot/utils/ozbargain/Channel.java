package com.dylansbogar.griddybot.utils.ozbargain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Channel {
    @JacksonXmlElementWrapper(useWrapping = false)
    public List<Item> items;
}
