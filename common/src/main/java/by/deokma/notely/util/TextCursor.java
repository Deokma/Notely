package by.deokma.notely.util;

import net.minecraft.util.Mth;

/**
 * Cursor navigation helpers for plain text editing.
 */
public final class TextCursor {

    private TextCursor() {}

    public static int wordBoundaryLeft(String t, int pos) {
        if (pos <= 0) return 0;
        int p = pos - 1;
        while (p > 0 && !Character.isLetterOrDigit(t.charAt(p))) p--;
        while (p > 0 && Character.isLetterOrDigit(t.charAt(p - 1))) p--;
        return p;
    }

    public static int wordBoundaryRight(String t, int pos) {
        if (pos >= t.length()) return t.length();
        int p = pos;
        while (p < t.length() && Character.isLetterOrDigit(t.charAt(p))) p++;
        while (p < t.length() && !Character.isLetterOrDigit(t.charAt(p))) p++;
        return p;
    }

    public static int lineStart(String t, int pos) {
        while (pos > 0 && t.charAt(pos - 1) != '\n') pos--;
        return pos;
    }

    public static int lineEnd(String t, int pos) {
        while (pos < t.length() && t.charAt(pos) != '\n') pos++;
        return pos;
    }

    public static int moveVertically(String t, int cursor, int dir) {
        int col = cursor - lineStart(t, cursor);
        String[] lines = t.split("\n", -1);
        int charCount = 0, lineIdx = 0;
        for (int i = 0; i < lines.length; i++) {
            if (cursor <= charCount + lines[i].length()) { lineIdx = i; break; }
            charCount += lines[i].length() + 1;
        }
        int target = Mth.clamp(lineIdx + dir, 0, lines.length - 1);
        int newPos = 0;
        for (int i = 0; i < target; i++) newPos += lines[i].length() + 1;
        return newPos + Math.min(col, lines[target].length());
    }

    /** Returns the logical line index (0-based) for the given cursor position. */
    public static int cursorLineIndex(String t, int cursor) {
        int line = 0;
        for (int i = 0; i < cursor && i < t.length(); i++) {
            if (t.charAt(i) == '\n') line++;
        }
        return line;
    }
}
