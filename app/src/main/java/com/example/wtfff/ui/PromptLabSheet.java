package com.example.wtfff.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wtfff.R;
import com.example.wtfff.ai.LocalPromptHeuristic;
import com.example.wtfff.ai.OpenAiCompatibleClient;
import com.example.wtfff.model.GraveyardEntry;
import com.example.wtfff.model.PromptOptimizationResult;
import com.example.wtfff.storage.AiConfig;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;

public class PromptLabSheet {

    public interface Listener {
        void onLoadPromptToDock(String optimizedPrompt);
        void onUseAndSparkNow(String optimizedPrompt);
    }

    public static void show(Context context,
                            String initialPrompt,
                            List<GraveyardEntry> graveyard,
                            AiConfig aiConfig,
                            OpenAiCompatibleClient aiClient,
                            Listener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_prompt_lab, null);
        dialog.setContentView(root);

        EditText etRaw = root.findViewById(R.id.etRawPromptLab);
        TextView chipCostar = root.findViewById(R.id.chipModeCostar);
        TextView chipDivergent = root.findViewById(R.id.chipModeDivergent);
        TextView chipSocratic = root.findViewById(R.id.chipModeSocratic);
        TextView chipConstraint = root.findViewById(R.id.chipModeConstraint);

        TextView btnRun = root.findViewById(R.id.btnRunPromptUpgrade);
        LinearLayout layoutResult = root.findViewById(R.id.layoutOptimizedResult);
        TextView tvClarity = root.findViewById(R.id.tvClarityScore);
        EditText etOptimized = root.findViewById(R.id.etOptimizedOutput);
        TextView tvUpgrades = root.findViewById(R.id.tvUpgradesList);
        TextView btnLoadOnly = root.findViewById(R.id.btnApplyPromptOnly);
        TextView btnSparkNow = root.findViewById(R.id.btnApplyAndSpark);

        if (initialPrompt != null && !initialPrompt.trim().isEmpty()) {
            etRaw.setText(initialPrompt.trim());
            etRaw.setSelection(etRaw.getText().length());
        }

        final String[] selectedMode = {"CO-STAR"};
        Runnable syncChips = () -> {
            styleChip(chipCostar, "CO-STAR".equals(selectedMode[0]));
            styleChip(chipDivergent, "10X_DIVERGENT".equals(selectedMode[0]));
            styleChip(chipSocratic, "SOCRATIC".equals(selectedMode[0]));
            styleChip(chipConstraint, "CONSTRAINT_BOX".equals(selectedMode[0]));
        };

        chipCostar.setOnClickListener(v -> { selectedMode[0] = "CO-STAR"; syncChips.run(); });
        chipDivergent.setOnClickListener(v -> { selectedMode[0] = "10X_DIVERGENT"; syncChips.run(); });
        chipSocratic.setOnClickListener(v -> { selectedMode[0] = "SOCRATIC"; syncChips.run(); });
        chipConstraint.setOnClickListener(v -> { selectedMode[0] = "CONSTRAINT_BOX"; syncChips.run(); });

        btnRun.setOnClickListener(v -> {
            String raw = etRaw.getText().toString().trim();
            if (raw.isEmpty()) {
                Toast.makeText(context, "Enter a rough idea seed first.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!aiConfig.isConfigured()) {
                PromptOptimizationResult local = LocalPromptHeuristic.upgradeLocally(raw, selectedMode[0], graveyard);
                renderResult(local, layoutResult, tvClarity, etOptimized, tvUpgrades);
                Toast.makeText(context, "Upgraded via local heuristic (Add BYOK key for live LLM rewrite)", Toast.LENGTH_SHORT).show();
                return;
            }

            btnRun.setEnabled(false);
            btnRun.setText("REWRITING_VIA_NEURAL_LINK...");

            aiClient.optimizePrompt(raw, selectedMode[0], graveyard, new OpenAiCompatibleClient.Callback<PromptOptimizationResult>() {
                @Override
                public void onSuccess(PromptOptimizationResult result) {
                    btnRun.setEnabled(true);
                    btnRun.setText("SYNTHESIZE_UPGRADED_PROMPT");
                    renderResult(result, layoutResult, tvClarity, etOptimized, tvUpgrades);
                }

                @Override
                public void onError(String errorMessage) {
                    btnRun.setEnabled(true);
                    btnRun.setText("SYNTHESIZE_UPGRADED_PROMPT");
                    PromptOptimizationResult fallback = LocalPromptHeuristic.upgradeLocally(raw, selectedMode[0], graveyard);
                    renderResult(fallback, layoutResult, tvClarity, etOptimized, tvUpgrades);
                    Toast.makeText(context, "API fallback used: " + errorMessage, Toast.LENGTH_LONG).show();
                }
            });
        });

        btnLoadOnly.setOnClickListener(v -> {
            String upgraded = etOptimized.getText().toString().trim();
            if (!upgraded.isEmpty() && listener != null) {
                listener.onLoadPromptToDock(upgraded);
                dialog.dismiss();
            }
        });

        btnSparkNow.setOnClickListener(v -> {
            String upgraded = etOptimized.getText().toString().trim();
            if (!upgraded.isEmpty() && listener != null) {
                listener.onUseAndSparkNow(upgraded);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private static void renderResult(PromptOptimizationResult res,
                                     LinearLayout layoutResult,
                                     TextView tvClarity,
                                     EditText etOptimized,
                                     TextView tvUpgrades) {
        layoutResult.setVisibility(View.VISIBLE);
        tvClarity.setText("CLARITY: " + res.clarityScore + "%");
        etOptimized.setText(res.optimizedPrompt);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < res.upgrades.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append("+ ").append(res.upgrades.get(i));
        }
        tvUpgrades.setText(sb.toString());
    }

    private static void styleChip(TextView chip, boolean active) {
        chip.setBackgroundResource(active ? R.drawable.bg_px_chip_active : R.drawable.bg_px_chip_idle);
        chip.setTextColor(active ? 0xFF00F0FF : 0xFF8DA4C7);
    }
}
