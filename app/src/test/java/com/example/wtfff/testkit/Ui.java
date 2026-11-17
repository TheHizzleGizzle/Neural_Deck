package com.example.wtfff.testkit;

import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.example.wtfff.MainActivity;
import com.example.wtfff.R;

import org.robolectric.Robolectric;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowToast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Drives the app the way a user does: through views found by id. Tests built on this
 * know nothing about the classes behind the screen, so they survive any refactor that
 * keeps the screen behaving the same.
 */
public final class Ui {
    private Ui() {
    }

    public static ActivityController<MainActivity> controller() {
        ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup();
        idle();
        return controller;
    }

    public static MainActivity launch() {
        return controller().get();
    }

    public static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Pumps the main thread until the condition holds; fails after 10 seconds. */
    public static void await(String what, BooleanSupplier condition) {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (true) {
            idle();
            if (condition.getAsBoolean()) {
                idle();
                return;
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for: " + what);
            }
            try {
                Thread.sleep(15);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted waiting for: " + what);
            }
        }
    }

    public static void click(View view) {
        if (!view.performClick()) {
            throw new AssertionError("View has no click listener: " + view);
        }
        idle();
    }

    public static void click(Activity activity, int id) {
        click(activity.findViewById(id));
    }

    public static String text(View root, int id) {
        return ((TextView) root.findViewById(id)).getText().toString();
    }

    public static String text(Activity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }

    public static boolean shown(Activity activity, int id) {
        return activity.findViewById(id).getVisibility() == View.VISIBLE;
    }

    /** First view under root whose text is exactly the given string, or null. Depth-first, in drawing order. */
    public static TextView findText(View root, String text) {
        if (root instanceof TextView && text.equals(((TextView) root).getText().toString())) {
            return (TextView) root;
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = findText(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** Every TextView text under root, depth-first. */
    public static List<String> allTexts(View root) {
        List<String> out = new ArrayList<>();
        collectTexts(root, out);
        return out;
    }

    private static void collectTexts(View view, List<String> out) {
        if (view instanceof TextView) {
            out.add(((TextView) view).getText().toString());
        }
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTexts(group.getChildAt(i), out);
            }
        }
    }

    /** Clicks the first view under root showing exactly this text. */
    public static void clickText(View root, String text) {
        TextView view = findText(root, text);
        if (view == null) {
            throw new AssertionError("No view with text " + text + " in " + allTexts(root));
        }
        click(view);
    }

    /** The content view of the newest dialog (alert or bottom sheet). */
    public static View dialogRoot() {
        return dialog().getWindow().getDecorView();
    }

    // ---- idea tree rows -------------------------------------------------------------

    public static RecyclerView tree(Activity activity) {
        return activity.findViewById(R.id.rvIdeaTree);
    }

    public static int rowCount(Activity activity) {
        RecyclerView.Adapter<?> adapter = tree(activity).getAdapter();
        return adapter == null ? 0 : adapter.getItemCount();
    }

    /** The row view at an adapter position, scrolling it on screen first. */
    public static View row(Activity activity, int position) {
        RecyclerView rv = tree(activity);
        rv.scrollToPosition(position);
        idle();
        RecyclerView.ViewHolder holder = rv.findViewHolderForAdapterPosition(position);
        if (holder == null) {
            throw new AssertionError("No row at position " + position + " (rows: " + rowCount(activity) + ")");
        }
        return holder.itemView;
    }

    public static String rowTitle(Activity activity, int position) {
        return text(row(activity, position), R.id.tvCardTitle);
    }

    public static List<String> rowTitles(Activity activity) {
        List<String> titles = new ArrayList<>();
        int n = rowCount(activity);
        for (int i = 0; i < n; i++) {
            titles.add(rowTitle(activity, i));
        }
        return titles;
    }

    /** Position of the first row with this title, or -1. */
    public static int rowOf(Activity activity, String title) {
        return rowTitles(activity).indexOf(title);
    }

    public static View rowNamed(Activity activity, String title) {
        int position = rowOf(activity, title);
        if (position < 0) {
            throw new AssertionError("No row titled " + title + " in " + rowTitles(activity));
        }
        return row(activity, position);
    }

    /** Indent of a row in dp: 0 for a root, depth * 18 + 6 for a nested card. */
    public static int rowIndentDp(Activity activity, int position) {
        View connector = row(activity, position).findViewById(R.id.branchConnector);
        float density = activity.getResources().getDisplayMetrics().density;
        return Math.round(connector.getWidth() / density);
    }

    /** "title@indentDp" for every row, top to bottom: the visible shape of the tree. */
    public static List<String> treeShape(Activity activity) {
        List<String> shape = new ArrayList<>();
        int n = rowCount(activity);
        for (int i = 0; i < n; i++) {
            shape.add(rowTitle(activity, i) + "@" + rowIndentDp(activity, i));
        }
        return shape;
    }

    // ---- dialogs, sheets, toasts -------------------------------------------------------

    public static Dialog dialog() {
        idle();
        Dialog dialog = ShadowDialog.getLatestDialog();
        if (dialog == null) {
            throw new AssertionError("No dialog was shown");
        }
        return dialog;
    }

    public static AlertDialog alert() {
        idle();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog == null) {
            throw new AssertionError("No alert dialog was shown");
        }
        return dialog;
    }

    public static String alertTitle() {
        CharSequence title = shadowOf(alert()).getTitle();
        return title == null ? "" : title.toString();
    }

    /** Title of the newest alert dialog that is still showing, or "" when none is. Never throws. */
    public static String showingAlertTitle() {
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        if (dialog == null || !dialog.isShowing()) {
            return "";
        }
        CharSequence title = shadowOf(dialog).getTitle();
        return title == null ? "" : title.toString();
    }

    /** True when the newest dialog of any kind (alert or bottom sheet) is showing. Never throws. */
    public static boolean dialogShowing() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        return dialog != null && dialog.isShowing();
    }

    public static String alertMessage() {
        CharSequence message = shadowOf(alert()).getMessage();
        return message == null ? "" : message.toString();
    }

    public static List<String> alertItems() {
        List<String> out = new ArrayList<>();
        CharSequence[] items = shadowOf(alert()).getItems();
        if (items != null) {
            for (CharSequence item : items) {
                out.add(item.toString());
            }
        }
        return out;
    }

    public static void clickAlertItem(int index) {
        shadowOf(alert()).clickOnItem(index);
        idle();
    }

    public static void clickAlertButton(int whichButton) {
        click(alert().getButton(whichButton));
    }

    public static String latestToast() {
        idle();
        return ShadowToast.getTextOfLatestToast();
    }
}
