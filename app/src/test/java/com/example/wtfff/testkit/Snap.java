package com.example.wtfff.testkit;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Style lock: renders a real view tree to pixels and compares it with a golden PNG.
 *
 * Verify (default): any differing pixel fails the test and writes
 * build/stylelock/NAME.actual.png and NAME.diff.png.
 * Record: run Gradle with -Pstylelock.record=true to (re)write the goldens in src/test/snapshots.
 */
public final class Snap {
    private static final File GOLDEN_DIR = new File("src/test/snapshots");
    private static final File OUT_DIR = new File("build/stylelock");

    private Snap() {
    }

    public static boolean recording() {
        return "true".equals(System.getProperty("stylelock.record"));
    }

    public static void window(String name, Activity activity) {
        view(name, activity.getWindow().getDecorView());
    }

    public static void dialog(String name, Dialog dialog) {
        view(name, dialog.getWindow().getDecorView());
    }

    public static void view(String name, View view) {
        Bitmap actual = render(view);
        File golden = new File(GOLDEN_DIR, name + ".png");
        if (recording()) {
            write(actual, golden);
            return;
        }
        if (!golden.exists()) {
            write(actual, new File(OUT_DIR, name + ".actual.png"));
            throw new AssertionError("No golden for " + name + ". Record it with -Pstylelock.record=true");
        }
        Bitmap expected = BitmapFactory.decodeFile(golden.getPath());
        String mismatch = compare(name, expected, actual);
        if (mismatch != null) {
            throw new AssertionError(mismatch);
        }
    }

    public static Bitmap render(View view) {
        Ui.idle();
        if (view.getWidth() <= 0 || view.getHeight() <= 0) {
            throw new AssertionError("View has no size yet: " + view);
        }
        Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bitmap));
        return bitmap;
    }

    private static String compare(String name, Bitmap expected, Bitmap actual) {
        if (expected.getWidth() != actual.getWidth() || expected.getHeight() != actual.getHeight()) {
            write(actual, new File(OUT_DIR, name + ".actual.png"));
            return "Style lock " + name + ": size changed from " + expected.getWidth() + "x" + expected.getHeight()
                    + " to " + actual.getWidth() + "x" + actual.getHeight();
        }
        int w = actual.getWidth();
        int h = actual.getHeight();
        int[] e = new int[w * h];
        int[] a = new int[w * h];
        expected.getPixels(e, 0, w, 0, 0, w, h);
        actual.getPixels(a, 0, w, 0, 0, w, h);
        int diffCount = 0;
        int minX = w;
        int minY = h;
        int maxX = -1;
        int maxY = -1;
        int[] d = new int[w * h];
        for (int i = 0; i < e.length; i++) {
            if (e[i] != a[i]) {
                diffCount++;
                int x = i % w;
                int y = i / w;
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
                d[i] = 0xFFFF0000;
            } else {
                int c = a[i];
                int grey = (((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF)) / 12;
                d[i] = 0xFF000000 | (grey << 16) | (grey << 8) | grey;
            }
        }
        if (diffCount == 0) {
            return null;
        }
        write(actual, new File(OUT_DIR, name + ".actual.png"));
        Bitmap diff = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        diff.setPixels(d, 0, w, 0, 0, w, h);
        write(diff, new File(OUT_DIR, name + ".diff.png"));
        return "Style lock " + name + ": " + diffCount + " pixel(s) differ, inside x " + minX + ".." + maxX
                + ", y " + minY + ".." + maxY + ". See build/stylelock/" + name + ".diff.png";
    }

    public static void write(Bitmap bitmap, File file) {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new AssertionError("Cannot create " + dir);
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException ex) {
            throw new AssertionError("Cannot write " + file, ex);
        }
    }
}
