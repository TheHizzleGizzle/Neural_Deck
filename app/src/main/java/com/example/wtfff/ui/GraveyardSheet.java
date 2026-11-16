package com.example.wtfff.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.wtfff.R;
import com.example.wtfff.model.BrainstormSession;
import com.example.wtfff.model.GraveyardEntry;
import com.google.android.material.bottomsheet.BottomSheetDialog;

public class GraveyardSheet {

    public interface Listener {
        void onGraveyardUpdated();
        void onReviveEntry(GraveyardEntry entry);
    }

    public static void show(Context context,
                            BrainstormSession session,
                            Listener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        View root = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_graveyard, null);
        dialog.setContentView(root);

        TextView tvEmpty = root.findViewById(R.id.tvGraveEmpty);
        LinearLayout container = root.findViewById(R.id.containerGraveItems);

        Runnable render = new Runnable() {
            @Override
            public void run() {
                container.removeAllViews();
                if (session.graveyard.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                tvEmpty.setVisibility(View.GONE);
                float d = context.getResources().getDisplayMetrics().density;
                int pad = Math.round(12 * d);

                for (int i = 0; i < session.graveyard.size(); i++) {
                    final GraveyardEntry entry = session.graveyard.get(i);

                    LinearLayout card = new LinearLayout(context);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setBackgroundResource(R.drawable.bg_px_cartridge);
                    card.setPadding(pad, pad, pad, pad);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    );
                    lp.bottomMargin = Math.round(10 * d);
                    card.setLayoutParams(lp);

                    // Header row
                    LinearLayout header = new LinearLayout(context);
                    header.setOrientation(LinearLayout.HORIZONTAL);
                    header.setGravity(Gravity.CENTER_VERTICAL);

                    TextView tag = new TextView(context);
                    tag.setText("[" + entry.reasonTag + "]");
                    tag.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    tag.setTextSize(10f);
                    tag.setTextColor(0xFFFF3864);
                    header.addView(tag);

                    TextView state = new TextView(context);
                    state.setText(entry.activeInPrompts ? "  ● GUARDRAIL_ON" : "  ○ MUTED");
                    state.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                    state.setTextSize(10f);
                    state.setTextColor(entry.activeInPrompts ? 0xFF39FF88 : 0xFF516587);
                    LinearLayout.LayoutParams stateLp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    state.setLayoutParams(stateLp);
                    header.addView(state);

                    card.addView(header);

                    if (entry.archivedCard != null) {
                        TextView title = new TextView(context);
                        title.setText(entry.archivedCard.title);
                        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                        title.setTextColor(0xFFECF6FF);
                        title.setTextSize(13f);
                        title.setPadding(0, Math.round(6 * d), 0, 0);
                        card.addView(title);
                    }

                    TextView rule = new TextView(context);
                    rule.setText(entry.antiPatternRule);
                    rule.setTextColor(0xFF8DA4C7);
                    rule.setTextSize(12f);
                    rule.setPadding(0, Math.round(4 * d), 0, Math.round(8 * d));
                    card.addView(rule);

                    // Action buttons: Toggle Guardrail | Revive | Purge
                    LinearLayout actions = new LinearLayout(context);
                    actions.setOrientation(LinearLayout.HORIZONTAL);

                    TextView btnToggle = makeActionBtn(context,
                            entry.activeInPrompts ? "MUTE_RULE" : "ENABLE_RULE",
                            0xFF00F0FF, d);
                    btnToggle.setOnClickListener(v -> {
                        entry.activeInPrompts = !entry.activeInPrompts;
                        if (listener != null) listener.onGraveyardUpdated();
                        run();
                    });
                    actions.addView(btnToggle);

                    if (entry.archivedCard != null) {
                        TextView btnRevive = makeActionBtn(context, "REVIVE_NODE", 0xFF39FF88, d);
                        LinearLayout.LayoutParams rlp = (LinearLayout.LayoutParams) btnRevive.getLayoutParams();
                        rlp.leftMargin = Math.round(8 * d);
                        btnRevive.setOnClickListener(v -> {
                            session.graveyard.remove(entry);
                            if (listener != null) listener.onReviveEntry(entry);
                            run();
                        });
                        actions.addView(btnRevive);
                    }

                    TextView btnPurge = makeActionBtn(context, "PURGE", 0xFFFF3864, d);
                    LinearLayout.LayoutParams plp = (LinearLayout.LayoutParams) btnPurge.getLayoutParams();
                    plp.leftMargin = Math.round(8 * d);
                    btnPurge.setOnClickListener(v -> {
                        session.graveyard.remove(entry);
                        if (listener != null) listener.onGraveyardUpdated();
                        run();
                    });
                    actions.addView(btnPurge);

                    card.addView(actions);
                    container.addView(card);
                }
            }
        };

        render.run();
        dialog.show();
    }

    private static TextView makeActionBtn(Context context, String label, int color, float d) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tv.setTextSize(10f);
        tv.setTextColor(color);
        tv.setBackgroundResource(R.drawable.bg_px_chip_idle);
        tv.setPadding(Math.round(8 * d), Math.round(5 * d), Math.round(8 * d), Math.round(5 * d));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        return tv;
    }
}
