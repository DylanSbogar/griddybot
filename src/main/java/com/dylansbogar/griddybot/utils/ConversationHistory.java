package com.dylansbogar.griddybot.utils;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ConversationHistory {
    private final List<JSONObject> messages = new ArrayList<>();
    private String summary;

    public List<JSONObject> getMessages() { return messages; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
