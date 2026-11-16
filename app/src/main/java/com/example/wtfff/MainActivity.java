package com.example.wtfff;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.wtfff.ai.OpenAiCompatibleClient;
import com.example.wtfff.model.BrainstormSession;
import com.example.wtfff.model.CreativeSkill;
import com.example.wtfff.model.GraveyardEntry;
import com.example.wtfff.model.IdeaCard;
import com.example.wtfff.storage.AiConfig;
import com.example.wtfff.storage.BrainstormRepository;
import com.example.wtfff.ui.ByokSettingsSheet;
import com.example.wtfff.ui.GraveyardSheet;
import com.example.wtfff.ui.IdeaCardAdapter;
import com.example.wtfff.ui.PromptLabSheet;
import com.example.wtfff.ui.SkillsSheet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements IdeaCardAdapter.Listener {

    private AiConfig aiConfig;
    private BrainstormRepository repository;
    private OpenAiCompatibleClient aiClient;

    private List<BrainstormSession> sessions = new ArrayList<>();
    private BrainstormSession activeSession;
    private List<CreativeSkill> allSkills = new ArrayList<>();
    private CreativeSkill activeSkill;

    private String filterMode = "ALL"; // ALL | STARRED | ROOTS
    private final Set<String> selectedMashupIds = new LinkedHashSet<>();

    // UI References
    private TextView tvSessionTitle;
    private TextView tvGraveCount;
    private ImageView ivByokIcon;
    private TextView tvByokStatus;
    private TextView tabFilterAll;
    private TextView tabFilterStarred;
    private TextView tabFilterRoots;
    private TextView tvActiveSkill;
    private LinearLayout layoutLoadingBar;
    private TextView tvLoadingStatus;
    private LinearLayout layoutMashupBar;
    private TextView tvMashupCount;
    private RecyclerView rvIdeaTree;
    private LinearLayout layoutEmptyState;
    private EditText etSeedPrompt;

    private IdeaCardAdapter cardAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        View rootWorkspace = findViewById(R.id.rootWorkspace);
        ViewCompat.setOnApplyWindowInsetsListener(rootWorkspace, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        aiConfig = new AiConfig(this);
        repository = new BrainstormRepository(this);
        aiClient = new OpenAiCompatibleClient(aiConfig);

        loadState();
        bindViews();
        refreshAllUi();
    }

    private void loadState() {
        sessions = repository.loadSessions();
        String savedId = repository.getActiveSessionId();
        activeSession = null;
        for (BrainstormSession s : sessions) {
            if (s.id.equals(savedId)) {
                activeSession = s;
                break;
            }
        }
        if (activeSession == null) {
            activeSession = sessions.get(0);
            repository.setActiveSessionId(activeSession.id);
        }

        allSkills = repository.loadAllSkills();
        activeSkill = allSkills.get(0);
    }

    private void bindViews() {
        LinearLayout btnSessionPicker = findViewById(R.id.btnSessionPicker);
        tvSessionTitle = findViewById(R.id.tvSessionTitle);
        LinearLayout btnGraveyard = findViewById(R.id.btnGraveyard);
        tvGraveCount = findViewById(R.id.tvGraveCount);
        LinearLayout btnByokStatus = findViewById(R.id.btnByokStatus);
        ivByokIcon = findViewById(R.id.ivByokIcon);
        tvByokStatus = findViewById(R.id.tvByokStatus);
        ImageButton btnExportTree = findViewById(R.id.btnExportTree);

        tabFilterAll = findViewById(R.id.tabFilterAll);
        tabFilterStarred = findViewById(R.id.tabFilterStarred);
        tabFilterRoots = findViewById(R.id.tabFilterRoots);
        LinearLayout btnSkillsDeck = findViewById(R.id.btnSkillsDeck);
        tvActiveSkill = findViewById(R.id.tvActiveSkill);

        layoutLoadingBar = findViewById(R.id.layoutLoadingBar);
        tvLoadingStatus = findViewById(R.id.tvLoadingStatus);

        layoutMashupBar = findViewById(R.id.layoutMashupBar);
        tvMashupCount = findViewById(R.id.tvMashupCount);
        TextView btnCancelMashup = findViewById(R.id.btnCancelMashup);
        TextView btnExecuteMashup = findViewById(R.id.btnExecuteMashup);

        rvIdeaTree = findViewById(R.id.rvIdeaTree);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        etSeedPrompt = findViewById(R.id.etSeedPrompt);
        LinearLayout btnOptimizePrompt = findViewById(R.id.btnOptimizePrompt);
        LinearLayout btnSparkIdeas = findViewById(R.id.btnSparkIdeas);

        cardAdapter = new IdeaCardAdapter(this);
        rvIdeaTree.setLayoutManager(new LinearLayoutManager(this));
        rvIdeaTree.setAdapter(cardAdapter);

        if (activeSession.seedPrompt != null && !activeSession.seedPrompt.isEmpty()) {
            etSeedPrompt.setText(activeSession.seedPrompt);
        }

        btnSessionPicker.setOnClickListener(v -> showSessionSwitcherDialog());
        btnGraveyard.setOnClickListener(v -> openGraveyardSheet());
        btnByokStatus.setOnClickListener(v -> openByokSheet());
        btnExportTree.setOnClickListener(v -> exportSessionMarkdown());

        tabFilterAll.setOnClickListener(v -> {
            filterMode = "ALL";
            refreshAllUi();
        });
        tabFilterStarred.setOnClickListener(v -> {
            filterMode = "STARRED";
            refreshAllUi();
        });
        tabFilterRoots.setOnClickListener(v -> {
            filterMode = "ROOTS";
            refreshAllUi();
        });

        btnSkillsDeck.setOnClickListener(v -> openSkillsSheet(null));

        btnCancelMashup.setOnClickListener(v -> {
            selectedMashupIds.clear();
            refreshAllUi();
        });

        btnExecuteMashup.setOnClickListener(v -> executeMashup());

        btnOptimizePrompt.setOnClickListener(v -> openPromptLab());
        btnSparkIdeas.setOnClickListener(v -> sparkRootIdeasFromDock());
    }

    private void refreshAllUi() {
        tvSessionTitle.setText(activeSession.title + " ▾");
        tvGraveCount.setText(String.valueOf(activeSession.graveyard.size()));

        boolean configured = aiConfig.isConfigured();
        int keyColor = configured ? 0xFF39FF88 : 0xFFFFB800;
        tvByokStatus.setText(configured ? aiConfig.getProviderLabel(aiConfig.getProviderId()) : "SET_KEY");
        tvByokStatus.setTextColor(keyColor);
        ImageViewCompat.setImageTintList(ivByokIcon, ColorStateList.valueOf(keyColor));

        styleFilterTab(tabFilterAll, "ALL".equals(filterMode));
        styleFilterTab(tabFilterStarred, "STARRED".equals(filterMode));
        styleFilterTab(tabFilterRoots, "ROOTS".equals(filterMode));

        if (activeSkill != null) {
            tvActiveSkill.setText("SKILL: " + activeSkill.badge);
        }

        if (!selectedMashupIds.isEmpty()) {
            layoutMashupBar.setVisibility(View.VISIBLE);
            int n = selectedMashupIds.size();
            tvMashupCount.setText(n + (n == 1 ? " NODE ARMED (PICK 2+)" : " NODES ARMED FOR SPLICE"));
        } else {
            layoutMashupBar.setVisibility(View.GONE);
        }

        List<IdeaCard> visible = activeSession.buildVisibleTree(filterMode);
        cardAdapter.submitData(activeSession, visible, activeSkill, selectedMashupIds);

        boolean empty = visible.isEmpty();
        layoutEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvIdeaTree.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void styleFilterTab(TextView tab, boolean active) {
        tab.setBackgroundResource(active ? R.drawable.bg_px_chip_active : R.drawable.bg_px_chip_idle);
        tab.setTextColor(active ? 0xFF00F0FF : 0xFF8DA4C7);
    }

    private void setLoading(boolean loading, String message) {
        layoutLoadingBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (message != null) {
            tvLoadingStatus.setText(message);
        }
    }

    private boolean ensureByokOrPrompt() {
        if (aiConfig.isConfigured()) {
            return true;
        }
        Toast.makeText(this, "Configure your BYOK API key first.", Toast.LENGTH_SHORT).show();
        openByokSheet();
        return false;
    }

    private void sparkRootIdeasFromDock() {
        String seed = etSeedPrompt.getText().toString().trim();
        if (seed.isEmpty()) {
            Toast.makeText(this, "Enter a seed idea or prompt in the bottom terminal.", Toast.LENGTH_SHORT).show();
            return;
        }
        activeSession.seedPrompt = seed;
        persistSessions();

        if (!ensureByokOrPrompt()) return;

        setLoading(true, "SPARKING_ROOT_NODES // [" + activeSkill.badge + "]...");
        aiClient.generateSeedIdeas(seed, activeSkill, activeSession.getActiveGraveyardEntries(),
                new OpenAiCompatibleClient.Callback<List<IdeaCard>>() {
                    @Override
                    public void onSuccess(List<IdeaCard> result) {
                        setLoading(false, null);
                        activeSession.cards.addAll(0, result);
                        filterMode = "ALL";
                        persistSessions();
                        refreshAllUi();
                        rvIdeaTree.scrollToPosition(0);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false, null);
                        showErrorDialog("SPARK_FAILED", errorMessage);
                    }
                });
    }

    private void branchCardWithSkill(IdeaCard parent, CreativeSkill skill) {
        if (!ensureByokOrPrompt()) return;

        parent.collapsed = false;
        setLoading(true, "BRANCHING [" + skill.badge + "] -> " + parent.title);
        aiClient.branchFromCard(
                parent,
                activeSession.seedPrompt,
                skill,
                activeSession.getActiveGraveyardEntries(),
                new OpenAiCompatibleClient.Callback<List<IdeaCard>>() {
                    @Override
                    public void onSuccess(List<IdeaCard> children) {
                        setLoading(false, null);
                        int insertIdx = activeSession.cards.indexOf(parent);
                        if (insertIdx >= 0 && insertIdx + 1 <= activeSession.cards.size()) {
                            activeSession.cards.addAll(insertIdx + 1, children);
                        } else {
                            activeSession.cards.addAll(children);
                        }
                        filterMode = "ALL";
                        persistSessions();
                        refreshAllUi();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false, null);
                        showErrorDialog("BRANCH_FAILED", errorMessage);
                    }
                }
        );
    }

    private void executeMashup() {
        if (selectedMashupIds.size() < 2) {
            Toast.makeText(this, "Tap [+SPLICE] on at least 2 idea cards to fuse them.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ensureByokOrPrompt()) return;

        List<IdeaCard> selectedCards = new ArrayList<>();
        for (String id : selectedMashupIds) {
            IdeaCard found = activeSession.findCardById(id);
            if (found != null) selectedCards.add(found);
        }
        if (selectedCards.size() < 2) return;

        setLoading(true, "SPLICING_" + selectedCards.size() + "_NODES_INTO_HYBRIDS...");
        aiClient.mashupCards(
                selectedCards,
                activeSession.seedPrompt,
                activeSession.getActiveGraveyardEntries(),
                new OpenAiCompatibleClient.Callback<List<IdeaCard>>() {
                    @Override
                    public void onSuccess(List<IdeaCard> hybrids) {
                        setLoading(false, null);
                        IdeaCard anchor = selectedCards.get(0);
                        anchor.collapsed = false;
                        int idx = activeSession.cards.indexOf(anchor);
                        if (idx >= 0 && idx + 1 <= activeSession.cards.size()) {
                            activeSession.cards.addAll(idx + 1, hybrids);
                        } else {
                            activeSession.cards.addAll(hybrids);
                        }
                        selectedMashupIds.clear();
                        filterMode = "ALL";
                        persistSessions();
                        refreshAllUi();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        setLoading(false, null);
                        showErrorDialog("SPLICE_FAILED", errorMessage);
                    }
                }
        );
    }

    private void openPromptLab() {
        String currentSeed = etSeedPrompt.getText().toString();
        PromptLabSheet.show(
                this,
                currentSeed,
                activeSession.getActiveGraveyardEntries(),
                aiConfig,
                aiClient,
                new PromptLabSheet.Listener() {
                    @Override
                    public void onLoadPromptToDock(String optimizedPrompt) {
                        etSeedPrompt.setText(optimizedPrompt);
                        etSeedPrompt.setSelection(etSeedPrompt.getText().length());
                        activeSession.seedPrompt = optimizedPrompt;
                        persistSessions();
                    }

                    @Override
                    public void onUseAndSparkNow(String optimizedPrompt) {
                        etSeedPrompt.setText(optimizedPrompt);
                        activeSession.seedPrompt = optimizedPrompt;
                        persistSessions();
                        sparkRootIdeasFromDock();
                    }
                }
        );
    }

    private void openByokSheet() {
        ByokSettingsSheet.show(this, aiConfig, aiClient, this::refreshAllUi);
    }

    private void openSkillsSheet(IdeaCard targetCardOrNull) {
        SkillsSheet.show(this, repository, activeSkill, targetCardOrNull, new SkillsSheet.Listener() {
            @Override
            public void onSkillSelected(CreativeSkill skill, IdeaCard targetCard) {
                activeSkill = skill;
                refreshAllUi();
                if (targetCard != null) {
                    branchCardWithSkill(targetCard, skill);
                }
            }

            @Override
            public void onCustomSkillsChanged() {
                allSkills = repository.loadAllSkills();
                boolean stillExists = false;
                for (CreativeSkill s : allSkills) {
                    if (activeSkill != null && s.id.equals(activeSkill.id)) {
                        stillExists = true;
                        break;
                    }
                }
                if (!stillExists && !allSkills.isEmpty()) {
                    activeSkill = allSkills.get(0);
                }
                refreshAllUi();
            }
        });
    }

    private void openGraveyardSheet() {
        GraveyardSheet.show(this, activeSession, new GraveyardSheet.Listener() {
            @Override
            public void onGraveyardUpdated() {
                persistSessions();
                refreshAllUi();
            }

            @Override
            public void onReviveEntry(GraveyardEntry entry) {
                if (entry.archivedCard != null) {
                    entry.archivedCard.parentId = null;
                    entry.archivedCard.depth = 0;
                    activeSession.cards.add(0, entry.archivedCard);
                }
                persistSessions();
                refreshAllUi();
                Toast.makeText(MainActivity.this, "Node revived to workspace.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSessionSwitcherDialog() {
        List<String> labels = new ArrayList<>();
        for (BrainstormSession s : sessions) {
            String marker = s.id.equals(activeSession.id) ? "● " : "  ";
            labels.add(marker + s.title + " (" + s.cards.size() + " nodes)");
        }
        labels.add("+ NEW_BLANK_SESSION");
        labels.add("✎ RENAME_CURRENT_SESSION");
        if (sessions.size() > 1) {
            labels.add("✕ DELETE_CURRENT_SESSION");
        }

        final int newIdx = sessions.size();
        final int renameIdx = sessions.size() + 1;
        final int deleteIdx = sessions.size() + 2;

        new AlertDialog.Builder(this)
                .setTitle("SELECT_DECK_SESSION")
                .setItems(labels.toArray(new String[0]), (dlg, which) -> {
                    if (which < sessions.size()) {
                        activeSession = sessions.get(which);
                        repository.setActiveSessionId(activeSession.id);
                        selectedMashupIds.clear();
                        etSeedPrompt.setText(activeSession.seedPrompt != null ? activeSession.seedPrompt : "");
                        refreshAllUi();
                    } else if (which == newIdx) {
                        promptCreateSession();
                    } else if (which == renameIdx) {
                        promptRenameSession();
                    } else if (which == deleteIdx && sessions.size() > 1) {
                        sessions.remove(activeSession);
                        activeSession = sessions.get(0);
                        repository.setActiveSessionId(activeSession.id);
                        persistSessions();
                        etSeedPrompt.setText(activeSession.seedPrompt != null ? activeSession.seedPrompt : "");
                        refreshAllUi();
                    }
                })
                .show();
    }

    private void promptCreateSession() {
        EditText input = createDialogInput("SESSION_CODENAME (e.g. NEXT_GEN_SYNTH)", "");
        new AlertDialog.Builder(this)
                .setTitle("NEW_BRAINSTORM_SESSION")
                .setView(wrapDialogInput(input))
                .setPositiveButton("CREATE", (d, w) -> {
                    String name = input.getText().toString().trim().toUpperCase().replace(' ', '_');
                    if (name.isEmpty()) {
                        name = "SESSION_0" + (sessions.size() + 1);
                    }
                    BrainstormSession created = new BrainstormSession(name, "");
                    sessions.add(0, created);
                    activeSession = created;
                    selectedMashupIds.clear();
                    repository.setActiveSessionId(created.id);
                    persistSessions();
                    etSeedPrompt.setText("");
                    refreshAllUi();
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private void promptRenameSession() {
        EditText input = createDialogInput("SESSION_CODENAME", activeSession.title);
        new AlertDialog.Builder(this)
                .setTitle("RENAME_SESSION")
                .setView(wrapDialogInput(input))
                .setPositiveButton("SAVE", (d, w) -> {
                    String name = input.getText().toString().trim().toUpperCase().replace(' ', '_');
                    if (!name.isEmpty()) {
                        activeSession.title = name;
                        persistSessions();
                        refreshAllUi();
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
    }

    private EditText createDialogInput(String hint, String prefill) {
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(10 * d);
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(prefill);
        et.setTextColor(0xFFECF6FF);
        et.setHintTextColor(0xFF516587);
        et.setBackgroundResource(R.drawable.bg_px_terminal_input);
        et.setPadding(pad, pad, pad, pad);
        return et;
    }

    private View wrapDialogInput(View child) {
        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(16 * d);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad, pad, pad);
        box.addView(child);
        return box;
    }

    private void exportSessionMarkdown() {
        String markdown = activeSession.toMarkdown();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("NeuralDeck Export", markdown));
        }
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, activeSession.title + " // Brainstorm Tree");
        sendIntent.putExtra(Intent.EXTRA_TEXT, markdown);
        startActivity(Intent.createChooser(sendIntent, "Export Markdown Tree (Copied to Clipboard)"));
    }

    private void showErrorDialog(String code, String details) {
        new AlertDialog.Builder(this)
                .setTitle(code)
                .setMessage(details)
                .setPositiveButton("BYOK_SETTINGS", (d, w) -> openByokSheet())
                .setNegativeButton("DISMISS", null)
                .show();
    }

    private void persistSessions() {
        activeSession.updatedAt = System.currentTimeMillis();
        repository.saveSessions(sessions);
    }

    // --- IdeaCardAdapter.Listener Callbacks ---

    @Override
    public void onQuickBranch(IdeaCard card) {
        branchCardWithSkill(card, activeSkill);
    }

    @Override
    public void onPickSkillBranch(IdeaCard card) {
        openSkillsSheet(card);
    }

    @Override
    public void onToggleCollapse(IdeaCard card) {
        card.collapsed = !card.collapsed;
        persistSessions();
        refreshAllUi();
    }

    @Override
    public void onToggleStar(IdeaCard card) {
        card.starred = !card.starred;
        persistSessions();
        refreshAllUi();
    }

    @Override
    public void onToggleSelectMashup(IdeaCard card) {
        if (selectedMashupIds.contains(card.id)) {
            selectedMashupIds.remove(card.id);
        } else {
            selectedMashupIds.add(card.id);
        }
        refreshAllUi();
    }

    @Override
    public void onCopyCard(IdeaCard card) {
        String text = "[" + card.skillBadge + "] " + card.title + "\n" + card.summary +
                (card.rationale.isEmpty() ? "" : ("\nWhy: " + card.rationale));
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Idea Node", text));
            Toast.makeText(this, "Copied node to clipboard", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBanishCard(IdeaCard card) {
        final String[] reasons = {
                "TOO_CLICHE — Generic or overdone trope",
                "OUT_OF_SCOPE — Too complex / wrong market",
                "WRONG_VIBE — Clashes with product vision",
                "DEAD_END — Flawed unit economics / UX",
                "SILENT_DELETE — Remove without Graveyard rule"
        };
        final String[] reasonCodes = {
                "TOO_CLICHE", "OUT_OF_SCOPE", "WRONG_VIBE", "DEAD_END", "DELETE_ONLY"
        };

        float d = getResources().getDisplayMetrics().density;
        EditText customNote = createDialogInput("Optional custom anti-pattern rule...", "");
        customNote.setMinLines(2);
        customNote.setGravity(Gravity.TOP | Gravity.START);

        new AlertDialog.Builder(this)
                .setTitle("BANISH_TO_GRAVEYARD")
                .setItems(reasons, (dlg, which) -> {
                    String code = reasonCodes[which];
                    List<IdeaCard> toRemove = activeSession.collectSubtree(card.id);
                    for (IdeaCard rem : toRemove) {
                        activeSession.cards.remove(rem);
                        selectedMashupIds.remove(rem.id);
                    }
                    if (!"DELETE_ONLY".equals(code)) {
                        String custom = customNote.getText().toString().trim();
                        GraveyardEntry entry = new GraveyardEntry(card, code, custom);
                        activeSession.graveyard.add(0, entry);
                        Toast.makeText(this, "Banished! AI will avoid [" + card.title + "]", Toast.LENGTH_SHORT).show();
                    }
                    persistSessions();
                    refreshAllUi();
                })
                .setView(wrapDialogInput(customNote))
                .setNegativeButton("CANCEL", null)
                .show();
    }
}
