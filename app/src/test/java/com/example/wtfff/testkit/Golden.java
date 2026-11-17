package com.example.wtfff.testkit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Golden text files (prompts sent to the model, exported Markdown, saved JSON shapes).
 * Lives in src/test/resources/golden. Same switch as the screenshots:
 * -Pstylelock.record=true rewrites them, otherwise any difference fails with both texts.
 */
public final class Golden {
    private static final File DIR = new File("src/test/resources/golden");

    private Golden() {
    }

    public static void text(String name, String actual) {
        File file = new File(DIR, name);
        String normalized = actual.replace("\r\n", "\n");
        if (Snap.recording()) {
            write(file, normalized);
            return;
        }
        if (!file.exists()) {
            File out = new File("build/stylelock/" + name);
            write(out, normalized);
            throw new AssertionError("No golden text " + name + ". Record it with -Pstylelock.record=true");
        }
        String expected;
        try {
            expected = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new AssertionError("Cannot read " + file, e);
        }
        if (!expected.equals(normalized)) {
            write(new File("build/stylelock/" + name), normalized);
            throw new AssertionError("Golden text " + name + " differs.\n--- expected ---\n" + expected
                    + "\n--- actual ---\n" + normalized);
        }
    }

    private static void write(File file, String content) {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new AssertionError("Cannot create " + dir);
        }
        try {
            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError("Cannot write " + file, e);
        }
    }
}
