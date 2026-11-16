package com.example.wtfff.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CreativeSkill {
    public String id;
    public String name;
    public String badge;
    public String subtitle;
    public String systemInstruction;
    public int accentColorHex;
    public int outputCount;
    public boolean builtIn;

    public CreativeSkill() {
        this.id = UUID.randomUUID().toString();
        this.outputCount = 3;
        this.accentColorHex = 0xFF00F0FF;
    }

    public CreativeSkill(String id, String name, String badge, String subtitle,
                         String systemInstruction, int accentColorHex, int outputCount, boolean builtIn) {
        this.id = id;
        this.name = name;
        this.badge = badge;
        this.subtitle = subtitle;
        this.systemInstruction = systemInstruction;
        this.accentColorHex = accentColorHex;
        this.outputCount = outputCount;
        this.builtIn = builtIn;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        obj.put("badge", badge);
        obj.put("subtitle", subtitle);
        obj.put("systemInstruction", systemInstruction);
        obj.put("accentColorHex", accentColorHex);
        obj.put("outputCount", outputCount);
        obj.put("builtIn", builtIn);
        return obj;
    }

    public static CreativeSkill fromJson(JSONObject obj) {
        CreativeSkill s = new CreativeSkill();
        s.id = obj.optString("id", UUID.randomUUID().toString());
        s.name = obj.optString("name", "Custom Skill");
        s.badge = obj.optString("badge", "CUSTOM");
        s.subtitle = obj.optString("subtitle", "User-forged AI thinking cartridge");
        s.systemInstruction = obj.optString("systemInstruction", "Generate distinct creative branches.");
        s.accentColorHex = obj.optInt("accentColorHex", 0xFF00F0FF);
        s.outputCount = Math.max(1, Math.min(6, obj.optInt("outputCount", 3)));
        s.builtIn = obj.optBoolean("builtIn", false);
        return s;
    }

    public static List<CreativeSkill> builtInSkills() {
        List<CreativeSkill> list = new ArrayList<>();
        list.add(new CreativeSkill(
                "skill_diverge_10x",
                "Diverge 10x",
                "10X_DIVERGE",
                "Break incrementalism with non-obvious lateral leaps",
                "Push the target idea 10x further into bold, non-obvious territory. Reject safe incremental tweaks; explore distinct mechanisms, audiences, or business models.",
                0xFF00F0FF, 3, true
        ));
        list.add(new CreativeSkill(
                "skill_scamper",
                "SCAMPER Matrix",
                "SCAMPER",
                "Substitute, Combine, Adapt, Modify, Eliminate, Reverse",
                "Apply SCAMPER operators to mutate the target idea. Pick the 3 strongest SCAMPER lenses and show concrete transformed concepts.",
                0xFFFF2E93, 3, true
        ));
        list.add(new CreativeSkill(
                "skill_first_principles",
                "First Principles",
                "1ST_PRINC",
                "Strip away assumptions to physics/human truths & rebuild",
                "Deconstruct the target idea down to undeniable fundamental truths, expose hidden legacy assumptions, and rebuild cleaner solutions from scratch.",
                0xFF39FF88, 3, true
        ));
        list.add(new CreativeSkill(
                "skill_devils_advocate",
                "Devil's Advocate",
                "PRE_MORTEM",
                "Brutal failure simulation + hardened counter-designs",
                "Imagine it is 12 months from now and this idea failed catastrophically. Identify the deadliest failure modes and propose hardened counter-architectures.",
                0xFFFF3864, 3, true
        ));
        list.add(new CreativeSkill(
                "skill_inversion",
                "Inversion Flip",
                "INVERT",
                "Guarantee failure first, then invert every lever",
                "Use Charlie Munger style inversion: ask how to guarantee the worst outcome, then flip the core constraint, workflow, or monetization upside down.",
                0xFFFFB800, 3, true
        ));
        list.add(new CreativeSkill(
                "skill_analogy",
                "Cross-Domain Bio/Game",
                "ANALOGY",
                "Steal mechanics from game design, biology, & aviation",
                "Transfer proven mechanics from distant domains (mycelial networks, roguelike game loops, cockpit checklists, high-frequency trading) onto this idea.",
                0xFFA855F7, 3, true
        ));
        list.add(new CreativeSkill(
                "skill_six_hats",
                "Six Hats Synthesis",
                "6_HATS",
                "Facts, gut intuition, upside, risk, lateral, execution",
                "Synthesize perspectives across De Bono's Six Thinking Hats—delivering one optimistic breakthrough, one data/system play, and one tactical execution pivot.",
                0xFF38BDF8, 3, true
        ));
        list.add(new CreativeSkill(
                "skill_48h_mvp",
                "48h Prototype Spec",
                "48H_MVP",
                "Razor-thin weekend build slices that prove core value",
                "Turn the concept into concrete 48-hour shippable MVP slices: smallest possible scope, wizard-of-oz shortcuts, and a falsifiable validation signal.",
                0xFF39FF88, 3, true
        ));
        return list;
    }
}
