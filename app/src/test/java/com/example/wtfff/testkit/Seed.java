package com.example.wtfff.testkit;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Writes saved data in the exact on-disk format the shipped app uses
 * (neural_deck_sessions_v1.json + the active-session preference), before the app starts.
 * Doubles as a check that old saved data keeps loading.
 */
public final class Seed {
    private Seed() {
    }

    public static JSONObject card(String id, String parentId, String title, String badge, int accent) {
        try {
            return new JSONObject()
                    .put("id", id)
                    .put("parentId", parentId == null ? "" : parentId)
                    .put("depth", 0)
                    .put("title", title)
                    .put("summary", "Summary of " + title + ".")
                    .put("rationale", "Why " + title + ".")
                    .put("skillBadge", badge)
                    .put("accentColorHex", accent)
                    .put("starred", false)
                    .put("collapsed", false)
                    .put("createdAt", 1700000000000L)
                    .put("tags", new JSONArray().put("TAG_" + id.toUpperCase()));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static JSONObject session(String id, String title, String seedPrompt, JSONObject... cards) {
        try {
            JSONArray arr = new JSONArray();
            for (JSONObject c : cards) {
                arr.put(c);
            }
            return new JSONObject()
                    .put("id", id)
                    .put("title", title)
                    .put("seedPrompt", seedPrompt)
                    .put("updatedAt", 1700000000000L)
                    .put("cards", arr)
                    .put("graveyard", new JSONArray());
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    public static void sessions(Context context, String activeSessionId, JSONObject... sessions) {
        JSONArray arr = new JSONArray();
        for (JSONObject s : sessions) {
            arr.put(s);
        }
        File file = new File(context.getFilesDir(), "neural_deck_sessions_v1.json");
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        context.getSharedPreferences("neural_deck_repo_prefs", Context.MODE_PRIVATE).edit()
                .putString("active_session_id", activeSessionId)
                .commit();
    }

    /** The sessions file as the app last saved it. */
    public static JSONArray savedSessions(Context context) {
        File file = new File(context.getFilesDir(), "neural_deck_sessions_v1.json");
        try {
            return new JSONArray(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("Cannot read saved sessions", e);
        }
    }
}
