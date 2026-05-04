package by.deokma.notely.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared Markdown parsing and rendering utilities.
 * Used by both NotepadScreen and PinnedNotesOverlay.
 */
public final class MarkdownRenderer {

    public enum LineType {NORMAL, H1, H2, H3, TODO_OPEN, TODO_DONE, HR, QUOTE, CODE, BOLD}

    // Colors
    public static final int COL_TEXT = 0xFF1A0A00;
    public static final int COL_HINT = 0xFF9B8A6A;
    public static final int COL_H1 = 0xFF3A2000;
    public static final int COL_H2 = 0xFF5A3800;
    public static final int COL_QUOTE = 0xFF7A6A50;
    public static final int COL_CHECKED = 0xFF4A8A3A;
    public static final int COL_CODE_BG = 0x22000000;

    private MarkdownRenderer() {
    }

    public static LineType detectLineType(String line) {
        if (line.startsWith("### ")) return LineType.H3;
        if (line.startsWith("## ")) return LineType.H2;
        if (line.startsWith("# ")) return LineType.H1;
        if (line.startsWith("[ ] ")) return LineType.TODO_OPEN;
        if (line.startsWith("[x] ")) return LineType.TODO_DONE;
        if (line.equals("---") || line.equals("***")) return LineType.HR;
        if (line.startsWith("> ")) return LineType.QUOTE;
        if (line.startsWith("`") && line.endsWith("`") && line.length() > 1) return LineType.CODE;
        return LineType.NORMAL;
    }

    /**
     * Returns the display text with MD prefix stripped.
     */
    public static String getDisplayText(String line, LineType type) {
        return switch (type) {
            case H1 -> line.substring(2);
            case H2 -> line.substring(3);
            case H3 -> line.substring(4);
            case TODO_OPEN, TODO_DONE -> line.substring(4);
            case QUOTE -> line.substring(2);
            case CODE -> line.substring(1, line.length() - 1);
            case HR -> "";
            default -> line;
        };
    }

    /**
     * Length of the MD prefix in the raw source string.
     */
    public static int prefixLen(LineType type) {
        return switch (type) {
            case H1 -> 2;
            case H2 -> 3;
            case H3 -> 4;
            case TODO_OPEN, TODO_DONE -> 4;
            case QUOTE -> 2;
            case CODE -> 1;
            default -> 0;
        };
    }

    /**
     * Extra X offset for indented line types.
     */
    public static int getTextXOffset(LineType type) {
        return switch (type) {
            case TODO_OPEN, TODO_DONE -> 11;
            case QUOTE -> 8;
            case CODE -> 4;
            default -> 0;
        };
    }

    /**
     * Draws a single rendered line segment in the notepad editor style.
     *
     * @param editorMaxW width of the editor area (for HR line)
     */
    public static void drawLine(GuiGraphicsExtractor g, Font font, String seg,
                                int x, int y, LineType type, int editorMaxW) {
        switch (type) {
            case H1 -> {
                g.text(font, seg, x, y, COL_H1, false);
                g.fill(x, y + font.lineHeight, x + font.width(seg), y + font.lineHeight + 1, COL_H1);
            }
            case H2 -> g.text(font, seg, x, y, COL_H2, false);
            case H3 -> g.text(font, seg, x, y, COL_HINT, false);
            case TODO_OPEN -> {
                drawCheckbox(g, x - 11, y, false);
                g.text(font, seg, x, y, COL_TEXT, false);
            }
            case TODO_DONE -> {
                drawCheckbox(g, x - 11, y, true);
                g.text(font, seg, x, y, 0xFF888877, false);
                g.fill(x, y + 2, x + font.width(seg), y + 2, 0xFF888877);
            }
            case HR -> {
                int mid = y + 6;
                g.fill(x, mid, x + editorMaxW, mid + 1, 0xAA8B7355);
            }
            case QUOTE -> {
                g.fill(x - 8, y, x - 6, y + 11, 0xFF8B7355);
                g.text(font, seg, x, y, COL_QUOTE, false);
            }
            case CODE -> {
                g.fill(x - 2, y - 1, x + font.width(seg) + 2, y + 12, COL_CODE_BG);
                g.text(font, seg, x, y, 0xFF4A7A30, false);
            }
            default -> g.text(font, seg, x, y, COL_TEXT, false);
        }
    }

    public static void drawCheckbox(GuiGraphicsExtractor g, int x, int y, boolean checked) {
        g.fill(x, y, x + 8, y + 8, 0x33000000);
        if (checked) {
            g.fill(x + 1, y + 1, x + 7, y + 7, COL_CHECKED);
            // Compact X, 4x4 pixels centered in the 6x6 inner area
            int lx = x + 2, ty = y + 2;
            g.fill(lx,     ty,     lx + 1, ty + 1, 0xFFFFFFFF);
            g.fill(lx + 3, ty,     lx + 4, ty + 1, 0xFFFFFFFF);
            g.fill(lx + 1, ty + 1, lx + 3, ty + 3, 0xFFFFFFFF);
            g.fill(lx,     ty + 3, lx + 1, ty + 4, 0xFFFFFFFF);
            g.fill(lx + 3, ty + 3, lx + 4, ty + 4, 0xFFFFFFFF);
        } else {
            g.fill(x + 1, y + 1, x + 7, y + 7, 0xFFEEE0C4);
        }
    }

    /**
     * Word-wraps a line to fit within maxW pixels.
     * Returns list of segments; empty line returns [""].
     */
    public static List<String> wrapLine(Font font, String line, int maxW) {
        List<String> result = new ArrayList<>();
        if (line.isEmpty()) {
            result.add("");
            return result;
        }

        String remaining = line;
        while (!remaining.isEmpty()) {
            if (font.width(remaining) <= maxW) {
                result.add(remaining);
                break;
            }

            int cut = remaining.length();
            while (cut > 0 && font.width(remaining.substring(0, cut)) > maxW) cut--;

            int spaceIndex = remaining.lastIndexOf(' ', cut);
            if (spaceIndex > 0) {
                result.add(remaining.substring(0, spaceIndex));
                remaining = remaining.substring(spaceIndex + 1);
            } else {
                result.add(remaining.substring(0, cut));
                remaining = remaining.substring(cut);
            }
        }
        return result;
    }
}
