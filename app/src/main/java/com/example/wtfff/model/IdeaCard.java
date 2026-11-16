package com.example.wtfff.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class IdeaCard {
    public String id;
    public String parentId;
    public int depth;
    public String title;
    public String summary;
    public String rationale;
    public String skillBadge;
    public int accentColorHex;
    public List<String> tags = new ArrayList<>();
    public boolean starred;
    public boolean collapsed;
    public long createdAt;

    public IdeaCard() {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.accentColorHex = 0xFF00F0FF;
    }

    public IdeaCard(String parentId, int depth, String title, String summary, String rationale,
                    String skillBadge, int accentColorHex, List<String> tags) {
        this();
        this.parentId = parentId;
        this.depth = depth;
        this.title = title != null ? title : "UNTITLED_NODE";
        this.summary = summary != null ? summary : "";
        this.rationale = rationale != null ? rationale : "";
        this.skillBadge = skillBadge != null ? skillBadge : "SEED";
        this.accentColorHex = accentColorHex;
        if (tags != null) {
            this.tags.addAll(tags);
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("parentId", parentId != null ? parentId : "");
        obj.put("depth", depth);
        obj.put("title", title);
        obj.put("summary", summary);
        obj.put("rationale", rationale);
        obj.put("skillBadge", skillBadge);
        obj.put("accentColorHex", accentColorHex);
        obj.put("starred", starred);
        obj.put("collapsed", collapsed);
        obj.put("createdAt", createdAt);
        JSONArray tagsArr = new JSONArray();
        for (String t : tags) {
            tagsArr.put(t);
        }
        obj.put("tags", tagsArr);
        return obj;
    }

    public static IdeaCard fromJson(JSONObject obj) {
        IdeaCard card = new IdeaCard();
        card.id = obj.optString("id", UUID.randomUUID().toString());
        String pid = obj.optString("parentId", "");
        card.parentId = pid.isEmpty() ? null : pid;
        card.depth = obj.optInt("depth", 0);
        card.title = obj.optString("title", "UNTITLED_NODE");
        card.summary = obj.optString("summary", "");
        card.rationale = obj.optString("rationale", "");
        card.skillBadge = obj.optString("skillBadge", "SEED");
        card.accentColorHex = obj.optInt("accentColorHex", 0xFF00F0FF);
        card.starred = obj.optBoolean("starred", false);
        card.collapsed = obj.optBoolean("collapsed", false);
        card.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
        JSONArray tagsArr = obj.optJSONArray("tags");
        if (tagsArr != null) {
            for (int i = 0; i < tagsArr.length(); i++) {
                String tag = tagsArr.optString(i, "").trim();
                if (!tag.isEmpty()) {
                    card.tags.add(tag);
                }
            }
        }
        return card;
    }
}
