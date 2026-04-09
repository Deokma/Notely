package by.deokma.notely.gui;

import by.deokma.notely.NotepadData;
import by.deokma.notely.NotepadData.Note;
import by.deokma.notely.util.MarkdownRenderer;
import by.deokma.notely.util.MarkdownRenderer.LineType;
import by.deokma.notely.util.TextCursor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;

public class NotepadScreen extends Screen {

    // ---- Layout constants ----
    private static final int W      = 440;
    private static final int H      = 280;
    private static final int LIST_W = 130;
    private static final int TORN   = 6;
    private static final int ROW_H  = 22;
    private static final int LINE_H = 12;

    // ---- Color palette ----
    private static final int COL_BG         = 0xFFEEE0C4;
    private static final int COL_LIST       = 0xFFD8C9A8;
    private static final int COL_SEL        = 0xFFC4B08A;
    private static final int COL_HOVER      = 0xFFCFBF9A;
    private static final int COL_BORDER     = 0xFF8B7355;
    private static final int COL_TORN       = 0xFFB8A888;
    private static final int COL_RULE       = 0x22886644;
    private static final int COL_RED_MARGIN = 0x88CC4433;
    private static final int COL_SHADOW     = 0x55000000;
    private static final int COL_TOOLBAR    = 0xFFCFBF9A;

    static final int[] STICKER_COLORS = {
        0xFFFFF176, 0xFFFFCC80, 0xFFA5D6A7,
        0xFF90CAF9, 0xFFCE93D8, 0xFFEF9A9A
    };

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("notely", "textures/gui/notepad.png");

    // ---- Torn-paper edge ----
    private final int[] tornTop, tornBot;

    // ---- State ----
    private Note selected = null;
    private int ox, oy;
    private int listOffset = 0;
    private int textOffset = 0;

    // ---- Cursor ----
    private int cursor = 0;
    private int cursorTimer = 0;
    private boolean cursorVisible = true;

    // ---- Title editing ----
    private boolean renamingTitle = false;
    private String titleBuffer = "";
    private int titleCursor = 0;

    // ---- Color picker ----
    private boolean pickingColor = false;

    // ---- Context menu ----
    private int contextMenuNoteIdx = -1;
    private int contextMenuX = 0, contextMenuY = 0;

    // ---- Undo ----
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 50;

    // ---- Rendered lines cache (rebuilt each frame) ----
    private record RenderedLine(int charStart, int charEnd, int screenY, LineType type, String raw) {}
    private final List<RenderedLine> renderedLines = new ArrayList<>();

    // ---- Widgets ----
    private Button btnAdd, btnPin, btnClose, btnHelp;
    private final List<Button> colorBtns = new ArrayList<>();

    // ---- GLFW modifier masks ----
    private static final int MOD_CTRL  = 2;
    private static final int MOD_SHIFT = 1;

    // =========================================================
    // Constructor
    // =========================================================

    public NotepadScreen() {
        super(Component.empty());
        var rng = new java.util.Random(42);
        int cols = (W - LIST_W) / 4 + 2;
        tornTop = new int[cols];
        tornBot = new int[cols];
        for (int i = 0; i < cols; i++) {
            tornTop[i] = rng.nextInt(TORN);
            tornBot[i] = rng.nextInt(TORN);
        }
        if (!NotepadData.notes.isEmpty()) {
            selected = NotepadData.notes.get(0);
            cursor = selected.content.length();
        }
    }

    // =========================================================
    // Init
    // =========================================================

