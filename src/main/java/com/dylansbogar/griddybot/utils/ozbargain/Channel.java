package com.dylansbogar.griddybot.utils.ozbargain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Channel {

    @JacksonXmlProperty(localName="item")
    @JacksonXmlElementWrapper(useWrapping = false)
    public List<Item> items;
}
