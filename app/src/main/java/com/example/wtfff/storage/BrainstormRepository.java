package com.example.wtfff.storage;

import android.content.Context;

import com.example.wtfff.model.BrainstormSession;
import com.example.wtfff.model.CreativeSkill;
import com.example.wtfff.model.GraveyardEntry;
import com.example.wtfff.model.IdeaCard;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BrainstormRepository {
    private static final String SESSIONS_FILE = "neural_deck_sessions_v1.json";
    private static final String CUSTOM_SKILLS_FILE = "neural_deck_custom_skills_v1.json";
    private static final String ACTIVE_SESSION_PREF = "neural_deck_repo_prefs";

    private final Context appContext;

    public BrainstormRepository(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public List<BrainstormSession> loadSessions() {
        List<BrainstormSession> list = new ArrayList<>();
        File file = new File(appContext.getFilesDir(), SESSIONS_FILE);
        if (file.exists()) {
            try {
                String raw = readFileText(file);
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        list.add(BrainstormSession.fromJson(obj));
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (list.isEmpty()) {
            BrainstormSession starter = createStarterSession();
            list.add(starter);
            saveSessions(list);
            setActiveSessionId(starter.id);
        }
        return list;
    }

    public void saveSessions(List<BrainstormSession> sessions) {
        try {
            JSONArray arr = new JSONArray();
            for (BrainstormSession s : sessions) {
                arr.put(s.toJson());
            }
            writeFileText(new File(appContext.getFilesDir(), SESSIONS_FILE), arr.toString());
        } catch (Exception ignored) {
        }
    }

    public String getActiveSessionId() {
        return appContext.getSharedPreferences(ACTIVE_SESSION_PREF, Context.MODE_PRIVATE)
                .getString("active_session_id", "");
    }

    public void setActiveSessionId(String sessionId) {
        appContext.getSharedPreferences(ACTIVE_SESSION_PREF, Context.MODE_PRIVATE)
                .edit()
                .putString("active_session_id", sessionId)
                .apply();
    }

    public List<CreativeSkill> loadAllSkills() {
        List<CreativeSkill> all = new ArrayList<>(CreativeSkill.builtInSkills());
        all.addAll(loadCustomSkills());
        return all;
    }

    public List<CreativeSkill> loadCustomSkills() {
        List<CreativeSkill> custom = new ArrayList<>();
        File file = new File(appContext.getFilesDir(), CUSTOM_SKILLS_FILE);
        if (!file.exists()) return custom;
        try {
            String raw = readFileText(file);
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj != null) {
                    CreativeSkill skill = CreativeSkill.fromJson(obj);
                    skill.builtIn = false;
                    custom.add(skill);
                }
            }
        } catch (Exception ignored) {
        }
        return custom;
    }

    public void saveCustomSkills(List<CreativeSkill> customSkills) {
        try {
            JSONArray arr = new JSONArray();
            for (CreativeSkill s : customSkills) {
                if (!s.builtIn) {
                    arr.put(s.toJson());
                }
            }
            writeFileText(new File(appContext.getFilesDir(), CUSTOM_SKILLS_FILE), arr.toString());
        } catch (Exception ignored) {
        }
    }

    private BrainstormSession createStarterSession() {
        BrainstormSession session = new BrainstormSession(
                "AMBIENT_FOCUS_OS",
                "Design a mobile tool that helps deep thinkers enter flow state without gamified streak guilt."
        );

        IdeaCard root1 = new IdeaCard(
                null,
                0,
                "Acoustic Bio-Rhythm Cockpit",
                "Generates adaptive pixel-synth soundscapes keyed to typing cadence and time-of-day ultradian cycles (90m sprint / 20m cooldown).",
                "Replaces punitive streak counters with immediate physiological feedback.",
                "SEED",
                0xFF00F0FF,
                Arrays.asList("AUDIO", "FLOW_STATE", "NO_STREAKS")
        );
        root1.starred = true;

        IdeaCard child1 = new IdeaCard(
                root1.id,
                1,
                "Zero-UI Lockscreen Cartridge",
                "Flip phone face-down to arm focus; a single 16-bit LED waveform pulses through the AOD while notifications buffer into a post-sprint digest.",
                "SCAMPER Eliminate: removes screen temptation completely while preserving ambient reassurance.",
                "SCAMPER",
                0xFFFF2E93,
                Arrays.asList("ZERO_UI", "HARDWARE_TRIGGER")
        );

        IdeaCard child2 = new IdeaCard(
                root1.id,
                1,
                "Multiplayer Ghost Co-Working",
                "Pair silently with 3 anonymous builders working on parallel sprints—represented only as tiny pixel avatars typing at real-time WPM.",
                "Borrows Dark Souls ambient multiplayer presence without chat distraction.",
                "ANALOGY",
                0xFFA855F7,
                Arrays.asList("AMBIENT_SOCIAL", "DARK_SOULS_UX")
        );

        IdeaCard root2 = new IdeaCard(
                null,
                0,
                "Context-Snapshot Parking Lot",
                "Before pausing work, record a 12-second voice dump that AI compiles into a 'Next 3 Keystrokes' launchpad card for tomorrow morning.",
                "First Principles: procrastination comes from high cognitive reload cost, not laziness.",
                "1ST_PRINC",
                0xFF39FF88,
                Arrays.asList("COGNITIVE_RELOAD", "VOICE_MEMO")
        );

        session.cards.add(root1);
        session.cards.add(child1);
        session.cards.add(child2);
        session.cards.add(root2);

        IdeaCard rejected = new IdeaCard(
                null,
                0,
                "Daily Flame Streak Leaderboard",
                "Users lose XP and drop league tiers if they miss a focus day, with push notification reminders.",
                "Standard Duolingo retention loop.",
                "SEED",
                0xFFFF3864,
                Arrays.asList("GAMIFICATION")
        );
        GraveyardEntry grave = new GraveyardEntry(
                rejected,
                "TOO_CLICHE",
                "Never suggest punitive daily streaks, XP leaderboards, or guilt-inducing push notifications."
        );
        session.graveyard.add(grave);

        return session;
    }

    private String readFileText(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private void writeFileText(File file, String content) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }
}