    @Override
    protected void init() {
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        colorBtns.clear();

        int toolY = oy + TORN + 3;

        btnAdd = addRenderableWidget(Button.builder(
            Component.translatable("notely.button.new_note"), b -> newNote()
        ).pos(ox + 3, oy + H - TORN - 18).size(LIST_W - 6, 14).build());

        btnPin = addRenderableWidget(Button.builder(
            Component.translatable("notely.button.pin"), b -> toggleColorPicker()
        ).pos(ox + W - 34, toolY).size(14, 13).build());

        btnHelp = addRenderableWidget(Button.builder(
            Component.translatable("notely.button.help"), b -> createHelpNote()
        ).pos(ox + LIST_W - 16, toolY).size(14, 13).build());

        btnClose = addRenderableWidget(Button.builder(
            Component.translatable("notely.button.close"), b -> onClose()
        ).pos(ox + W - 18, toolY).size(15, 13).build());

        for (int i = 0; i < STICKER_COLORS.length; i++) {
            final int ci = i;
            Button cb = addRenderableWidget(Button.builder(
                Component.literal(" "), b -> pinWithColor(STICKER_COLORS[ci])
            ).pos(ox + W - 50 - i * 17, toolY + 1).size(15, 11).build());
            cb.visible = false;
            colorBtns.add(cb);
        }

        refreshWidgets();
    }

    private void refreshWidgets() {
        boolean has = selected != null;
        btnPin.active = has;
        colorBtns.forEach(b -> b.visible = pickingColor && has);
    }

    // =========================================================
    // Note management
    // =========================================================

    private void newNote() {
        Note n = NotepadData.createNote();
        selected = n;
        cursor = 0;
        textOffset = 0;
        renamingTitle = true;
        titleBuffer = n.title;
        titleCursor = titleBuffer.length();
        refreshWidgets();
    }

    private void createHelpNote() {
        Note note = NotepadData.createNote();
        note.title = Component.translatable("notely.help.title").getString();
        note.content = Component.translatable("notely.help.content").getString();
        selected = note;
        cursor = note.content.length();
        textOffset = 0;
        renamingTitle = false;
        NotepadData.save();
        refreshWidgets();
    }

    private void deleteSelected() {
        if (selected == null) return;
        NotepadData.deleteNote(selected.id);
        selected = NotepadData.notes.isEmpty() ? null : NotepadData.notes.get(0);
        cursor = selected != null ? selected.content.length() : 0;
        textOffset = 0;
        renamingTitle = false;
        refreshWidgets();
    }

    private void openNote(Note note) {
        if (selected != null) NotepadData.save();
        selected = note;
        cursor = note.content.length();
        textOffset = 0;
        renamingTitle = false;
        pickingColor = false;
        refreshWidgets();
    }

    // =========================================================
    // Sticker / color picker
    // =========================================================

    private void toggleColorPicker() {
        pickingColor = !pickingColor;
        refreshWidgets();
    }

    private void pinWithColor(int color) {
        if (selected == null) return;
        float px = width - 210f;
        float py = 40f + (NotepadData.stickers.size() * 25) % (height - 140);
        NotepadData.pinNote(selected.id, px, py, color);
        pickingColor = false;
        refreshWidgets();
    }

    // =========================================================
    // Title rename
    // =========================================================

    private void commitRename() {
        if (selected != null)
            selected.title = titleBuffer.isBlank() ? "Note" : titleBuffer.trim();
        renamingTitle = false;
        NotepadData.save();
    }

