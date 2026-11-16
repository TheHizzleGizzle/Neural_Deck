package com.example.wtfff.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class GraveyardEntry {
    public String id;
    public String reasonTag;
    public String antiPatternRule;
    public boolean activeInPrompts;
    public long banishedAt;
    public IdeaCard archivedCard;

    public GraveyardEntry() {
        this.id = UUID.randomUUID().toString();
        this.activeInPrompts = true;
        this.banishedAt = System.currentTimeMillis();
    }

    public GraveyardEntry(IdeaCard card, String reasonTag, String customRule) {
        this();
        this.archivedCard = card;
        this.reasonTag = (reasonTag != null && !reasonTag.trim().isEmpty()) ? reasonTag.trim() : "REJECTED_DIRECTION";
        if (customRule != null && !customRule.trim().isEmpty()) {
            this.antiPatternRule = customRule.trim();
        } else {
            String snippet = card.summary != null ? card.summary : "";
            if (snippet.length() > 110) {
                snippet = snippet.substring(0, 107) + "...";
            }
            this.antiPatternRule = "Avoid ideas resembling \"" + card.title + "\" (" + this.reasonTag + "): " + snippet;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("reasonTag", reasonTag);
        obj.put("antiPatternRule", antiPatternRule);
        obj.put("activeInPrompts", activeInPrompts);
        obj.put("banishedAt", banishedAt);
        if (archivedCard != null) {
            obj.put("archivedCard", archivedCard.toJson());
        }
        return obj;
    }

    public static GraveyardEntry fromJson(JSONObject obj) {
        GraveyardEntry entry = new GraveyardEntry();
        entry.id = obj.optString("id", UUID.randomUUID().toString());
        entry.reasonTag = obj.optString("reasonTag", "REJECTED_DIRECTION");
        entry.antiPatternRule = obj.optString("antiPatternRule", "");
        entry.activeInPrompts = obj.optBoolean("activeInPrompts", true);
        entry.banishedAt = obj.optLong("banishedAt", System.currentTimeMillis());
        JSONObject cardObj = obj.optJSONObject("archivedCard");
        if (cardObj != null) {
            entry.archivedCard = IdeaCard.fromJson(cardObj);
        }
        return entry;
    }
}
