package com.example.wtfff;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.view.View;

import com.example.wtfff.testkit.FakeLlm;
import com.example.wtfff.testkit.Seed;
import com.example.wtfff.testkit.Snap;
import com.example.wtfff.testkit.Ui;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.concurrent.CountDownLatch;

/**
 * Style lock. Every screen, sheet and dialog of the app, rendered by the real code and
 * compared pixel-for-pixel with a golden PNG recorded from the original app.
 *
 * A failure means something visible changed. If the change is intended, it has to be shown
 * and approved first; then re-record with: gradlew :app:testDebugUnitTest -Pstylelock.record=true
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 36, qualifiers = "w411dp-h914dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class StyleLockTest {

    private static final String TALL = "w411dp-h2600dp-420dpi";
    /** Fixed port so the base URL shown in the BYOK sheet is the same on every run. */
    private static final int FIXED_PORT = 47123;

    private FakeLlm llm;

    @After
    public void closeLlm() {
        if (llm != null) {
            llm.close();
        }
    }

    private static Context app() {
        return RuntimeEnvironment.getApplication();
    }

    private static View rowButton(Activity a, int position, int id) {
        return Ui.row(a, position).findViewById(id);
    }

    /** A deep tree: ROOT_A > CHILD_B > GRAND_D > GREAT_G, ROOT_A > CHILD_C > GRAND_F, ROOT_E. */
    private static void seedDeepTree(boolean withStars) {
        org.json.JSONObject a = Seed.card("a", null, "ROOT_A", "SEED", 0xFF00F0FF);
        org.json.JSONObject b = Seed.card("b", "a", "CHILD_B", "SCAMPER", 0xFFFF2E93);
        org.json.JSONObject d = Seed.card("d", "b", "GRAND_D", "ANALOGY", 0xFFA855F7);
        org.json.JSONObject g = Seed.card("g", "d", "GREAT_G", "INVERT", 0xFFFFB800);
        org.json.JSONObject c = Seed.card("c", "a", "CHILD_C", "1ST_PRINC", 0xFF39FF88);
        org.json.JSONObject f = Seed.card("f", "c", "GRAND_F", "SCAMPER", 0xFFFF2E93);
        org.json.JSONObject e = Seed.card("e", null, "ROOT_E", "SEED", 0xFF00F0FF);
        if (withStars) {
            try {
                a.put("starred", true);
                d.put("starred", true);
                c.put("starred", true);
                g.put("starred", true);
            } catch (Exception ex) {
                throw new AssertionError(ex);
            }
        }
        Seed.sessions(app(), "s1", Seed.session("s1", "DEEP_TREE", "seed", a, b, d, g, c, f, e));
    }

    // ---- main screen --------------------------------------------------------------------

    @Test
    public void mainAll() {
        Snap.window("main_all", Ui.launch());
    }

    @Test
    public void mainStarred() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.tabFilterStarred);
        Snap.window("main_starred", a);
    }

    @Test
    public void mainRoots() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.tabFilterRoots);
        Snap.window("main_roots", a);
    }

    @Test
    public void mainSpliceOneArmed() {
        MainActivity a = Ui.launch();
        Ui.click(rowButton(a, 0, R.id.btnSelectMashup));
        Ui.tree(a).scrollToPosition(0);
        Snap.window("main_splice_one", a);
    }

    @Test
    public void mainSpliceTwoArmed() {
        MainActivity a = Ui.launch();
        Ui.click(rowButton(a, 0, R.id.btnSelectMashup));
        Ui.click(rowButton(a, 1, R.id.btnSelectMashup));
        Ui.tree(a).scrollToPosition(0);
        Snap.window("main_splice_two", a);
    }

    @Test
    public void mainCollapsed() {
        MainActivity a = Ui.launch();
        Ui.click(rowButton(a, 0, R.id.btnCollapseToggle));
        Snap.window("main_collapsed", a);
    }

    @Test
    public void mainStarToggled() {
        MainActivity a = Ui.launch();
        Ui.click(rowButton(a, 1, R.id.btnStar));
        Ui.tree(a).scrollToPosition(0);
        Snap.window("main_star_toggled", a);
    }

    @Test
    public void mainEmptyDeck() {
        Seed.sessions(app(), "s1", Seed.session("s1", "BLANK_SLATE", ""));
        Snap.window("main_empty", Ui.launch());
    }

    @Test
    public void mainLoadingBar() throws Exception {
        llm = new FakeLlm().install(app());
        MainActivity a = Ui.launch();
        CountDownLatch gate = llm.replyIdeasWhenReleased("Alpha Signal", "Beta Mesh");
        Ui.click(a, R.id.btnSparkIdeas);
        Ui.await("request sent", () -> llm.requestCount() == 1);
        Snap.window("main_loading", a);
        gate.countDown();
        Ui.await("reply applied", () -> Ui.rowCount(a) == 6);
    }

    @Test
    public void mainAfterSpark() {
        llm = new FakeLlm().install(app());
        MainActivity a = Ui.launch();
        llm.replyIdeas("Alpha Signal", "Beta Mesh");
        Ui.click(a, R.id.btnSparkIdeas);
        Ui.await("reply applied", () -> Ui.rowCount(a) == 6);
        Snap.window("main_after_spark", a);
    }

    @Test
    @Config(qualifiers = TALL)
    public void treeDeep() {
        seedDeepTree(false);
        Snap.window("tree_deep", Ui.launch());
    }

    @Test
    @Config(qualifiers = TALL)
    public void treeDeepStarred() {
        seedDeepTree(true);
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.tabFilterStarred);
        Snap.window("tree_deep_starred", a);
    }

    // ---- bottom sheets ------------------------------------------------------------------

    @Test
    public void sheetSkills() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnSkillsDeck);
        Snap.dialog("sheet_skills", Ui.dialog());
    }

    @Test
    public void sheetSkillsForBranch() {
        MainActivity a = Ui.launch();
        Ui.click(rowButton(a, 0, R.id.btnBranchPickSkill));
        Snap.dialog("sheet_skills_branch", Ui.dialog());
    }

    @Test
    public void sheetGraveyard() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnGraveyard);
        Snap.dialog("sheet_graveyard", Ui.dialog());
    }

    @Test
    public void sheetGraveyardMuted() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnGraveyard);
        Ui.clickText(Ui.dialogRoot(), "MUTE_RULE");
        Snap.dialog("sheet_graveyard_muted", Ui.dialog());
    }

    @Test
    public void sheetGraveyardEmpty() {
        Seed.sessions(app(), "s1", Seed.session("s1", "BLANK_SLATE", ""));
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnGraveyard);
        Snap.dialog("sheet_graveyard_empty", Ui.dialog());
    }

    @Test
    public void sheetByok() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnByokStatus);
        Snap.dialog("sheet_byok", Ui.dialog());
    }

    @Test
    public void sheetByokPingOnline() {
        llm = new FakeLlm(FIXED_PORT).install(app());
        MainActivity a = Ui.launch();
        llm.replyContent("LINK_ESTABLISHED");
        Ui.click(a, R.id.btnByokStatus);
        View sheet = Ui.dialogRoot();
        Ui.click(sheet.findViewById(R.id.btnPingConnection));
        Ui.await("ping result", () -> Ui.text(sheet, R.id.tvPingResult).startsWith("ONLINE"));
        Snap.dialog("sheet_byok_ping_online", Ui.dialog());
    }

    @Test
    public void sheetPromptLab() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnOptimizePrompt);
        Snap.dialog("sheet_prompt_lab", Ui.dialog());
    }

    @Test
    public void sheetPromptLabOfflineResult() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnOptimizePrompt);
        View sheet = Ui.dialogRoot();
        Ui.click(sheet.findViewById(R.id.btnRunPromptUpgrade));
        Snap.dialog("sheet_prompt_lab_offline", Ui.dialog());
    }

    // ---- dialogs ------------------------------------------------------------------------

    @Test
    public void dialogSessions() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnSessionPicker);
        Snap.dialog("dialog_sessions", Ui.alert());
    }

    @Test
    public void dialogSessionsWithDelete() {
        Seed.sessions(app(), "s1",
                Seed.session("s1", "FIRST_DECK", "one", Seed.card("x", null, "Only Card", "SEED", 0xFF00F0FF)),
                Seed.session("s2", "SECOND_DECK", "two"));
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnSessionPicker);
        Snap.dialog("dialog_sessions_delete", Ui.alert());
    }

    @Test
    public void dialogNewSession() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnSessionPicker);
        Ui.clickAlertItem(1);
        Snap.dialog("dialog_new_session", Ui.alert());
    }

    @Test
    public void dialogBanish() {
        MainActivity a = Ui.launch();
        Ui.click(rowButton(a, 0, R.id.btnBanishCard));
        Snap.dialog("dialog_banish", Ui.alert());
    }

    @Test
    public void dialogSparkFailed() {
        llm = new FakeLlm().install(app());
        MainActivity a = Ui.launch();
        llm.replyHttpError(500, "boom");
        Ui.click(a, R.id.btnSparkIdeas);
        Ui.await("error dialog", () -> "SPARK_FAILED".equals(Ui.showingAlertTitle()));
        Snap.dialog("dialog_spark_failed", Ui.alert());
    }

    @Test
    public void dialogForgeSkill() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnSkillsDeck);
        Ui.click(Ui.dialogRoot().findViewById(R.id.btnForgeCustomSkill));
        Snap.dialog("dialog_forge_skill", Ui.alert());
    }

    @Test
    public void dialogRenameSession() {
        MainActivity a = Ui.launch();
        Ui.click(a, R.id.btnSessionPicker);
        Ui.clickAlertItem(2);
        Snap.dialog("dialog_rename_session", Ui.alert());
        Ui.clickAlertButton(DialogInterface.BUTTON_NEGATIVE);
    }
}