    // =========================================================
    // Lifecycle
    // =========================================================

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float dt) {}

    @Override
    public void onClose() {
        if (renamingTitle && selected != null) commitRename();
        NotepadData.save();
        super.onClose();
    }

    @Override
    public void tick() {
        if (++cursorTimer >= 10) { cursorTimer = 0; cursorVisible = !cursorVisible; }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // =========================================================
    // Keyboard input
    // =========================================================

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }

        // Space goes to text, not focused button
        if (key == GLFW.GLFW_KEY_SPACE && !renamingTitle && selected != null) {
            typeChar(' '); return true;
        }

        if (renamingTitle) return handleTitleKey(key);
        if (selected == null) return super.keyPressed(key, scan, mods);

        String t = selected.content;
        boolean ctrl = (mods & MOD_CTRL) != 0;

        if (ctrl && key == GLFW.GLFW_KEY_Z) { undo(); return true; }

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                pushUndo();
                if (ctrl) {
                    int p = TextCursor.wordBoundaryLeft(t, cursor);
                    selected.content = t.substring(0, p) + t.substring(cursor);
                    cursor = p;
                } else if (cursor > 0) {
                    selected.content = t.substring(0, cursor - 1) + t.substring(cursor);
                    cursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                pushUndo();
                if (ctrl) {
                    int p = TextCursor.wordBoundaryRight(t, cursor);
                    selected.content = t.substring(0, cursor) + t.substring(p);
                } else if (cursor < t.length()) {
                    selected.content = t.substring(0, cursor) + t.substring(cursor + 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT  -> { cursor = ctrl ? TextCursor.wordBoundaryLeft(t, cursor)  : Math.max(0, cursor - 1);          return true; }
            case GLFW.GLFW_KEY_RIGHT -> { cursor = ctrl ? TextCursor.wordBoundaryRight(t, cursor) : Math.min(t.length(), cursor + 1); return true; }
            case GLFW.GLFW_KEY_UP    -> { cursor = TextCursor.moveVertically(t, cursor, -1); return true; }
            case GLFW.GLFW_KEY_DOWN  -> { cursor = TextCursor.moveVertically(t, cursor,  1); return true; }
            case GLFW.GLFW_KEY_HOME  -> { cursor = ctrl ? 0          : TextCursor.lineStart(t, cursor); return true; }
            case GLFW.GLFW_KEY_END   -> { cursor = ctrl ? t.length() : TextCursor.lineEnd(t, cursor);   return true; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { typeChar('\n'); return true; }
            case GLFW.GLFW_KEY_TAB                            -> { typeChar('\t'); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    private boolean handleTitleKey(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { commitRename(); return true; }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (titleCursor > 0) {
                    titleBuffer = titleBuffer.substring(0, titleCursor - 1) + titleBuffer.substring(titleCursor);
                    titleCursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT  -> { titleCursor = Math.max(0, titleCursor - 1); return true; }
            case GLFW.GLFW_KEY_RIGHT -> { titleCursor = Math.min(titleBuffer.length(), titleCursor + 1); return true; }
        }
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (renamingTitle) {
            if (c >= 32 && c != 127 && titleBuffer.length() < 40) {
                titleBuffer = titleBuffer.substring(0, titleCursor) + c + titleBuffer.substring(titleCursor);
                titleCursor++;
            }
            return true;
        }
        if (selected != null && c >= 32 && c != 127) { typeChar(c); return true; }
        return false;
    }

    private void typeChar(char c) {
        if (selected.content.length() >= NotepadData.MAX_CONTENT_LENGTH) return;
        pushUndo();
        selected.content = selected.content.substring(0, cursor) + c + selected.content.substring(cursor);
        cursor++;
    }

    // =========================================================
    // Undo
    // =========================================================

    private void pushUndo() {
        if (selected == null) return;
        if (undoStack.size() >= MAX_UNDO) undoStack.pollFirst();
        undoStack.addLast(selected.content + "\u0000" + cursor);
    }

    private void undo() {
        if (undoStack.isEmpty() || selected == null) return;
        String snapshot = undoStack.pollLast();
        int sep = snapshot.lastIndexOf('\u0000');
        if (sep >= 0) {
            selected.content = snapshot.substring(0, sep);
            try { cursor = Math.min(Integer.parseInt(snapshot.substring(sep + 1)), selected.content.length()); }
            catch (NumberFormatException ignored) { cursor = selected.content.length(); }
        }
    }

    // =========================================================
    // Mouse input
    // =========================================================

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int ix = (int) mx, iy = (int) my;

        if (contextMenuNoteIdx >= 0) {
            handleContextMenuClick(ix, iy);
            contextMenuNoteIdx = -1;
            return true;
        }

        // RMB on note list → context menu
        if (btn == 1 && ix >= ox + 2 && ix < ox + LIST_W - 2) {
            int visible = visibleListRows();
            for (int i = 0; i < visible; i++) {
                int idx = listOffset + i;
                if (idx >= NotepadData.notes.size()) break;
                int ry = oy + TORN + 16 + i * ROW_H;
                if (iy >= ry && iy < ry + ROW_H) {
                    contextMenuNoteIdx = idx;
                    contextMenuX = ix;
                    contextMenuY = iy;
                    return true;
                }
            }
        }

        // LMB on note list
        if (btn == 0 && ix >= ox + 2 && ix < ox + LIST_W - 2) {
            int visible = visibleListRows();
            for (int i = 0; i < visible; i++) {
                int idx = listOffset + i;
                if (idx >= NotepadData.notes.size()) break;
                int ry = oy + TORN + 16 + i * ROW_H;
                if (iy >= ry && iy < ry + ROW_H) { openNote(NotepadData.notes.get(idx)); return true; }
            }
        }

        // Clicks in editor area
        if (ix > ox + LIST_W && selected != null && !renamingTitle) {
            int titleY = oy + TORN + 18;
            if (iy >= titleY && iy < titleY + 11 && ix < ox + W - 60) {
                renamingTitle = true;
                titleBuffer = selected.title;
                titleCursor = titleBuffer.length();
                return true;
            }

            int contentY = oy + TORN + 32;
            int clipBot = oy + H - TORN - 20;
            if (iy >= contentY && iy < clipBot) {
                for (RenderedLine rl : renderedLines) {
                    if (iy >= rl.screenY() && iy < rl.screenY() + LINE_H) {
                        int checkX = ox + LIST_W + 22;
                        if (ix >= checkX - 2 && ix < checkX + 9
                                && (rl.type() == LineType.TODO_OPEN || rl.type() == LineType.TODO_DONE)) {
                            toggleTodo(rl.charStart());
                            return true;
                        }
                        cursor = clickPosToCursor(ix, rl);
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    private void handleContextMenuClick(int ix, int iy) {
        int itemH = 14, menuW = 90;
        for (int i = 0; i < 3; i++) {
            int itemY = contextMenuY + i * itemH;
            if (ix >= contextMenuX && ix < contextMenuX + menuW && iy >= itemY && iy < itemY + itemH) {
                if (contextMenuNoteIdx < NotepadData.notes.size()) {
                    Note note = NotepadData.notes.get(contextMenuNoteIdx);
                    switch (i) {
                        case 0 -> openNote(note);
                        case 1 -> { openNote(note); renamingTitle = true; titleBuffer = note.title; titleCursor = titleBuffer.length(); }
                        case 2 -> NotepadData.deleteNote(note.id);
                    }
                }
                break;
            }
        }
    }

    private int clickPosToCursor(int mx, RenderedLine rl) {
        int textX = ox + LIST_W + 22 + MarkdownRenderer.getTextXOffset(rl.type());
        String display = MarkdownRenderer.getDisplayText(rl.raw(), rl.type());
        int relX = mx - textX;
        if (relX <= 0) return rl.charStart() + MarkdownRenderer.prefixLen(rl.type());
        for (int i = 0; i <= display.length(); i++) {
            int w = font.width(display.substring(0, i));
            if (w >= relX) {
                int wPrev = i > 0 ? font.width(display.substring(0, i - 1)) : 0;
                int chosen = (relX - wPrev < w - relX) ? i - 1 : i;
                return rl.charStart() + MarkdownRenderer.prefixLen(rl.type()) + Math.max(0, chosen);
            }
        }
        return rl.charEnd();
    }

    private void toggleTodo(int lineCharStart) {
        String t = selected.content;
        if (lineCharStart + 4 > t.length()) return;
        String prefix = t.substring(lineCharStart, lineCharStart + 4);
        if (prefix.equals("[ ] "))
            selected.content = t.substring(0, lineCharStart) + "[x] " + t.substring(lineCharStart + 4);
        else if (prefix.equals("[x] "))
            selected.content = t.substring(0, lineCharStart) + "[ ] " + t.substring(lineCharStart + 4);
        NotepadData.save();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if ((int) mx < ox + LIST_W) {
            listOffset = Mth.clamp((int) (listOffset - dy), 0, Math.max(0, NotepadData.notes.size() - visibleListRows()));
        } else {
            textOffset = Mth.clamp((int) (textOffset - dy), 0, Math.max(0, countTextLines() - visibleEditorLines()));
        }
        return true;
    }

    // =========================================================
    // Render
    // =========================================================

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        drawFrame(g);
        drawList(g, mx, my);
        drawEditor(g);
        super.render(g, mx, my, dt);
        drawColorPickerOverlay(g);
        drawContextMenu(g);
    }

    private void drawFrame(GuiGraphics g) {
        int x = ox, y = oy;
        g.fill(x + 4, y + 4, x + W + 4, y + H + 4, COL_SHADOW);
        g.fill(x, y, x + W, y + H, COL_BG);
        g.fill(x, y, x + LIST_W, y + H, COL_LIST);
        g.fill(x + LIST_W, y, x + LIST_W + 1, y + H, COL_BORDER);

        // Torn top edge
        for (int col = 0; col < W; col += 4) {
            int t = tornTop[Math.min(col / 4, tornTop.length - 1)];
            g.fill(x + col, y, x + col + 4, y + t, 0xFF1E1E1E);
            g.fill(x + col, y + t, x + col + 4, y + t + 2, COL_TORN);
        }
        // Torn bottom edge
        for (int col = 0; col < W; col += 4) {
            int t = tornBot[Math.min(col / 4, tornBot.length - 1)];
            int base = y + H - TORN;
            g.fill(x + col, base + t, x + col + 4, y + H, 0xFF1E1E1E);
            g.fill(x + col, base + t - 1, x + col + 4, base + t + 1, COL_TORN);
        }

        // Toolbar strip
        g.fill(x + LIST_W + 1, y + TORN, x + W, y + TORN + 17, COL_TOOLBAR);
        g.fill(x + LIST_W + 1, y + TORN + 16, x + W, y + TORN + 17, COL_BORDER);

        // Binding holes
        for (int i = 0; i < 3; i++) {
            int hy = y + 30 + i * 75;
            g.fill(x + LIST_W + 3, hy, x + LIST_W + 12, hy + 8, 0xFF111111);
            g.fill(x + LIST_W + 4, hy + 1, x + LIST_W + 11, hy + 7, 0xFF000000);
            g.fill(x + LIST_W + 4, hy + 1, x + LIST_W + 7, hy + 3, 0x22FFFFFF);
        }

        // Red margin line + ruled lines
        int mx2 = ox + LIST_W + 22;
        g.fill(mx2, oy + TORN + 17, mx2 + 1, oy + H - TORN - 20, COL_RED_MARGIN);
        int ruledY = oy + TORN + 34 + LINE_H;
        while (ruledY < oy + H - TORN - 20) {
            g.fill(mx2 - 1, ruledY, ox + W - 8, ruledY + 1, COL_RULE);
            ruledY += LINE_H;
        }
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        g.drawString(font, Component.translatable("notely.list.header").getString(), ox + 4, oy + TORN + 4, MarkdownRenderer.COL_HINT, false);

        int clipY1 = oy + TORN + 14, clipY2 = oy + H - TORN - 20;
        g.enableScissor(ox + 1, clipY1, ox + LIST_W - 1, clipY2);

        int visible = visibleListRows();
        for (int i = 0; i <= visible; i++) {
            int idx = listOffset + i;
            if (idx >= NotepadData.notes.size()) break;
            Note note = NotepadData.notes.get(idx);
            int ry = oy + TORN + 16 + i * ROW_H;
            boolean hov = mx >= ox + 2 && mx < ox + LIST_W - 2 && my >= ry && my < ry + ROW_H;
            boolean isSel = note == selected;
            if (isSel)       g.fill(ox + 2, ry, ox + LIST_W - 2, ry + ROW_H - 2, COL_SEL);
            else if (hov)    g.fill(ox + 2, ry, ox + LIST_W - 2, ry + ROW_H - 2, COL_HOVER);
            boolean pinned = NotepadData.stickers.stream().anyMatch(s -> s.noteId.equals(note.id));
            int maxTW = LIST_W - (pinned ? 22 : 8);
            g.drawString(font, font.plainSubstrByWidth(note.title, maxTW), ox + 5, ry + 6,
                isSel ? MarkdownRenderer.COL_TEXT : MarkdownRenderer.COL_HINT, false);
            if (pinned) g.drawString(font, Component.translatable("notely.list.pinned_marker").getString(), ox + LIST_W - 10, ry + 6, 0xFFFF8800, false);
        }

        g.disableScissor();

        // Scrollbar
        int total = NotepadData.notes.size();
        if (total > visible) {
            float scroll = (float) listOffset / (total - visible);
            int barH = clipY2 - clipY1;
            int thumbH = Math.max(10, barH * visible / total);
            int thumbY = clipY1 + (int) ((barH - thumbH) * scroll);
            g.fill(ox + LIST_W - 5, clipY1, ox + LIST_W - 3, clipY2, 0x33000000);
            g.fill(ox + LIST_W - 5, thumbY, ox + LIST_W - 3, thumbY + thumbH, 0x88000000);
        }
    }

    private void drawEditor(GuiGraphics g) {
        int ex = ox + LIST_W + 22;
        int titleY = oy + TORN + 18;
        int contentY = titleY + 14;
        int clipTop = contentY - 2, clipBot = oy + H - TORN - 20;

        if (selected == null) {
            g.drawString(font, Component.translatable("notely.editor.select_note").getString(), ex, contentY + 20, MarkdownRenderer.COL_HINT, false);
            return;
        }

        drawTitle(g, ex, titleY);

        g.enableScissor(ox + LIST_W + 2, clipTop, ox + W - 2, clipBot);
        renderedLines.clear();

        if (selected.content.isEmpty()) {
            g.drawString(font, Component.translatable("notely.editor.start_writing").getString(), ex, contentY, MarkdownRenderer.COL_HINT, false);
        }

        drawContent(g, ex, contentY, clipTop, clipBot);
        drawContentScrollbar(g, clipTop, clipBot);

        g.disableScissor();
    }

    private void drawTitle(GuiGraphics g, int ex, int titleY) {
        if (renamingTitle) {
            g.fill(ex - 2, titleY - 1, ox + W - 22, titleY + 11, 0x33FFFFFF);
            g.drawString(font, titleBuffer, ex, titleY + 1, MarkdownRenderer.COL_TEXT, false);
            if (cursorVisible) {
                int cx = ex + font.width(titleBuffer.substring(0, titleCursor));
                g.fill(cx, titleY - 1, cx + 1, titleY + 10, MarkdownRenderer.COL_TEXT);
            }
            g.drawString(font, Component.translatable("notely.editor.rename_hint").getString(), ox + LIST_W + 210, titleY + 1, MarkdownRenderer.COL_HINT, false);
        } else {
            String title = font.plainSubstrByWidth(selected.title, W - LIST_W - 70);
            g.drawString(font, title, ex, titleY + 1, MarkdownRenderer.COL_TEXT, false);
            g.drawString(font, Component.translatable("notely.editor.click_to_rename").getString(), ex + font.width(title), titleY + 1, MarkdownRenderer.COL_HINT, false);
        }
    }

    private void drawContent(GuiGraphics g, int ex, int contentY, int clipTop, int clipBot) {
        String text = selected.content;
        String[] rawLines = text.split("\n", -1);
        int dy = contentY - textOffset * LINE_H;
        int ci = 0;
        int maxW = editorMaxW();

        outer:
        for (int li = 0; li < rawLines.length; li++) {
            String raw = rawLines[li];
            LineType type = MarkdownRenderer.detectLineType(raw);
            String display = MarkdownRenderer.getDisplayText(raw, type);
            int xOff = MarkdownRenderer.getTextXOffset(type);
            List<String> wrapped = MarkdownRenderer.wrapLine(font, display, maxW - xOff);
            if (wrapped.isEmpty()) wrapped.add("");

            for (int wi = 0; wi < wrapped.size(); wi++) {
                String seg = wrapped.get(wi);
                int segCharStart = ci;
                int segCharEnd = ci + raw.length();

                if (dy >= clipTop - LINE_H && dy <= clipBot) {
                    renderedLines.add(new RenderedLine(segCharStart, segCharEnd, dy, type, raw));
                    MarkdownRenderer.drawLine(g, font, seg, ex + xOff, dy, type, maxW);
                }

                // Draw cursor
                if (!renamingTitle && cursorVisible && cursor >= ci && cursor <= ci + raw.length()) {
                    int posInDisplay = Math.max(0, cursor - ci - MarkdownRenderer.prefixLen(type));
                    if (posInDisplay <= display.length() && dy >= clipTop && dy <= clipBot) {
                        String beforeCursor = font.plainSubstrByWidth(display.substring(0, posInDisplay), maxW);
                        int cx = ex + xOff + font.width(beforeCursor);
                        g.fill(cx, dy - 1, cx + 1, dy + font.lineHeight, MarkdownRenderer.COL_TEXT);
                    }
                }

                if (wi < wrapped.size() - 1) {
                    dy += LINE_H;
                    if (dy > clipBot + LINE_H) break outer;
                }
            }
            if (li < rawLines.length - 1) ci++;
            ci += raw.length();
            dy += LINE_H;
            if (dy > clipBot + LINE_H) break;
        }
    }

    private void drawContentScrollbar(GuiGraphics g, int clipTop, int clipBot) {
        int total = countTextLines(), vis = visibleEditorLines();
        if (total > vis) {
            float scroll = (float) textOffset / (total - vis);
            int barH = clipBot - clipTop;
            int thumbH = Math.max(10, barH * vis / total);
            int thumbY = clipTop + (int) ((barH - thumbH) * scroll);
            g.fill(ox + W - 7, clipTop, ox + W - 5, clipBot, 0x33000000);
            g.fill(ox + W - 7, thumbY, ox + W - 5, thumbY + thumbH, 0x88000000);
        }
    }

    private void drawContextMenu(GuiGraphics g) {
        if (contextMenuNoteIdx < 0) return;
        int itemH = 14, menuW = 90;
        String[] items = {
            Component.translatable("notely.context.open").getString(),
            Component.translatable("notely.context.rename").getString(),
            Component.translatable("notely.context.delete").getString()
        };
        g.fill(contextMenuX - 1, contextMenuY - 1, contextMenuX + menuW + 1, contextMenuY + items.length * itemH + 1, COL_BORDER);
        g.fill(contextMenuX, contextMenuY, contextMenuX + menuW, contextMenuY + items.length * itemH, COL_LIST);
        for (int i = 0; i < items.length; i++) {
            int iy = contextMenuY + i * itemH;
            if (i == items.length - 1) {
                g.fill(contextMenuX, iy, contextMenuX + menuW, iy + itemH, 0xFFEED8D8);
                g.drawString(font, items[i], contextMenuX + 4, iy + 3, 0xFFAA4444, false);
            } else {
                g.drawString(font, items[i], contextMenuX + 4, iy + 3, MarkdownRenderer.COL_TEXT, false);
            }
            if (i < items.length - 1)
                g.fill(contextMenuX, iy + itemH - 1, contextMenuX + menuW, iy + itemH, 0x33000000);
        }
    }

    private void drawColorPickerOverlay(GuiGraphics g) {
        if (!pickingColor || colorBtns.isEmpty()) return;
        Button first = colorBtns.get(0);
        int baseX = first.getX();
        int baseY = first.getY();
        int totalW = STICKER_COLORS.length * 17;
        g.fill(baseX - totalW + 15, baseY - 2, baseX + 15, baseY + 12, 0xDD333333);
        for (int i = 0; i < STICKER_COLORS.length; i++) {
            int x = baseX - i * 17;
            g.fill(x, baseY, x + 13, baseY + 11, STICKER_COLORS[i]);
        }
    }

    // =========================================================
    // Layout helpers
    // =========================================================

    private int visibleListRows()   { return (H - TORN * 2 - 22) / ROW_H; }
    private int visibleEditorLines(){ return (H - TORN * 2 - 36) / LINE_H; }
    private int editorMaxW()        { return W - LIST_W - 32; }

    private int countTextLines() {
        if (selected == null) return 0;
        int maxW = editorMaxW();
        int count = 0;
        for (String line : selected.content.split("\n", -1))
            count += Math.max(1, MarkdownRenderer.wrapLine(font, line, maxW).size());
        return count;
    }
}
