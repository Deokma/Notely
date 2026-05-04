package by.deokma.notely.gui;

import by.deokma.notely.NotelyData;
import by.deokma.notely.NotelyData.Note;
import by.deokma.notely.util.MarkdownRenderer;
import by.deokma.notely.util.MarkdownRenderer.LineType;
import by.deokma.notely.util.TextCursor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

// Compatibility imports — resolved at runtime based on MC version
// ResourceLocation (1.21.1) / Identifier (1.21.11) handled via reflection in TEX_NOTEPAD

public class NotelyScreen extends Screen {

    // ---- Layout constants ----
    // Fixed size matching the actual notepad artwork in the 512x512 texture.
    // The artwork occupies exactly 440x280 pixels in the top-left of the texture.
    private static final int W_FIXED    = 430;
    private static final int H_FIXED    = 270;
    private static final float LIST_W_PCT = 0.30f;

    private static final int TORN   = 6;
    private static final int ROW_H  = 22;
    private static final int LINE_H = 12;

    // Computed each init()
    private int W      = W_FIXED;
    private int H      = H_FIXED;
    private int LIST_W = (int)(W_FIXED * LIST_W_PCT);

    // ---- Color palette ----
    private static final int COL_LIST = 0xFFD8C9A8;
    private static final int COL_SEL = 0xFFC4B08A;
    private static final int COL_HOVER = 0xFFCFBF9A;
    private static final int COL_BORDER = 0xFF8B7355;
    private static final int COL_RULE = 0x22886644;
    private static final int COL_TOOLBAR = 0xFFCFBF9A;

    static final int[] STICKER_COLORS = {
            0xFFFFF176, 0xFFFFCC80, 0xFFA5D6A7,
            0xFF90CAF9, 0xFFCE93D8, 0xFFEF9A9A
    };

    // ---- Torn-paper edge ----
    private int[] tornTop = new int[0];
    private int[] tornBot = new int[0];

    // ---- State ----
    private Note selected = null;
    private int ox, oy;
    private int listOffset = 0;
    private int textOffset = 0;

    // ---- Cursor ----
    private int cursor = 0;
    private int selectionStart = -1; // -1 = no selection
    private int cursorTimer = 0;
    private boolean cursorVisible = true;

    // ---- Title editing ----
    private boolean renamingTitle = false;
    private String titleBuffer = "";
    private int titleCursor = 0;
    private int titleSelStart = -1; // -1 = no selection

    // ---- Mouse drag selection ----
    private boolean draggingContent = false;
    private boolean draggingTitle = false;

    // ---- Color picker ----
    private boolean pickingColor = false;

    // ---- Context menu ----
    private int contextMenuNoteIdx = -1;
    private int contextMenuX = 0, contextMenuY = 0;

    // ---- Undo ----
    private final ArrayDeque<String> undoStack = new ArrayDeque<>();
    private static final int MAX_UNDO = 50;

    // ---- Title undo ----
    private final ArrayDeque<String> titleUndoStack = new ArrayDeque<>();

    // ---- Rendered lines cache (rebuilt each frame) ----
    private record RenderedLine(int charStart, int charEnd, int screenY, LineType type, String raw) {
    }

    private final List<RenderedLine> renderedLines = new ArrayList<>();

    // ---- Widgets ----
    private Button btnAdd, btnPin, btnClose, btnHelp;
    private final List<Button> colorBtns = new ArrayList<>();

    // ---- Textures ----
    private static final Identifier TEX_NOTEPAD = Identifier.fromNamespaceAndPath("notely", "textures/gui/notepad.png");

    private static final int MOD_CTRL = 2;
    private static final int MOD_SHIFT = 1;

    // =========================================================
    // Constructor
    // =========================================================

    public NotelyScreen() {
        super(Component.empty());
        if (!NotelyData.notes.isEmpty()) {
            selected = NotelyData.notes.get(0);
            cursor = selected.content.length();
        }
    }

    // =========================================================
    // Init
    // =========================================================

