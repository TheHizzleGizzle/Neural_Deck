package com.example.wtfff.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BrainstormSession {
    public String id;
    public String title;
    public String seedPrompt;
    public long updatedAt;
    public List<IdeaCard> cards = new ArrayList<>();
    public List<GraveyardEntry> graveyard = new ArrayList<>();

    public BrainstormSession() {
        this.id = UUID.randomUUID().toString();
        this.title = "NEURAL_SESSION_01";
        this.seedPrompt = "";
        this.updatedAt = System.currentTimeMillis();
    }

    public BrainstormSession(String title, String seedPrompt) {
        this();
        this.title = title;
        this.seedPrompt = seedPrompt != null ? seedPrompt : "";
    }

    public IdeaCard findCardById(String cardId) {
        if (cardId == null) return null;
        for (IdeaCard c : cards) {
            if (cardId.equals(c.id)) {
                return c;
            }
        }
        return null;
    }

    public int countChildren(String parentId) {
        int count = 0;
        for (IdeaCard c : cards) {
            if (parentId != null && parentId.equals(c.parentId)) {
                count++;
            }
        }
        return count;
    }

    public int countSubtree(String rootId) {
        int total = 0;
        for (IdeaCard c : cards) {
            if (rootId.equals(c.parentId)) {
                total += 1 + countSubtree(c.id);
            }
        }
        return total;
    }

    public List<IdeaCard> collectSubtree(String rootId) {
        List<IdeaCard> out = new ArrayList<>();
        IdeaCard root = findCardById(rootId);
        if (root != null) {
            out.add(root);
            collectChildrenRecursive(rootId, out);
        }
        return out;
    }

    private void collectChildrenRecursive(String parentId, List<IdeaCard> out) {
        for (IdeaCard c : cards) {
            if (parentId.equals(c.parentId)) {
                out.add(c);
                collectChildrenRecursive(c.id, out);
            }
        }
    }

    public List<IdeaCard> buildVisibleTree(String filterMode) {
        if ("STARRED".equals(filterMode)) {
            List<IdeaCard> starred = new ArrayList<>();
            for (IdeaCard c : cards) {
                if (c.starred) starred.add(c);
            }
            return starred;
        }
        if ("ROOTS".equals(filterMode)) {
            List<IdeaCard> roots = new ArrayList<>();
            for (IdeaCard c : cards) {
                if (c.parentId == null || findCardById(c.parentId) == null) {
                    roots.add(c);
                }
            }
            return roots;
        }

        Map<String, List<IdeaCard>> childrenMap = new HashMap<>();
        List<IdeaCard> roots = new ArrayList<>();
        for (IdeaCard c : cards) {
            if (c.parentId == null || findCardById(c.parentId) == null) {
                roots.add(c);
            } else {
                List<IdeaCard> list = childrenMap.get(c.parentId);
                if (list == null) {
                    list = new ArrayList<>();
                    childrenMap.put(c.parentId, list);
                }
                list.add(c);
            }
        }

        List<IdeaCard> ordered = new ArrayList<>();
        for (IdeaCard root : roots) {
            root.depth = 0;
            ordered.add(root);
            if (!root.collapsed) {
                appendVisibleChildren(root.id, 1, childrenMap, ordered);
            }
        }
        return ordered;
    }

    private void appendVisibleChildren(String parentId, int depth,
                                       Map<String, List<IdeaCard>> childrenMap,
                                       List<IdeaCard> ordered) {
        List<IdeaCard> kids = childrenMap.get(parentId);
        if (kids == null) return;
        for (IdeaCard kid : kids) {
            kid.depth = depth;
            ordered.add(kid);
            if (!kid.collapsed) {
                appendVisibleChildren(kid.id, depth + 1, childrenMap, ordered);
            }
        }
    }

    public List<GraveyardEntry> getActiveGraveyardEntries() {
        List<GraveyardEntry> active = new ArrayList<>();
        for (GraveyardEntry g : graveyard) {
            if (g.activeInPrompts) {
                active.add(g);
            }
        }
        return active;
    }

    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("# // ").append(title).append("\n\n");
        if (seedPrompt != null && !seedPrompt.trim().isEmpty()) {
            sb.append("> **Seed Prompt:** ").append(seedPrompt.trim()).append("\n\n");
        }

        List<IdeaCard> fullTree = buildFullOrderedTree();
        sb.append("## Idea Branch Tree (").append(fullTree.size()).append(" nodes)\n\n");
        for (IdeaCard c : fullTree) {
            for (int i = 0; i < c.depth; i++) {
                sb.append("  ");
            }
            sb.append("- **[").append(c.skillBadge).append("] ").append(c.title).append("**");
            if (c.starred) sb.append(" ★");
            sb.append("\n");
            for (int i = 0; i < c.depth; i++) {
                sb.append("  ");
            }
            sb.append("  ").append(c.summary).append("\n");
            if (c.rationale != null && !c.rationale.isEmpty()) {
                for (int i = 0; i < c.depth; i++) {
                    sb.append("  ");
                }
                sb.append("  _Why it works:_ ").append(c.rationale).append("\n");
            }
        }

        if (!graveyard.isEmpty()) {
            sb.append("\n## Anti-Pattern Graveyard (").append(graveyard.size()).append(" banished)\n\n");
            for (GraveyardEntry g : graveyard) {
                sb.append("- **[").append(g.reasonTag).append("]** ").append(g.antiPatternRule).append("\n");
            }
        }
        return sb.toString();
    }

    private List<IdeaCard> buildFullOrderedTree() {
        Map<String, List<IdeaCard>> childrenMap = new HashMap<>();
        List<IdeaCard> roots = new ArrayList<>();
        for (IdeaCard c : cards) {
            if (c.parentId == null || findCardById(c.parentId) == null) {
                roots.add(c);
            } else {
                List<IdeaCard> list = childrenMap.get(c.parentId);
                if (list == null) {
                    list = new ArrayList<>();
                    childrenMap.put(c.parentId, list);
                }
                list.add(c);
            }
        }
        List<IdeaCard> out = new ArrayList<>();
        for (IdeaCard r : roots) {
            r.depth = 0;
            out.add(r);
            appendAllChildren(r.id, 1, childrenMap, out);
        }
        return out;
    }

    private void appendAllChildren(String parentId, int depth,
                                   Map<String, List<IdeaCard>> childrenMap,
                                   List<IdeaCard> out) {
        List<IdeaCard> kids = childrenMap.get(parentId);
        if (kids == null) return;
        for (IdeaCard k : kids) {
            k.depth = depth;
            out.add(k);
            appendAllChildren(k.id, depth + 1, childrenMap, out);
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("title", title);
        obj.put("seedPrompt", seedPrompt);
        obj.put("updatedAt", updatedAt);
        JSONArray cardsArr = new JSONArray();
        for (IdeaCard c : cards) {
            cardsArr.put(c.toJson());
        }
        obj.put("cards", cardsArr);
        JSONArray graveArr = new JSONArray();
        for (GraveyardEntry g : graveyard) {
            graveArr.put(g.toJson());
        }
        obj.put("graveyard", graveArr);
        return obj;
    }

    public static BrainstormSession fromJson(JSONObject obj) {
        BrainstormSession s = new BrainstormSession();
        s.id = obj.optString("id", UUID.randomUUID().toString());
        s.title = obj.optString("title", "NEURAL_SESSION");
        s.seedPrompt = obj.optString("seedPrompt", "");
        s.updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
        JSONArray cardsArr = obj.optJSONArray("cards");
        if (cardsArr != null) {
            for (int i = 0; i < cardsArr.length(); i++) {
                JSONObject co = cardsArr.optJSONObject(i);
                if (co != null) {
                    s.cards.add(IdeaCard.fromJson(co));
                }
            }
        }
        JSONArray graveArr = obj.optJSONArray("graveyard");
        if (graveArr != null) {
            for (int i = 0; i < graveArr.length(); i++) {
                JSONObject go = graveArr.optJSONObject(i);
                if (go != null) {
                    s.graveyard.add(GraveyardEntry.fromJson(go));
                }
            }
        }
        return s;
    }
}
