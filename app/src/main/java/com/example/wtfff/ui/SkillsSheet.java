package com.example.wtfff.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.wtfff.R;
import com.example.wtfff.model.CreativeSkill;
import com.example.wtfff.model.IdeaCard;
import com.example.wtfff.storage.BrainstormRepository;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.List;
import java.util.UUID;

public class SkillsSheet {

    public interface Listener {
        void onSkillSelected(CreativeSkill skill, IdeaCard targetCardOrNull);
        void onCustomSkillsChanged();
    }

    public static void show(Context context,
                            BrainstormRepository repository,
                            CreativeSkill currentActiveSkill,
                            IdeaCard targetCardOrNull,
                            Listener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_skills, null);
        dialog.setContentView(root);

        TextView tvTitle = root.findViewById(R.id.tvSkillsSheetTitle);
        TextView tvSubtitle = root.findViewById(R.id.tvSkillsSheetSubtitle);
        TextView btnForge = root.findViewById(R.id.btnForgeCustomSkill);
        LinearLayout container = root.findViewById(R.id.containerSkillItems);

        if (targetCardOrNull != null) {
            tvTitle.setText("BRANCH_NODE // PICK_SKILL");
            tvSubtitle.setText("Run a creative skill cartridge on \"" + targetCardOrNull.title + "\"");
        }

        Runnable populateList = new Runnable() {
            @Override
            public void run() {
                container.removeAllViews();
                List<CreativeSkill> all = repository.loadAllSkills();
                float d = context.getResources().getDisplayMetrics().density;

                for (CreativeSkill skill : all) {
                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.VERTICAL);
                    boolean isCurrent = currentActiveSkill != null && skill.id.equals(currentActiveSkill.id);
                    row.setBackgroundResource(isCurrent ? R.drawable.bg_px_cartridge_selected : R.drawable.bg_px_cartridge);
                    int pad = Math.round(12 * d);
                    row.setPadding(pad, pad, pad, pad);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.bottomMargin = Math.round(8 * d);
                    row.setLayoutParams(lp);

                    LinearLayout topLine = new LinearLayout(context);
                    topLine.setOrientation(LinearLayout.HORIZONTAL);
                    topLine.setGravity(Gravity.CENTER_VERTICAL);

                    TextView badge = new TextView(context);
                    badge.setText("[" + skill.badge + "]");
                    badge.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    badge.setTextSize(11f);
                    badge.setTextColor(skill.accentColorHex);
                    topLine.addView(badge);

                    TextView name = new TextView(context);
                    name.setText("  " + skill.name);
                    name.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    name.setTextSize(13f);
                    name.setTextColor(0xFFECF6FF);
                    LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    name.setLayoutParams(nameLp);
                    topLine.addView(name);

                    if (!skill.builtIn) {
                        TextView delBtn = new TextView(context);
                        delBtn.setText("DEL");
                        delBtn.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                        delBtn.setTextSize(10f);
                        delBtn.setTextColor(0xFFFF3864);
                        delBtn.setPadding(Math.round(6 * d), Math.round(2 * d), Math.round(6 * d), Math.round(2 * d));
                        delBtn.setOnClickListener(v -> {
                            List<CreativeSkill> customs = repository.loadCustomSkills();
                            for (int i = customs.size() - 1; i >= 0; i--) {
                                if (skill.id.equals(customs.get(i).id)) {
                                    customs.remove(i);
                                }
                            }
                            repository.saveCustomSkills(customs);
                            if (listener != null) listener.onCustomSkillsChanged();
                            run();
                        });
                        topLine.addView(delBtn);
                    }

                    row.addView(topLine);

                    TextView desc = new TextView(context);
                    desc.setText(skill.subtitle);
                    desc.setTextColor(0xFF8DA4C7);
                    desc.setTextSize(12f);
                    desc.setPadding(0, Math.round(4 * d), 0, 0);
                    row.addView(desc);

                    row.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onSkillSelected(skill, targetCardOrNull);
                        }
                        dialog.dismiss();
                    });

                    container.addView(row);
                }
            }
        };

        populateList.run();

        btnForge.setOnClickListener(v -> showForgeSkillDialog(context, repository, () -> {
            if (listener != null) listener.onCustomSkillsChanged();
            populateList.run();
        }));

        dialog.show();
    }

    private static void showForgeSkillDialog(Context context,
                                             BrainstormRepository repository,
                                             Runnable onCreated) {
        float d = context.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * d);
        box.setPadding(pad, pad, pad, pad);
        box.setBackgroundColor(0xFF0F1424);

        EditText etName = new EditText(context);
        etName.setHint("Skill Name (e.g. Sci-Fi Worldbuilder)");
        etName.setTextColor(0xFFECF6FF);
        etName.setHintTextColor(0xFF516587);
        etName.setBackgroundResource(R.drawable.bg_px_terminal_input);
        etName.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        box.addView(etName);

        EditText etBadge = new EditText(context);
        etBadge.setHint("Short HUD Badge (e.g. SCI_FI)");
        etBadge.setTextColor(0xFF00F0FF);
        etBadge.setHintTextColor(0xFF516587);
        etBadge.setBackgroundResource(R.drawable.bg_px_terminal_input);
        etBadge.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp2.topMargin = Math.round(8 * d);
        etBadge.setLayoutParams(lp2);
        box.addView(etBadge);

        EditText etPrompt = new EditText(context);
        etPrompt.setHint("System instruction: how should AI mutate or branch ideas with this cartridge?");
        etPrompt.setMinLines(3);
        etPrompt.setGravity(Gravity.TOP | Gravity.START);
        etPrompt.setTextColor(0xFFECF6FF);
        etPrompt.setHintTextColor(0xFF516587);
        etPrompt.setBackgroundResource(R.drawable.bg_px_terminal_input);
        etPrompt.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp3.topMargin = Math.round(8 * d);
        etPrompt.setLayoutParams(lp3);
        box.addView(etPrompt);

        new AlertDialog.Builder(context)
                .setTitle("FORGE_CUSTOM_SKILL")
                .setView(box)
                .setPositiveButton("FORGE", (dlg, which) -> {
                    String name = etName.getText().toString().trim();
                    String badge = etBadge.getText().toString().trim().toUpperCase().replace(' ', '_');
                    String sys = etPrompt.getText().toString().trim();
                    if (name.isEmpty() || sys.isEmpty()) {
                        Toast.makeText(context, "Name and instruction required.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (badge.isEmpty()) {
                        badge = name.substring(0, Math.min(8, name.length())).toUpperCase().replace(' ', '_');
                    }
                    CreativeSkill custom = new CreativeSkill(
                            "custom_" + UUID.randomUUID().toString(),
                            name,
                            badge,
                            sys.length() > 70 ? sys.substring(0, 67) + "..." : sys,
                            sys,
                            0xFF39FF88,
                            3,
                            false
                    );
                    List<CreativeSkill> existing = repository.loadCustomSkills();
                    existing.add(custom);
                    repository.saveCustomSkills(existing);
                    if (onCreated != null) onCreated.run();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }
}