    private void computeLayout() {
        // Fixed size matching the artwork — always 440x280 GUI pixels.
        // Content areas are clamped to screen, but texture is always drawn at full size.
        W = W_FIXED;
        H = H_FIXED;
        LIST_W = (int)(W * LIST_W_PCT);

        var rng = new java.util.Random(42);
        int cols = (W - LIST_W) / 4 + 2;
        tornTop = new int[cols];
        tornBot = new int[cols];
        for (int i = 0; i < cols; i++) {
            tornTop[i] = rng.nextInt(TORN);
            tornBot[i] = rng.nextInt(TORN);
        }
    }

    @Override
    protected void init() {
        computeLayout();
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        colorBtns.clear();

        int toolY = oy + TORN + 3;

        btnAdd = addRenderableWidget(Button.builder(
                Component.translatable("notely.button.new_note"), b -> newNote()
        ).pos(ox + 3, oy + H - TORN - 22).size(LIST_W - 10, 14).build());

        btnPin = addRenderableWidget(Button.builder(
                Component.translatable("notely.button.pin"), b -> toggleColorPicker()
        ).pos(ox + W - 34, toolY - 1).size(14, 13).build());

        btnHelp = addRenderableWidget(Button.builder(
                Component.translatable("notely.button.help"), b -> createHelpNote()
        ).pos(ox + LIST_W - 16, toolY).size(14, 13).build());

        btnClose = addRenderableWidget(Button.builder(
                Component.translatable("notely.button.close"), b -> onClose()
        ).pos(ox + W - 18, toolY - 1).size(15, 13).build());

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
        // Remove button focus so space doesn't trigger them while editing
        if (has && !renamingTitle) setFocused(null);
    }

    // =========================================================
    // Note management
    // =========================================================

    private void newNote() {
        Note n = NotelyData.createNote();
        n.title = Component.translatable("notely.button.new_note").getString();
        selected = n;
        cursor = 0;
        textOffset = 0;
        renamingTitle = true;
        titleBuffer = n.title;
        titleCursor = titleBuffer.length();
        refreshWidgets();
    }

    private void createHelpNote() {
        Note note = NotelyData.createNote();
        note.title = Component.translatable("notely.help.title").getString();
        note.content = Component.translatable("notely.help.content").getString();
        selected = note;
        cursor = note.content.length();
        textOffset = 0;
        renamingTitle = false;
        NotelyData.save();
        refreshWidgets();
    }
//
//    private void deleteSelected() {
//        if (selected == null) return;
//        NotepadData.deleteNote(selected.id);
//        selected = NotepadData.notes.isEmpty() ? null : NotepadData.notes.get(0);
//        cursor = selected != null ? selected.content.length() : 0;
//        textOffset = 0;
//        renamingTitle = false;
//        refreshWidgets();
//    }

    private void openNote(Note note) {
        if (selected != null) NotelyData.save();
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
        float py = 40f + (NotelyData.stickers.size() * 25) % (height - 140);
        NotelyData.pinNote(selected.id, px, py, color);
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
        NotelyData.save();
    }

    // =========================================================
    // Lifecycle
    // =========================================================

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {
    }

    @Override
    public void onClose() {
        if (renamingTitle && selected != null) commitRename();
        NotelyData.save();
        super.onClose();
    }

