package com.chromecide.lowtalk.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A dropdown must never be able to grow wider than the panel it opens.
 *
 * <p>The client declines to open the panel at all in that state: no error, no event, nothing in any log, just a
 * control that looks fine, holds the right entries, shows the right selection, and does nothing when clicked. It
 * shipped that way once, in the list of passages for a "they have seen the passage" condition, and four more
 * were waiting for the first command whose only argument was a choice.
 *
 * <p>A control sized with FlexWeight takes whatever room its slot has, which is the whole row once its
 * neighbours are hidden. So a dropdown gets an explicit Width, and that width has to fit inside its PanelWidth.
 */
class DropdownWidthTest {

    private static final Pattern BOX = Pattern.compile("DropdownBox\\s+(#[A-Za-z0-9_]+)\\s*\\{(.*?)\\n\\s*\\}", Pattern.DOTALL);
    private static final Pattern WIDTH = Pattern.compile("Anchor:\\s*\\([^)]*Width:\\s*(\\d+)");
    private static final Pattern FLEX = Pattern.compile("FlexWeight:\\s*(\\d+)");
    private static final Pattern PANEL = Pattern.compile("PanelWidth:\\s*(\\d+)");

    @Test
    void noDropdownCanOutgrowItsPanel() throws IOException {
        Path root = Path.of("src/main/resources/Common/UI");
        assumeTrue(Files.isDirectory(root), "run from the project directory");
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".ui")).toList()) {
                String text = Files.readString(file);
                Matcher m = BOX.matcher(text);
                while (m.find()) {
                    String id = m.group(1);
                    String body = m.group(2);
                    Matcher width = WIDTH.matcher(body);
                    Matcher flex = FLEX.matcher(body);
                    Matcher panel = PANEL.matcher(body);
                    boolean hasWidth = width.find();
                    if (!hasWidth && flex.find()) {
                        bad.add(file.getFileName() + " " + id + ": FlexWeight with no Width, so it stretches to its slot");
                    } else if (hasWidth && panel.find()
                            && Integer.parseInt(width.group(1)) > Integer.parseInt(panel.group(1))) {
                        bad.add(file.getFileName() + " " + id + ": Width " + width.group(1)
                                + " is wider than PanelWidth " + panel.group(1));
                    }
                }
            }
        }
        assertTrue(bad.isEmpty(), "dropdowns that will not open their panel:\n  " + String.join("\n  ", bad));
    }
}
