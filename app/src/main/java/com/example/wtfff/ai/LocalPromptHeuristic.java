package com.example.wtfff.ai;

import com.example.wtfff.model.GraveyardEntry;
import com.example.wtfff.model.PromptOptimizationResult;

import java.util.ArrayList;
import java.util.List;

public class LocalPromptHeuristic {

    public static PromptOptimizationResult upgradeLocally(String rawPrompt,
                                                          String framework,
                                                          List<GraveyardEntry> graveyard) {
        String clean = rawPrompt != null ? rawPrompt.trim() : "";
        List<String> upgrades = new ArrayList<>();
        StringBuilder out = new StringBuilder();

        if ("CO-STAR".equalsIgnoreCase(framework)) {
            out.append("[CONTEXT] Exploring innovative solutions around: ").append(clean).append("\n")
                    .append("[OBJECTIVE] Generate 4 sharply differentiated, non-obvious product/system concepts.\n")
                    .append("[STYLE & TONE] Concrete mechanics, first-principles clarity, zero generic buzzwords.\n")
                    .append("[RESPONSE] Provide distinct mechanisms, user workflow, and asymmetric advantage.");
            upgrades.add("Structured into Context / Objective / Style / Response blocks");
            upgrades.add("Added explicit anti-buzzword guardrail");
        } else if ("10X_DIVERGENT".equalsIgnoreCase(framework)) {
            out.append("Topic: ").append(clean).append("\n")
                    .append("Push 10x past obvious answers across 4 orthogonal axes: ")
                    .append("(1) Zero-UI / ambient automation, (2) Multiplayer / network-effect wedge, ")
                    .append("(3) Radical simplification (eliminate 80% of steps), and (4) Inverted business/workflow model.");
            upgrades.add("Forced 4 orthogonal creative axes (Zero-UI, Network, Eliminate, Invert)");
            upgrades.add("Blocked safe incremental variations");
        } else if ("SOCRATIC".equalsIgnoreCase(framework)) {
            out.append("Deconstruct: ").append(clean).append("\n")
                    .append("First expose the 3 biggest unspoken assumptions keeping current solutions mediocre. ")
                    .append("Then design breakthrough concepts that violate each assumption from first principles.");
            upgrades.add("Reframed around assumption-breaking first principles");
            upgrades.add("Linked root bottleneck directly to solution design");
        } else {
            out.append("Design around: ").append(clean).append("\n")
                    .append("Hard Creative Constraints: Must ship an testable MVP in 48 hours, require zero onboarding tutorial, ")
                    .append("and deliver value within 10 seconds of first interaction.");
            upgrades.add("Injected 48h MVP + 10-second time-to-value constraint box");
            upgrades.add("Eliminated bloated multi-step onboarding ideas");
        }

        if (graveyard != null && !graveyard.isEmpty()) {
            int activeCount = 0;
            for (GraveyardEntry g : graveyard) {
                if (g.activeInPrompts) activeCount++;
            }
            if (activeCount > 0) {
                upgrades.add("Bound " + activeCount + " Graveyard anti-pattern constraint(s)");
            }
        }

        return new PromptOptimizationResult(clean, framework + " (LOCAL_SYNTH)", out.toString(), 87, upgrades);
    }
}