    @Override
    public void tick() {
        if (++cursorTimer >= 10) {
            cursorTimer = 0;
            cursorVisible = !cursorVisible;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =========================================================
    // Keyboard input
    // =========================================================

    // 1.21.1 compatible signature
    public boolean keyPressed(int key, int scan, int mods) {
        return handleKeyPressed(key, scan, mods);
    }

    protected boolean handleKeyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }

        if (renamingTitle) return handleTitleKey(key, mods);
        if (selected == null) {
            return false;
        }

        String t = selected.content;
        boolean ctrl = (mods & MOD_CTRL) != 0;
        boolean shift = (mods & MOD_SHIFT) != 0;

        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            undo();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_V) {
            pasteFromClipboard();
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_A) {
            selectionStart = 0;
            cursor = t.length();
            return true;
        }

        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSelection()) {
                    deleteSelection();
                    return true;
                }
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
                if (hasSelection()) {
                    deleteSelection();
                    return true;
                }
                pushUndo();
                if (ctrl) {
                    int p = TextCursor.wordBoundaryRight(t, cursor);
                    selected.content = t.substring(0, cursor) + t.substring(p);
                } else if (cursor < t.length()) {
                    selected.content = t.substring(0, cursor) + t.substring(cursor + 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (!shift && hasSelection()) {
                    cursor = selMin();
                    selectionStart = -1;
                } else {
                    if (shift && !hasSelection()) selectionStart = cursor;
                    cursor = ctrl ? TextCursor.wordBoundaryLeft(t, cursor) : Math.max(0, cursor - 1);
                    if (shift && cursor == selectionStart) selectionStart = -1;
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (!shift && hasSelection()) {
                    cursor = selMax();
                    selectionStart = -1;
                } else {
                    if (shift && !hasSelection()) selectionStart = cursor;
                    cursor = ctrl ? TextCursor.wordBoundaryRight(t, cursor) : Math.min(t.length(), cursor + 1);
                    if (shift && cursor == selectionStart) selectionStart = -1;
                }
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                if (shift && !hasSelection()) selectionStart = cursor;
                cursor = TextCursor.moveVertically(t, cursor, -1);
                if (!shift) selectionStart = -1;
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                if (shift && !hasSelection()) selectionStart = cursor;
                cursor = TextCursor.moveVertically(t, cursor, 1);
                if (!shift) selectionStart = -1;
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (shift && !hasSelection()) selectionStart = cursor;
                cursor = ctrl ? 0 : TextCursor.lineStart(t, cursor);
                if (!shift) selectionStart = -1;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (shift && !hasSelection()) selectionStart = cursor;
                cursor = ctrl ? t.length() : TextCursor.lineEnd(t, cursor);
                if (!shift) selectionStart = -1;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                typeChar('\n');
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                typeChar('\t');
                return true;
            }
        }
        return false;
    }

    private boolean handleTitleKey(int key, int mods) {
        boolean ctrl = (mods & MOD_CTRL) != 0;
        boolean shift = (mods & MOD_SHIFT) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                commitRename();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                commitRename();
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (titleHasSel()) {
                    pushTitleUndo();
                    titleDeleteSel();
                    return true;
                }
                if (ctrl) {
                    pushTitleUndo();
                    int p = TextCursor.wordBoundaryLeft(titleBuffer, titleCursor);
                    titleBuffer = titleBuffer.substring(0, p) + titleBuffer.substring(titleCursor);
                    titleCursor = p;
                } else if (titleCursor > 0) {
                    pushTitleUndo();
                    titleBuffer = titleBuffer.substring(0, titleCursor - 1) + titleBuffer.substring(titleCursor);
                    titleCursor--;
                }
                return true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (titleHasSel()) {
                    pushTitleUndo();
                    titleDeleteSel();
                    return true;
                }
                if (titleCursor < titleBuffer.length()) {
                    pushTitleUndo();
                    titleBuffer = titleBuffer.substring(0, titleCursor) + titleBuffer.substring(titleCursor + 1);
                }
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                if (!shift && titleHasSel()) {
                    titleCursor = titleSelMin();
                    titleSelStart = -1;
                } else {
                    if (shift && !titleHasSel()) titleSelStart = titleCursor;
                    titleCursor = ctrl ? TextCursor.wordBoundaryLeft(titleBuffer, titleCursor) : Math.max(0, titleCursor - 1);
                    if (shift && titleCursor == titleSelStart) titleSelStart = -1;
                }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (!shift && titleHasSel()) {
                    titleCursor = titleSelMax();
                    titleSelStart = -1;
                } else {
                    if (shift && !titleHasSel()) titleSelStart = titleCursor;
                    titleCursor = ctrl ? TextCursor.wordBoundaryRight(titleBuffer, titleCursor) : Math.min(titleBuffer.length(), titleCursor + 1);
                    if (shift && titleCursor == titleSelStart) titleSelStart = -1;
                }
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                if (shift && !titleHasSel()) titleSelStart = titleCursor;
                titleCursor = 0;
                if (!shift) titleSelStart = -1;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                if (shift && !titleHasSel()) titleSelStart = titleCursor;
                titleCursor = titleBuffer.length();
                if (!shift) titleSelStart = -1;
                return true;
            }
            case GLFW.GLFW_KEY_A -> {
                if (ctrl) {
                    titleSelStart = 0;
                    titleCursor = titleBuffer.length();
                }
                return true;
            }
            case GLFW.GLFW_KEY_V -> {
                if (ctrl) pasteTitleFromClipboard();
                return true;
            }
            case GLFW.GLFW_KEY_Z -> {
                if (ctrl) undoTitle();
                return true;
            }
        }
        return true;
    }

    private void pasteTitleFromClipboard() {
        if (titleHasSel()) {
            pushTitleUndo();
            titleDeleteSel();
        } else pushTitleUndo();
        String clip = minecraft.keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        clip = clip.replaceAll("[\\r\\n]", " ").replaceAll("[^\\x20-\\x7E]", "");
        int available = 40 - titleBuffer.length();
        if (available <= 0) return;
        if (clip.length() > available) clip = clip.substring(0, available);
        titleBuffer = titleBuffer.substring(0, titleCursor) + clip + titleBuffer.substring(titleCursor);
        titleCursor += clip.length();
    }

    public boolean charTyped(char c, int mods) {
        if (renamingTitle) {
            if (c >= 32 && c != 127) {
                pushTitleUndo();
                if (titleHasSel()) titleDeleteSel();
                if (titleBuffer.length() < 40) {
                    titleBuffer = titleBuffer.substring(0, titleCursor) + c + titleBuffer.substring(titleCursor);
                    titleCursor++;
                }
            }
            return true;
        }
        if (selected != null && c >= 32 && c != 127) {
            typeChar(c);
            return true;
        }
        return false;
    }

    private void typeChar(char c) {
        if (hasSelection()) deleteSelection();
        if (selected.content.length() >= NotelyData.MAX_CONTENT_LENGTH) return;
        pushUndo();
        selected.content = selected.content.substring(0, cursor) + c + selected.content.substring(cursor);
        cursor++;
    }

    private void pasteFromClipboard() {
        if (selected == null) return;
        if (hasSelection()) deleteSelection();
        String clip = minecraft.keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        clip = clip.chars()
                .filter(c -> c == '\n' || c == '\t' || (c >= 32 && c != 127))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        if (clip.isEmpty()) return;
        String t = selected.content;
        int available = NotelyData.MAX_CONTENT_LENGTH - t.length();
        if (available <= 0) return;
        if (clip.length() > available) clip = clip.substring(0, available);
        pushUndo();
        selected.content = t.substring(0, cursor) + clip + t.substring(cursor);
        cursor += clip.length();
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
            try {
                cursor = Math.min(Integer.parseInt(snapshot.substring(sep + 1)), selected.content.length());
            } catch (NumberFormatException ignored) {
                cursor = selected.content.length();
            }
        }
    }

    private void pushTitleUndo() {
        if (titleUndoStack.size() >= MAX_UNDO) titleUndoStack.pollFirst();
        titleUndoStack.addLast(titleBuffer + "\u0000" + titleCursor);
    }

    private void undoTitle() {
        if (titleUndoStack.isEmpty()) return;
        String snapshot = titleUndoStack.pollLast();
        int sep = snapshot.lastIndexOf('\u0000');
        if (sep >= 0) {
            titleBuffer = snapshot.substring(0, sep);
            try {
                titleCursor = Math.min(Integer.parseInt(snapshot.substring(sep + 1)), titleBuffer.length());
            } catch (NumberFormatException ignored) {
                titleCursor = titleBuffer.length();
            }
            titleSelStart = -1;
        }
    }

    // =========================================================
    // Selection helpers — content editor
    // =========================================================

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionStart != cursor;
    }

    private int selMin() {
        return Math.min(selectionStart, cursor);
    }

    private int selMax() {
        return Math.max(selectionStart, cursor);
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        pushUndo();
        int lo = selMin(), hi = selMax();
        selected.content = selected.content.substring(0, lo) + selected.content.substring(hi);
        cursor = lo;
        selectionStart = -1;
    }

    // =========================================================
    // Selection helpers — title editor
    // =========================================================

    private boolean titleHasSel() {
        return titleSelStart >= 0 && titleSelStart != titleCursor;
    }

    private int titleSelMin() {
        return Math.min(titleSelStart, titleCursor);
    }

    private int titleSelMax() {
        return Math.max(titleSelStart, titleCursor);
    }

    private void titleDeleteSel() {
        if (!titleHasSel()) return;
        int lo = titleSelMin(), hi = titleSelMax();
        titleBuffer = titleBuffer.substring(0, lo) + titleBuffer.substring(hi);
        titleCursor = lo;
        titleSelStart = -1;
    }

    // =========================================================
    // Mouse input
    // =========================================================

    // 1.21.1 compatible signature
    public boolean mouseClicked(double mx, double my, int btn) {
        return handleMouseClicked(mx, my, btn);
    }

    protected boolean handleMouseClicked(double mx, double my, int btn) {
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
                if (idx >= NotelyData.notes.size()) break;
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
                if (idx >= NotelyData.notes.size()) break;
                int ry = oy + TORN + 16 + i * ROW_H;
                if (iy >= ry && iy < ry + ROW_H) {
                    openNote(NotelyData.notes.get(idx));
                    return true;
                }
            }
        }

        // Clicks in editor area
        if (ix > ox + LIST_W && selected != null) {
            // Clicking content area while renaming title — commit and place cursor
            if (renamingTitle) {
                commitRename();
                // fall through to handle content click below
            }

            int titleY = oy + TORN + 18;
            if (!renamingTitle && iy >= titleY && iy < titleY + 11 && ix < ox + W - 60) {
                renamingTitle = true;
                titleBuffer = selected.title;
                titleCursor = titleBuffer.length();
                titleSelStart = -1;
                titleUndoStack.clear();
                return true;
            }
            if (renamingTitle && iy >= oy + TORN + 18 && iy < oy + TORN + 29 && ix < ox + W - 60) {
                titleSelStart = -1;
                titleCursor = titleClickToCursor(ix);
                draggingTitle = true;
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
                        selectionStart = -1;
                        cursor = clickPosToCursor(ix, rl);
                        draggingContent = true;
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private void handleContextMenuClick(int ix, int iy) {
        int itemH = 14, menuW = 90;
        for (int i = 0; i < 3; i++) {
            int itemY = contextMenuY + i * itemH;
            if (ix >= contextMenuX && ix < contextMenuX + menuW && iy >= itemY && iy < itemY + itemH) {
                if (contextMenuNoteIdx < NotelyData.notes.size()) {
                    Note note = NotelyData.notes.get(contextMenuNoteIdx);
                    switch (i) {
                        case 0 -> openNote(note);
                        case 1 -> {
                            openNote(note);
                            renamingTitle = true;
                            titleBuffer = note.title;
                            titleCursor = titleBuffer.length();
                        }
                        case 2 -> NotelyData.deleteNote(note.id);
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
        NotelyData.save();
    }

    public boolean mouseReleased(double mx, double my, int btn) {
        return handleMouseReleased();
    }

    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        return handleMouseDragged(mx, my, dx, dy);
    }

    protected boolean handleMouseReleased() {
        draggingContent = false;
        draggingTitle = false;
        return false;
    }

    protected boolean handleMouseDragged(double mx, double my, double dx, double dy) {
        int ix = (int) mx, iy = (int) my;
        if (draggingContent && selected != null) {
            int newCursor = cursor;
            for (RenderedLine rl : renderedLines) {
                if (iy >= rl.screenY() && iy < rl.screenY() + LINE_H) {
                    newCursor = clickPosToCursor(ix, rl);
                    break;
                }
            }
            // Clamp to first/last line if dragging outside
            if (iy < oy + TORN + 32 && !renderedLines.isEmpty())
                newCursor = renderedLines.get(0).charStart();
            else if (iy >= oy + H - TORN - 20 && !renderedLines.isEmpty())
                newCursor = renderedLines.get(renderedLines.size() - 1).charEnd();

            if (selectionStart < 0) selectionStart = cursor;
            cursor = newCursor;
            if (cursor == selectionStart) selectionStart = -1;
            return true;
        }
        if (draggingTitle && renamingTitle) {
            int newCursor = titleClickToCursor(ix);
            if (titleSelStart < 0) titleSelStart = titleCursor;
            titleCursor = newCursor;
            if (titleCursor == titleSelStart) titleSelStart = -1;
            return true;
        }
        return false;
    }

    private int titleClickToCursor(int mx) {
        int ex = ox + LIST_W + 24;
        int relX = mx - ex;
        if (relX <= 0) return 0;
        for (int i = 0; i <= titleBuffer.length(); i++) {
            int w = font.width(titleBuffer.substring(0, i));
            if (w >= relX) {
                int wPrev = i > 0 ? font.width(titleBuffer.substring(0, i - 1)) : 0;
                return (relX - wPrev < w - relX) ? i - 1 : i;
            }
        }
        return titleBuffer.length();
    }

    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if ((int) mx < ox + LIST_W) {
            listOffset = Mth.clamp((int) (listOffset - dy), 0, Math.max(0, NotelyData.notes.size() - visibleListRows()));
        } else {
            textOffset = Mth.clamp((int) (textOffset - dy), 0, Math.max(0, countTextLines() - visibleEditorLines()));
        }
        return true;
    }

    // =========================================================
    // Render
    // =========================================================

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        drawFrame(g);
        drawList(g, mx, my);
        drawEditor(g);
        super.extractRenderState(g, mx, my, dt);
        drawColorPickerOverlay(g);
        drawContextMenu(g);
    }

    private void drawFrame(GuiGraphicsExtractor g) {
        int x = ox, y = oy;
        // Draw background texture: always render full W_FIXED x H_FIXED artwork from 512x512 texture.
        // If W or H was clamped (small screen), scale the UV region proportionally.
        g.blit(RenderPipelines.GUI_TEXTURED, TEX_NOTEPAD, x, y, 0, 0, W, H, 512, 492);
        // Toolbar strip
        g.fill(x + LIST_W + 1, y + TORN, x + W, y + TORN + 17, COL_TOOLBAR);

        // Ruled lines
        int mx2 = ox + LIST_W + 22;
        int contentY = oy + TORN + 32;
        int ruledY = contentY + LINE_H - 1;
        while (ruledY < oy + H - TORN - 20) {
            g.fill(mx2 - 1, ruledY, ox + W - 8, ruledY + 1, COL_RULE);
            ruledY += LINE_H;
        }
    }

    private void drawList(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, Component.translatable("notely.list.header").getString(), ox + 4, oy + TORN + 4, MarkdownRenderer.COL_HINT, false);

        int clipY1 = oy + TORN + 14, clipY2 = oy + H - TORN - 20;
        g.enableScissor(ox, clipY1, ox + LIST_W - 1, clipY2);


        int visible = visibleListRows();
        for (int i = 0; i <= visible; i++) {
            int idx = listOffset + i;
            if (idx >= NotelyData.notes.size()) break;
            Note note = NotelyData.notes.get(idx);
            int ry = oy + TORN + 16 + i * ROW_H;
            boolean hov = mx >= ox + 2 && mx < ox + LIST_W - 2 && my >= ry && my < ry + ROW_H;
            boolean isSel = note == selected;
            if (isSel) g.fill(ox + 2, ry, ox + LIST_W - 4, ry + ROW_H - 2, COL_SEL);
            else if (hov) g.fill(ox + 2, ry, ox + LIST_W - 4, ry + ROW_H - 2, COL_HOVER);
            boolean pinned = NotelyData.stickers.stream().anyMatch(s -> s.noteId.equals(note.id));
            int maxTW = LIST_W - (pinned ? 22 : 8);
            g.text(font, font.plainSubstrByWidth(note.title, maxTW), ox + 5, ry + 6,
                    isSel ? MarkdownRenderer.COL_TEXT : MarkdownRenderer.COL_HINT, false);
            if (pinned)
                g.text(font, Component.translatable("notely.list.pinned_marker").getString(), ox + LIST_W - 10, ry + 6, 0xFFFF8800, false);
        }

        g.disableScissor();

        // Scrollbar
        int total = NotelyData.notes.size();
        if (total > visible) {
            float scroll = (float) listOffset / (total - visible);
            int barH = clipY2 - clipY1;
            int thumbH = Math.max(10, barH * visible / total);
            int thumbY = clipY1 + (int) ((barH - thumbH) * scroll);
            g.fill(ox + LIST_W - 5, clipY1, ox + LIST_W - 3, clipY2, 0x33000000);
            g.fill(ox + LIST_W - 5, thumbY, ox + LIST_W - 3, thumbY + thumbH, 0x88000000);
        }
    }

    private void drawEditor(GuiGraphicsExtractor g) {
        int ex = ox + LIST_W + 24; // +2px indent from red margin line
        int titleY = oy + TORN + 18;
        int contentY = titleY + 18;
        int clipTop = contentY, clipBot = oy + H - TORN - 10;

        if (selected == null) {
            g.text(font, Component.translatable("notely.editor.select_note").getString(), ex, contentY + 20, MarkdownRenderer.COL_HINT, false);
            return;
        }

        drawTitle(g, ex, titleY);

        g.enableScissor(ox + LIST_W + 2, clipTop, ox + W, clipBot);
        renderedLines.clear();

        if (selected.content.isEmpty()) {
            g.text(font, Component.translatable("notely.editor.start_writing").getString(), ex, contentY, MarkdownRenderer.COL_HINT, false);
        }

        drawContent(g, ex, contentY, clipTop, clipBot);
        drawContentScrollbar(g, clipTop, clipBot);

        g.disableScissor();
    }

    private void drawTitle(GuiGraphicsExtractor g, int ex, int titleY) {
        if (renamingTitle) {
            g.fill(ex - 2, titleY - 1, ox + W - 22, titleY + 11, 0x33FFFFFF);
            // Selection highlight
            if (titleHasSel()) {
                int x1 = ex + font.width(titleBuffer.substring(0, titleSelMin()));
                int x2 = ex + font.width(titleBuffer.substring(0, titleSelMax()));
                g.fill(x1, titleY, x2, titleY + 9, 0x664488FF);
            }
            g.text(font, titleBuffer, ex, titleY + 1, MarkdownRenderer.COL_TEXT, false);
            if (cursorVisible) {
                int cx = ex + font.width(titleBuffer.substring(0, titleCursor));
                g.fill(cx, titleY - 1, cx + 1, titleY + 10, MarkdownRenderer.COL_TEXT);
            }
            g.text(font, Component.translatable("notely.editor.rename_hint").getString(), ox + LIST_W + 210, titleY + 1, MarkdownRenderer.COL_HINT, false);
        } else {
            String title = font.plainSubstrByWidth(selected.title, W - LIST_W - 70);
            g.text(font, title, ex, titleY + 1, MarkdownRenderer.COL_TEXT, false);
            g.text(font, Component.translatable("notely.editor.click_to_rename").getString(), ex + font.width(title), titleY + 1, MarkdownRenderer.COL_HINT, false);
        }
    }

    private void drawContent(GuiGraphicsExtractor g, int ex, int contentY, int clipTop, int clipBot) {
        String text = selected.content;
        String[] rawLines = text.split("\n", -1);
        int dy = contentY - textOffset * LINE_H;
        int ci = 0;
        int maxW = editorMaxW();

        // Find which logical line index the cursor is on (for raw-text preview)
        int cursorLi = TextCursor.cursorLineIndex(text, cursor);

        outer:
        for (int li = 0; li < rawLines.length; li++) {
            String raw = rawLines[li];
            LineType type = MarkdownRenderer.detectLineType(raw);
            boolean cursorOnThisLine = (li == cursorLi) && !renamingTitle;

            // Obsidian mode: show raw text when cursor is on this line
            String display = cursorOnThisLine ? raw : MarkdownRenderer.getDisplayText(raw, type);
            LineType renderType = cursorOnThisLine ? LineType.NORMAL : type;
            int xOff = cursorOnThisLine ? 0 : MarkdownRenderer.getTextXOffset(type);

            List<String> wrapped = MarkdownRenderer.wrapLine(font, display, maxW - xOff);
            if (wrapped.isEmpty()) wrapped.add("");

            for (int wi = 0; wi < wrapped.size(); wi++) {
                String seg = wrapped.get(wi);
                int segCharStart = ci;
                int segCharEnd = ci + raw.length();

                if (dy >= clipTop - LINE_H && dy <= clipBot) {
                    renderedLines.add(new RenderedLine(segCharStart, segCharEnd, dy, type, raw));
                    if (cursorOnThisLine) {
                        g.fill(ex - 2, dy - 1, ex + maxW, dy + LINE_H, 0x18000000);
                    }
                    // Selection highlight
                    if (hasSelection()) {
                        int lo = selMin(), hi = selMax();
                        int lineStart = segCharStart;
                        int lineEnd = segCharEnd;
                        if (lo < lineEnd && hi > lineStart) {
                            int selLo = Math.max(lo, lineStart) - lineStart;
                            int selHi = Math.min(hi, lineEnd) - lineStart;
                            String rawSub = raw.substring(0, Math.min(raw.length(), lineEnd - lineStart));
                            int x1 = ex + xOff + font.width(rawSub.substring(0, Math.min(selLo, rawSub.length())));
                            int x2 = ex + xOff + font.width(rawSub.substring(0, Math.min(selHi, rawSub.length())));
                            g.fill(x1, dy, x2, dy + LINE_H - 1, 0x664488FF);
                        }
                    }
                    MarkdownRenderer.drawLine(g, font, seg, ex + xOff, dy, renderType, maxW);
                }

                // Draw cursor
                if (!renamingTitle && cursorVisible && cursor >= ci && cursor <= ci + raw.length()) {
                    int prefixOffset = cursorOnThisLine ? 0 : MarkdownRenderer.prefixLen(type);
                    int posInDisplay = Math.max(0, cursor - ci - prefixOffset);
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

    private void drawContentScrollbar(GuiGraphicsExtractor g, int clipTop, int clipBot) {
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

    private void drawContextMenu(GuiGraphicsExtractor g) {
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
                g.text(font, items[i], contextMenuX + 4, iy + 3, 0xFFAA4444, false);
            } else {
                g.text(font, items[i], contextMenuX + 4, iy + 3, MarkdownRenderer.COL_TEXT, false);
            }
            if (i < items.length - 1)
                g.fill(contextMenuX, iy + itemH - 1, contextMenuX + menuW, iy + itemH, 0x33000000);
        }
    }

    private void drawColorPickerOverlay(GuiGraphicsExtractor g) {
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

    private int visibleListRows() {
        return (H - TORN * 2 - 22) / ROW_H;
    }

    private int visibleEditorLines() {
        return (H - TORN * 2 - 36) / LINE_H;
    }

    private int editorMaxW() {
        return W - LIST_W - 32;
    }

    private int countTextLines() {
        if (selected == null) return 0;
        int maxW = editorMaxW();
        int count = 0;
        for (String line : selected.content.split("\n", -1))
            count += Math.max(1, MarkdownRenderer.wrapLine(font, line, maxW).size());
        return count;
    }
}
