package com.dylansbogar.griddybot.utils.ozbargain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Item {
    public String title;
    public String link;
    public String category;
    public String pubDate;

    @JacksonXmlProperty(localName = "meta", namespace = "ozb")
    public OzbMeta meta;

    @JacksonXmlProperty(localName = "title-msg", namespace = "ozb")
    public OzbTitleMsg titleMsg;
}
