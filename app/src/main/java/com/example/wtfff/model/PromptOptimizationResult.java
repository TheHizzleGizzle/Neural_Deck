package com.example.wtfff.model;

import java.util.ArrayList;
import java.util.List;

public class PromptOptimizationResult {
    public String originalPrompt;
    public String framework;
    public String optimizedPrompt;
    public int clarityScore;
    public List<String> upgrades = new ArrayList<>();

    public PromptOptimizationResult(String originalPrompt, String framework,
                                    String optimizedPrompt, int clarityScore,
                                    List<String> upgrades) {
        this.originalPrompt = originalPrompt;
        this.framework = framework;
        this.optimizedPrompt = optimizedPrompt;
        this.clarityScore = clarityScore;
        if (upgrades != null) {
            this.upgrades.addAll(upgrades);
        }
    }
}
