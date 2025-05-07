package com.dylansbogar.griddybot.utils.ozbargain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OzbMeta {
    @JacksonXmlProperty(localName = "votes-pos")
    public int votesPos;

    @JacksonXmlProperty(localName = "votes-neg")
    public int votesNeg;

    public String url;
    public String image;

    @JacksonXmlProperty(localName = "comment-count")
    public int commentCount;
}
