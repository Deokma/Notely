package com.example.notepad.gui;

import com.example.notepad.NotepadData;
import com.example.notepad.NotepadData.Note;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class NotepadScreen extends Screen {

    private static final int W = 440;
    private static final int H = 280;
    private static final int LIST_W = 130;
    private static final int TORN = 6;
    private static final int ROW_H = 22;

    private static final int COL_BG         = 0xFFEEE0C4;
    private static final int COL_LIST       = 0xFFD8C9A8;
    private static final int COL_SEL        = 0xFFC4B08A;
    private static final int COL_HOVER      = 0xFFCFBF9A;
    private static final int COL_BORDER     = 0xFF8B7355;
    private static final int COL_TORN       = 0xFFB8A888;
    private static final int COL_RULE       = 0x22886644;
    private static final int COL_RED_MARGIN = 0x88CC4433;
    private static final int COL_TEXT       = 0xFF1A0A00;
    private static final int COL_HINT       = 0xFF9B8A6A;
    private static final int COL_SHADOW     = 0x55000000;
    private static final int COL_CHECKED    = 0xFF4A8A3A;
    private static final int COL_TOOLBAR    = 0xFFCFBF9A;
    private static final int COL_H1         = 0xFF3A2000;
    private static final int COL_H2         = 0xFF5A3800;
    private static final int COL_QUOTE      = 0xFF7A6A50;
    private static final int COL_CODE_BG    = 0x22000000;

    static final int[] STICKER_COLORS = {
        0xFFFFF176, 0xFFFFCC80, 0xFFA5D6A7,
        0xFF90CAF9, 0xFFCE93D8, 0xFFEF9A9A
    };

    private final int[] tornTop, tornBot;

    private Note selected = null;
    private int ox, oy;

    private int listOffset = 0;
    private int textOffset = 0;

    private int cursor = 0;
    private int cursorTimer = 0;
    private boolean cursorVisible = true;

    private boolean renamingTitle = false;
    private String titleBuffer = "";
    private int titleCursor = 0;

    private boolean pickingColor = false;

    // Контекстное меню (ПКМ по заметке в списке)
    private int contextMenuNoteIdx = -1;  // индекс заметки под курсором ПКМ
    private int contextMenuX = 0, contextMenuY = 0;


    private Button btnAdd, btnPin, btnClose, btnTodo, btnHelp;

    // Битовые маски для модификаторов (GLFW)
    private static final int MOD_CTRL  = 2;
    private static final int MOD_SHIFT = 1;
    private final List<Button> colorBtns = new ArrayList<>();

    // Кешированный список отрендеренных строк для кликов
    // Масштаб шрифта (1 = стандарт, меняется Ctrl+Scroll)
    private float fontScale = 1.0f;
    private int lineH() { return Math.round(12 * fontScale); }

    // Undo: хранит последние 50 состояний контента
    private final java.util.ArrayDeque<String> undoStack = new java.util.ArrayDeque<>();
    private static final int MAX_UNDO = 50;

    // Номер строки (по \n) на которой стоит курсор — для Obsidian-режима
    private int cursorLineIndex = 0;

    private final List<RenderedLine> renderedLines = new ArrayList<>();

    // Одна видимая строка: позиция в тексте, Y на экране, тип
    private record RenderedLine(int charStart, int charEnd, int screenY, LineType type, String raw) {}

    private enum LineType { NORMAL, H1, H2, H3, TODO_OPEN, TODO_DONE, HR, QUOTE, CODE, BOLD }

    public NotepadScreen() {
        super(Component.empty());
        java.util.Random rng = new java.util.Random(42);
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

    @Override
    protected void init() {
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        colorBtns.clear();

        int toolY = oy + TORN + 3;
        int edX = ox + LIST_W + 4;

        btnAdd = addRenderableWidget(Button.builder(
            Component.literal("+ Заметка"), b -> newNote()
        ).pos(ox + 3, oy + H - TORN - 18).size(LIST_W - 6, 14).build());

        btnTodo = addRenderableWidget(Button.builder(
            Component.literal("[ ] Todo"), b -> insertAtCursor("[ ] ")
        ).pos(edX, toolY).size(52, 13).build());

//        btnHr = addRenderableWidget(Button.builder(
//            Component.literal("--- HR"), b -> insertAtCursor("---\n")
//        ).pos(edX + 55, toolY).size(40, 13).build());

btnPin = addRenderableWidget(Button.builder(
            Component.literal("\uD83D\uDCCC"), b -> toggleColorPicker() // Pin
        ).pos(ox + W - 34, toolY).size(14, 13).build());

        btnHelp = addRenderableWidget(Button.builder(
            Component.literal("?"), b -> createHelpNote()
        ).pos(ox + LIST_W - 16, toolY).size(14, 13).build());

        btnClose = addRenderableWidget(Button.builder(
            Component.literal("X"), b -> onClose()
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
        btnTodo.active = has;
        //btnHr.active = has;
        colorBtns.forEach(b -> b.visible = pickingColor && has);
    }

    // ---- Заметки ----

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
        note.title = "Справка по синтаксису";
        note.content =
            "# Заголовок H1\n" +
            "## Заголовок H2\n" +
            "### Заголовок H3\n" +
            "---\n" +
            "> Цитата\n" +
            "`код в строке`\n" +
            "---\n" +
            "[ ] Задача не выполнена\n" +
            "[x] Задача выполнена\n" +
            "---\n" +
            "Обычный текст. Нажми Pin\n" +
            "чтобы закрепить как стикер.\n" +
            "ПКМ по заметке для меню.\n" +
            "Ctrl+Backspace — стереть слово.";
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

    // ---- Вставка ----

    private void insertAtCursor(String text) {
        if (selected == null) return;
        String t = selected.content;
        // Переносим на новую строку если не в начале строки
        boolean needNewline = cursor > 0 && t.charAt(cursor - 1) != '\n';
        String insert = (needNewline ? "\n" : "") + text;
        if (t.length() + insert.length() > NotepadData.MAX_CONTENT_LENGTH) return;
        selected.content = t.substring(0, cursor) + insert + t.substring(cursor);
        cursor += insert.length();
    }

    // ---- Стикер ----

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

    // ---- Переименование ----

    private void commitRename() {
        if (selected != null)
            selected.title = titleBuffer.isBlank() ? "Заметка" : titleBuffer.trim();
        renamingTitle = false;
        NotepadData.save();
    }

    // ---- Закрытие ----

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float dt) {}

    @Override
    public void onClose() {
        if (renamingTitle && selected != null) commitRename();
        NotepadData.save();
        super.onClose();
    }

    // ---- Клавиатура ----

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { onClose(); return true; }

        // Пробел пишется в текст, а не нажимает focused кнопку
        if (key == 32 && !renamingTitle && selected != null) {
            typeChar(' ');
            return true;
        }

        if (renamingTitle) {
            switch (key) {
                case 257, 335 -> { commitRename(); return true; }
                case 259 -> {
                    if (titleCursor > 0) {
                        titleBuffer = titleBuffer.substring(0, titleCursor - 1) + titleBuffer.substring(titleCursor);
                        titleCursor--;
                    }
                    return true;
                }
                case 263 -> { titleCursor = Math.max(0, titleCursor - 1); return true; }
                case 262 -> { titleCursor = Math.min(titleBuffer.length(), titleCursor + 1); return true; }
            }
            return true;
        }

        if (selected == null) return super.keyPressed(key, scan, mods);

        String t = selected.content;
        boolean ctrl  = (mods & MOD_CTRL)  != 0;
        boolean shift = (mods & MOD_SHIFT) != 0;

        // Ctrl+Z — undo
        if (ctrl && key == 90) { undo(); return true; }

        switch (key) {
            case 259 -> { // Backspace
                pushUndo();
                if (ctrl) {
                    // Ctrl+Backspace — стереть слово влево
                    int newPos = wordBoundaryLeft(t, cursor);
                    selected.content = t.substring(0, newPos) + t.substring(cursor);
                    cursor = newPos;
                } else if (cursor > 0) {
                    selected.content = t.substring(0, cursor - 1) + t.substring(cursor);
                    cursor--;
                }
                return true;
            }
            case 261 -> { // Delete
                pushUndo();
                if (ctrl) {
                    // Ctrl+Delete — стереть слово вправо
                    int newEnd = wordBoundaryRight(t, cursor);
                    selected.content = t.substring(0, cursor) + t.substring(newEnd);
                } else if (cursor < t.length()) {
                    selected.content = t.substring(0, cursor) + t.substring(cursor + 1);
                }
                return true;
            }
            case 263 -> { cursor = ctrl ? wordBoundaryLeft(t, cursor) : Math.max(0, cursor - 1); updateCursorLine(); return true; }
            case 262 -> { cursor = ctrl ? wordBoundaryRight(t, cursor) : Math.min(t.length(), cursor + 1); updateCursorLine(); return true; }
            case 265 -> { moveCursorVertically(-1); updateCursorLine(); return true; }
            case 264 -> { moveCursorVertically(1); updateCursorLine(); return true; }
            case 268 -> { cursor = ctrl ? 0 : lineStart(t, cursor); updateCursorLine(); return true; }
            case 269 -> { cursor = ctrl ? t.length() : lineEnd(t, cursor); updateCursorLine(); return true; }
            case 257, 335 -> { typeChar('\n'); return true; }
            case 258 -> { typeChar('\t'); return true; }
        }
        return super.keyPressed(key, scan, mods);
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
        if (selected != null && c >= 32 && c != 127) {
            typeChar(c);
            return true;
        }
        return false;
    }

    private void typeChar(char c) {
        if (selected.content.length() >= NotepadData.MAX_CONTENT_LENGTH) return;
        pushUndo();
        selected.content = selected.content.substring(0, cursor) + c + selected.content.substring(cursor);
        cursor++;
        updateCursorLine();
    }

    // Вычисляем на какой строке (по \n) находится курсор
    private void updateCursorLine() {
        if (selected == null) { cursorLineIndex = 0; return; }
        String t = selected.content;
        int line = 0;
        for (int i = 0; i < cursor && i < t.length(); i++) {
            if (t.charAt(i) == '\n') line++;
        }
        cursorLineIndex = line;
    }

    private void pushUndo() {
        if (selected == null) return;
        if (undoStack.size() >= MAX_UNDO) undoStack.pollFirst();
        undoStack.addLast(selected.content + "\u0000" + cursor); // сохраняем контент + позицию курсора
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

    // Граница слова влево (Ctrl+Backspace / Ctrl+Left)
    private int wordBoundaryLeft(String t, int pos) {
        if (pos <= 0) return 0;
        int p = pos - 1;
        // Пропускаем пробелы/переносы
        while (p > 0 && !Character.isLetterOrDigit(t.charAt(p))) p--;
        // Пропускаем само слово
        while (p > 0 && Character.isLetterOrDigit(t.charAt(p - 1))) p--;
        return p;
    }

    // Граница слова вправо (Ctrl+Delete / Ctrl+Right)
    private int wordBoundaryRight(String t, int pos) {
        if (pos >= t.length()) return t.length();
        int p = pos;
        // Пропускаем текущее слово
        while (p < t.length() && Character.isLetterOrDigit(t.charAt(p))) p++;
        // Пропускаем пробелы
        while (p < t.length() && !Character.isLetterOrDigit(t.charAt(p))) p++;
        return p;
    }

    private int lineStart(String t, int pos) {
        while (pos > 0 && t.charAt(pos - 1) != '\n') pos--;
        return pos;
    }

    private int lineEnd(String t, int pos) {
        while (pos < t.length() && t.charAt(pos) != '\n') pos++;
        return pos;
    }

    private void moveCursorVertically(int dir) {
        String t = selected.content;
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
        cursor = newPos + Math.min(col, lines[target].length());
    }

    // ---- Клики ----

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int ix = (int) mx, iy = (int) my;

        // Закрываем контекстное меню при любом клике
        if (contextMenuNoteIdx >= 0) {
            handleContextMenuClick(ix, iy);
            contextMenuNoteIdx = -1;
            return true;
        }

        // ПКМ по заметке в списке — открываем контекстное меню
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

        // ЛКМ по заметке в списке (строго левая панель)
        if (btn == 0 && ix >= ox + 2 && ix < ox + LIST_W - 2) {
            int visible = visibleListRows();
            for (int i = 0; i < visible; i++) {
                int idx = listOffset + i;
                if (idx >= NotepadData.notes.size()) break;
                int ry = oy + TORN + 16 + i * ROW_H;
                if (iy >= ry && iy < ry + ROW_H) {
                    openNote(NotepadData.notes.get(idx));
                    return true;
                }
            }
        }

        // Клики в редакторе (строго правее LIST_W)
        if (ix > ox + LIST_W && selected != null && !renamingTitle) {
            // Заголовок — переименование
            int titleY = oy + TORN + 18;
            if (iy >= titleY && iy < titleY + 11 && ix < ox + W - 60) {
                renamingTitle = true;
                titleBuffer = selected.title;
                titleCursor = titleBuffer.length();
                return true;
            }

            // Клик по строке текста (только в зоне контента)
            int contentY = oy + TORN + 32;
            int clipBot = oy + H - TORN - 20;
            if (iy >= contentY && iy < clipBot) {
                for (RenderedLine rl : renderedLines) {
                    if (iy >= rl.screenY() && iy < rl.screenY() + lineH()) {
                        // Чекбокс todo
                        int checkX = ox + LIST_W + 22;
                        if (ix >= checkX - 2 && ix < checkX + 9
                                && (rl.type() == LineType.TODO_OPEN || rl.type() == LineType.TODO_DONE)) {
                            toggleTodo(rl.charStart());
                            return true;
                        }
                        // Позиция курсора
                        cursor = clickPosToCursor(ix, iy, rl);
                        updateCursorLine();
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    private void handleContextMenuClick(int ix, int iy) {
        // Пункты меню: 0=открыть, 1=переименовать, 2=удалить
        int menuX = contextMenuX, menuY = contextMenuY;
        int itemH = 14, menuW = 90;
        String[] items = { "Открыть", "Переименовать", "Удалить" };
        for (int i = 0; i < items.length; i++) {
            int itemY = menuY + i * itemH;
            if (ix >= menuX && ix < menuX + menuW && iy >= itemY && iy < itemY + itemH) {
                if (contextMenuNoteIdx < NotepadData.notes.size()) {
                    Note note = NotepadData.notes.get(contextMenuNoteIdx);
                    switch (i) {
                        case 0 -> openNote(note);
                        case 1 -> {
                            openNote(note);
                            renamingTitle = true;
                            titleBuffer = note.title;
                            titleCursor = titleBuffer.length();
                        }
                        case 2 -> NotepadData.deleteNote(note.id);
                    }
                }
                break;
            }
        }
    }

    // Определяем позицию символа по клику мышью внутри строки
    private int clickPosToCursor(int mx, int screenY, RenderedLine rl) {
        int textX = ox + LIST_W + 22;
        // Для todo смещаем начало текста
        String raw = rl.raw();
        String display = getDisplayText(raw, rl.type());
        int xOffset = getTextXOffset(rl.type());

        int relX = mx - (textX + xOffset);
        if (relX <= 0) return rl.charStart() + prefixLen(rl.type());

        // Идём по символам и ищем ближайший
        for (int i = 0; i <= display.length(); i++) {
            int w = font.width(display.substring(0, i));
            if (w >= relX) {
                // Уточняем — лево или право от символа ближе
                int wPrev = i > 0 ? font.width(display.substring(0, i - 1)) : 0;
                int chosen = (relX - wPrev < w - relX) ? i - 1 : i;
                return rl.charStart() + prefixLen(rl.type()) + Math.max(0, chosen);
            }
        }
        return rl.charEnd();
    }

    private void toggleTodo(int lineCharStart) {
        String t = selected.content;
        if (lineCharStart + 4 > t.length()) return;
        String prefix = t.substring(lineCharStart, lineCharStart + 4);
        if (prefix.equals("[ ] ")) {
            selected.content = t.substring(0, lineCharStart) + "[x] " + t.substring(lineCharStart + 4);
        } else if (prefix.equals("[x] ")) {
            selected.content = t.substring(0, lineCharStart) + "[ ] " + t.substring(lineCharStart + 4);
        }
        NotepadData.save();
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        boolean ctrl = hasControlDown();
        if (ctrl) {
            // Ctrl+Scroll — масштаб шрифта
            fontScale = Mth.clamp(fontScale + (float) dy * 0.1f, 0.5f, 2.0f);
            return true;
        }
        if ((int) mx < ox + LIST_W) {
            listOffset = Mth.clamp((int) (listOffset - dy), 0,
                Math.max(0, NotepadData.notes.size() - visibleListRows()));
        } else {
            textOffset = Mth.clamp((int) (textOffset - dy), 0,
                Math.max(0, countTextLines() - visibleEditorLines()));
        }
        return true;
    }

    private int visibleListRows() { return (H - TORN * 2 - 22) / ROW_H; }
    private int visibleEditorLines() { return (H - TORN * 2 - 36) / lineH(); }

    private int countTextLines() {
        if (selected == null) return 0;
        int maxW = editorMaxW();
        int count = 0;
        for (String line : selected.content.split("\n", -1))
            count += Math.max(1, wrapLine(line, maxW).size());
        return count;
    }

    private int editorMaxW() { return W - LIST_W - 32; }

    // ---- Tick ----

    @Override
    public void tick() {
        if (++cursorTimer >= 10) { cursorTimer = 0; cursorVisible = !cursorVisible; }
    }

    // ---- Render ----

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

        for (int col = 0; col < W; col += 4) {
            int t = tornTop[Math.min(col / 4, tornTop.length - 1)];
            g.fill(x + col, y, x + col + 4, y + t, 0xFF1E1E1E);
            g.fill(x + col, y + t, x + col + 4, y + t + 2, COL_TORN);
        }
        for (int col = 0; col < W; col += 4) {
            int t = tornBot[Math.min(col / 4, tornBot.length - 1)];
            int base = y + H - TORN;
            g.fill(x + col, base + t, x + col + 4, y + H, 0xFF1E1E1E);
            g.fill(x + col, base + t - 1, x + col + 4, base + t + 1, COL_TORN);
        }

        g.fill(x + LIST_W + 1, y + TORN, x + W, y + TORN + 17, COL_TOOLBAR);
        g.fill(x + LIST_W + 1, y + TORN + 16, x + W, y + TORN + 17, COL_BORDER);

        for (int i = 0; i < 3; i++) {
            int hy = y + 30 + i * 75;
            g.fill(x + LIST_W + 3, hy, x + LIST_W + 12, hy + 8, 0xFF111111);
            g.fill(x + LIST_W + 4, hy + 1, x + LIST_W + 11, hy + 7, 0xFF000000);
            g.fill(x + LIST_W + 4, hy + 1, x + LIST_W + 7, hy + 3, 0x22FFFFFF);
        }

        int mx2 = ox + LIST_W + 22;
        g.fill(mx2, oy + TORN + 17, mx2 + 1, oy + H - TORN - 20, COL_RED_MARGIN);

        int ruledY = oy + TORN + 34 + lineH();
        while (ruledY < oy + H - TORN - 20) {
            g.fill(mx2 - 1, ruledY, ox + W - 8, ruledY + 1, COL_RULE);
            ruledY += lineH();
        }
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        g.drawString(font, "Заметки", ox + 4, oy + TORN + 4, COL_HINT, false);

        int clipY1 = oy + TORN + 14, clipY2 = oy + H - TORN - 20;
        g.enableScissor(ox + 1, clipY1, ox + LIST_W - 1, clipY2);

        int visible = visibleListRows();
        for (int i = 0; i < visible + 1; i++) {
            int idx = listOffset + i;
            if (idx >= NotepadData.notes.size()) break;
            Note note = NotepadData.notes.get(idx);
            int ry = oy + TORN + 16 + i * ROW_H;
            boolean hov = mx >= ox + 2 && mx < ox + LIST_W - 2 && my >= ry && my < ry + ROW_H;
            boolean isSel = note == selected;
            if (isSel) g.fill(ox + 2, ry, ox + LIST_W - 2, ry + ROW_H - 2, COL_SEL);
            else if (hov) g.fill(ox + 2, ry, ox + LIST_W - 2, ry + ROW_H - 2, COL_HOVER);
            boolean pinned = NotepadData.stickers.stream().anyMatch(s -> s.noteId.equals(note.id));
            int maxTW = LIST_W - (pinned ? 22 : 8);
            g.drawString(font, font.plainSubstrByWidth(note.title, maxTW), ox + 5, ry + 6,
                isSel ? COL_TEXT : COL_HINT, false);
            if (pinned) g.drawString(font, "*", ox + LIST_W - 10, ry + 6, 0xFFFF8800, false);
        }

        g.disableScissor();

        // Скроллбар списка
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
            g.drawString(font, "Выберите заметку слева", ex, contentY + 20, COL_HINT, false);
            return;
        }

        // Заголовок
        if (renamingTitle) {
            g.fill(ex - 2, titleY - 1, ox + W - 22, titleY + 11, 0x33FFFFFF);
            g.drawString(font, titleBuffer, ex, titleY + 1, COL_TEXT, false);
            if (cursorVisible) {
                int cx = ex + font.width(titleBuffer.substring(0, titleCursor));
                g.fill(cx, titleY - 1, cx + 1, titleY + 10, COL_TEXT);
            }
            g.drawString(font, "Enter — сохранить", ox + LIST_W + 210, titleY + 1, COL_HINT, false);
        } else {
            String title = font.plainSubstrByWidth(selected.title, W - LIST_W - 70);
            g.drawString(font, title, ex, titleY + 1, COL_TEXT, false);
            g.drawString(font, " [клик для переименования]", ex + font.width(title), titleY + 1, COL_HINT, false);
        }

        // Контент с масштабированием шрифта
        g.enableScissor(ox + LIST_W + 2, clipTop, ox + W - 2, clipBot);
        if (fontScale != 1.0f) {
            g.pose().pushPose();
            // Масштабируем относительно левого верхнего угла области текста
            g.pose().translate(ex, contentY, 0);
            g.pose().scale(fontScale, fontScale, 1.0f);
            g.pose().translate(-ex, -contentY, 0);
        }

        renderedLines.clear();
        if (selected.content.isEmpty()) {
            g.drawString(font, "Начните писать... (поддерживается MD)", ex, contentY, COL_HINT, false);
        }

        String text = selected.content;
        String[] rawLines = text.split("\n", -1);
        int dy = contentY - textOffset * lineH();
        int ci = 0; // позиция в оригинальной строке

        outer:
        for (int li = 0; li < rawLines.length; li++) {
            String raw = rawLines[li];
            LineType type = detectLineType(raw);
            String display = getDisplayText(raw, type);
            int xOff = getTextXOffset(type);
            int maxW = editorMaxW() - xOff;

            List<String> wrapped = wrapLine(display, maxW);
            if (wrapped.isEmpty()) wrapped.add("");

            for (int wi = 0; wi < wrapped.size(); wi++) {
                String seg = wrapped.get(wi);
                int segStart = ci + (wi == 0 ? prefixLen(type) : 0);
                // Для переноса — оригинальная позиция в тексте чуть сложнее,
                // упрощённо: курсор считаем по первому сегменту каждой строки
                int segCharStart = ci;
                int segCharEnd = ci + raw.length();

                if (dy >= clipTop - lineH() && dy <= clipBot) {
                    renderedLines.add(new RenderedLine(segCharStart, segCharEnd, dy, type, raw));
                    // Если курсор на этой строке — показываем сырой текст (Obsidian-режим)
                    boolean cursorOnThisLine = (li == cursorLineIndex) && !renamingTitle;
                    if (cursorOnThisLine) {
                        // Рисуем подсветку строки
                        g.fill(ex - 4, dy - 1, ex + editorMaxW(), dy + lineH(), 0x18000000);
                        g.drawString(font, raw, ex, dy, COL_HINT, false);
                    } else {
                        drawLine(g, seg, ex + xOff, dy, type, ci, segCharStart + prefixLen(type));
                    }
                }

                // Курсор
                if (!renamingTitle && cursorVisible && cursor >= ci && cursor <= ci + raw.length()) {
                    // Найти где именно в пикселях курсор
                    int posInDisplay = Math.max(0, cursor - ci - prefixLen(type));
                    if (posInDisplay >= 0 && posInDisplay <= display.length() && dy >= clipTop && dy <= clipBot) {
                        String beforeCursor = font.plainSubstrByWidth(display.substring(0, posInDisplay), maxW);
                        int cx = ex + xOff + font.width(beforeCursor);
                        g.fill(cx, dy - 1, cx + 1, dy + font.lineHeight, COL_TEXT);
                    }
                }

                if (wi < wrapped.size() - 1) {
                    dy += lineH();
                    if (dy > clipBot + lineH()) break outer;
                }
            }
            if (li < rawLines.length - 1) ci++;
            ci += raw.length();
            dy += lineH();
            if (dy > clipBot + lineH()) break;
        }

        // Скроллбар контента
        int total = countTextLines(), vis = visibleEditorLines();
        if (total > vis) {
            float scroll = (float) textOffset / (total - vis);
            int barH = clipBot - clipTop;
            int thumbH = Math.max(10, barH * vis / total);
            int thumbY = clipTop + (int) ((barH - thumbH) * scroll);
            g.fill(ox + W - 7, clipTop, ox + W - 5, clipBot, 0x33000000);
            g.fill(ox + W - 7, thumbY, ox + W - 5, thumbY + thumbH, 0x88000000);
        }

        if (fontScale != 1.0f) {
            g.pose().popPose();
        }
        g.disableScissor();
    }

    // Рендер одной строки в зависимости от её типа (MD)
    private void drawLine(GuiGraphics g, String seg, int x, int y, LineType type, int ci, int displayStart) {
        switch (type) {
            case H1 -> {
                // H1 — крупнее не можем, рисуем жирным цветом + подчёркивание
                g.drawString(font, seg, x, y, COL_H1, false);
                g.fill(x, y + font.lineHeight, x + font.width(seg), y + font.lineHeight + 1, COL_H1);
            }
            case H2 -> g.drawString(font, seg, x, y, COL_H2, false);
            case H3 -> g.drawString(font, seg, x, y, COL_HINT, false);
            case TODO_OPEN -> {
                drawCheckbox(g, x - 11, y, false);
                g.drawString(font, seg, x, y, COL_TEXT, false);
            }
            case TODO_DONE -> {
                drawCheckbox(g, x - 11, y, true);
                g.drawString(font, seg, x, y, 0xFF888877, false);
                g.fill(x, y + 4, x + font.width(seg), y + 5, 0xFF888877);
            }
            case HR -> {
                int mid = y + lineH() / 2;
                g.fill(x - 10, mid, x + editorMaxW(), mid + 1, 0xAA8B7355);
            }
            case QUOTE -> {
                g.fill(x - 8, y, x - 6, y + lineH() - 1, 0xFF8B7355);
                g.drawString(font, seg, x, y, COL_QUOTE, false);
            }
            case CODE -> {
                g.fill(x - 2, y - 1, x + font.width(seg) + 2, y + lineH(), COL_CODE_BG);
                g.drawString(font, seg, x, y, 0xFF4A7A30, false);
            }
            default -> g.drawString(font, seg, x, y, COL_TEXT, false);
        }
    }

    private void drawCheckbox(GuiGraphics g, int x, int y, boolean checked) {
        g.fill(x, y, x + 8, y + 8, 0x33000000);
        if (checked) {
            g.fill(x + 1, y + 1, x + 7, y + 7, COL_CHECKED);
            g.drawString(font, "v", x, y, 0xFFFFFFFF, false);
        } else {
            g.fill(x + 1, y + 1, x + 7, y + 7, 0xFFEEE0C4);
        }
    }

    private void drawContextMenu(GuiGraphics g) {
        if (contextMenuNoteIdx < 0) return;
        int menuX = contextMenuX, menuY = contextMenuY;
        int itemH = 14, menuW = 90;
        String[] items = { "Открыть", "Переименовать", "Удалить" };
        // Фон меню
        g.fill(menuX - 1, menuY - 1, menuX + menuW + 1, menuY + items.length * itemH + 1, COL_BORDER);
        g.fill(menuX, menuY, menuX + menuW, menuY + items.length * itemH, COL_LIST);
        for (int i = 0; i < items.length; i++) {
            int iy = menuY + i * itemH;
            if (i == items.length - 1) {
                // Удалить — красноватый
                g.fill(menuX, iy, menuX + menuW, iy + itemH, 0xFFEED8D8);
                g.drawString(font, items[i], menuX + 4, iy + 3, 0xFFAA4444, false);
            } else {
                g.drawString(font, items[i], menuX + 4, iy + 3, COL_TEXT, false);
            }
            if (i < items.length - 1)
                g.fill(menuX, iy + itemH - 1, menuX + menuW, iy + itemH, 0x33000000);
        }
    }

    private void drawColorPickerOverlay(GuiGraphics g) {
        if (!pickingColor || colorBtns.isEmpty()) return;

        Button first = colorBtns.get(0);

        int baseX = first.getX();
        int baseY = first.getY();

        int width = STICKER_COLORS.length * 17;
        int height = 12;

        // Фон (смещаем ВЛЕВО)
        g.fill(baseX - width + 15, baseY - 2, baseX + 15, baseY + height, 0xDD333333);

        // Цвета рисуем ВЛЕВО
        for (int i = 0; i < STICKER_COLORS.length; i++) {
            int x = baseX - i * 17;
            g.fill(x, baseY, x + 13, baseY + 11, STICKER_COLORS[i]);
        }
    }

    // ---- MD парсинг ----

    private LineType detectLineType(String line) {
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

    // Текст без MD-префикса для отображения
    private String getDisplayText(String line, LineType type) {
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

    // Длина MD-префикса в исходной строке
    private int prefixLen(LineType type) {
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

    // Дополнительный отступ X для типа строки
    private int getTextXOffset(LineType type) {
        return switch (type) {
            case TODO_OPEN, TODO_DONE -> 11;
            case QUOTE -> 8;
            case CODE -> 4;
            default -> 0;
        };
    }

    // ---- Утилиты ----

    private List<String> wrapLine(String line, int maxW) {
        List<String> result = new ArrayList<>();
        if (line.isEmpty()) { result.add(""); return result; }
        String rem = line;
        while (!rem.isEmpty()) {
            String fit = font.plainSubstrByWidth(rem, maxW);
            if (fit.isEmpty()) fit = rem.substring(0, 1);
            result.add(fit);
            rem = rem.substring(fit.length());
        }
        return result;
    }

    @Override public boolean isPauseScreen() { return false; }
}
