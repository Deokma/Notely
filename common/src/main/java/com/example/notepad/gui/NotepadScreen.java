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

    private static final int W = 420;
    private static final int H = 270;
    private static final int LIST_W = 130;
    private static final int TORN = 6;
    private static final int ROW_H = 22;
    private static final int LINE_H = 11;

    // Цвета интерфейса
    private static final int COL_BG         = 0xFFEEE0C4;
    private static final int COL_LIST       = 0xFFD8C9A8;
    private static final int COL_SEL        = 0xFFC4B08A;
    private static final int COL_HOVER      = 0xFFCFBF9A;
    private static final int COL_BORDER     = 0xFF8B7355;
    private static final int COL_TORN       = 0xFFB8A888;
    private static final int COL_RULED_LINE = 0x22886644;
    private static final int COL_MARGIN_RED = 0x88CC4433;
    private static final int COL_TEXT       = 0xFF1A0A00;
    private static final int COL_HINT       = 0xFF9B8A6A;
    private static final int COL_SHADOW     = 0x55000000;
    private static final int COL_CHECKED    = 0xFF4A8A3A;
    private static final int COL_TOOLBAR    = 0xFFCFBF9A;

    static final int[] STICKER_COLORS = {
        0xFFFFF176, 0xFFFFCC80, 0xFFA5D6A7,
        0xFF90CAF9, 0xFFCE93D8, 0xFFEF9A9A
    };

    // Рваные края (генерируются один раз)
    private final int[] tornTop, tornBot;

    // Текущая выбранная заметка
    private Note selected = null;

    // Позиция экрана
    private int ox, oy;

    // Скролл списка и текста
    private int listOffset = 0;
    private int textOffset = 0;

    // Курсор в тексте
    private int cursor = 0;
    private int cursorTimer = 0;
    private boolean cursorVisible = true;

    // Режим редактирования заголовка
    private boolean renamingTitle = false;
    private String titleBuffer = "";
    private int titleCursor = 0;

    // Выбор цвета стикера
    private boolean pickingColor = false;

    // Виджеты
    private Button btnAdd, btnDelete, btnPin, btnClose, btnTodo, btnHr;
    private final List<Button> colorBtns = new ArrayList<>();

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

        int toolbarY = oy + TORN + 3;
        int editorX = ox + LIST_W + 4;

        btnAdd = addRenderableWidget(Button.builder(
            Component.literal("+ Заметка"),
            b -> newNote()
        ).pos(ox + 3, oy + H - TORN - 18).size(LIST_W - 6, 14).build());

        btnTodo = addRenderableWidget(Button.builder(
            Component.literal("[x] Todo"),
            b -> insertTodo()
        ).pos(editorX, toolbarY).size(54, 13).build());

        btnHr = addRenderableWidget(Button.builder(
            Component.literal("--- HR"),
            b -> insertSeparator()
        ).pos(editorX + 57, toolbarY).size(40, 13).build());

        btnDelete = addRenderableWidget(Button.builder(
            Component.literal("Del"),
            b -> deleteSelected()
        ).pos(ox + W - 58, toolbarY).size(24, 13).build());

        btnPin = addRenderableWidget(Button.builder(
            Component.literal("Pin"),
            b -> toggleColorPicker()
        ).pos(ox + W - 32, toolbarY).size(24, 13).build());

        btnClose = addRenderableWidget(Button.builder(
            Component.literal("X"),
            b -> onClose()
        ).pos(ox + W - 18, oy + 2).size(15, 13).build());

        for (int i = 0; i < STICKER_COLORS.length; i++) {
            final int ci = i;
            Button cb = addRenderableWidget(Button.builder(
                Component.literal(" "),
                b -> pinWithColor(STICKER_COLORS[ci])
            ).pos(ox + W - 18 - (STICKER_COLORS.length - i) * 17, oy + 17).size(15, 11).build());
            cb.visible = false;
            colorBtns.add(cb);
        }

        refreshWidgetState();
    }

    private void refreshWidgetState() {
        boolean hasNote = selected != null;
        btnDelete.active = hasNote;
        btnPin.active = hasNote;
        btnTodo.active = hasNote;
        btnHr.active = hasNote;
        colorBtns.forEach(b -> b.visible = pickingColor && hasNote);
    }

    // ---- Операции с заметками ----

    private void newNote() {
        Note note = NotepadData.createNote();
        selected = note;
        cursor = 0;
        textOffset = 0;
        renamingTitle = true;
        titleBuffer = note.title;
        titleCursor = titleBuffer.length();
        refreshWidgetState();
    }

    private void deleteSelected() {
        if (selected == null) return;
        NotepadData.deleteNote(selected.id);
        selected = NotepadData.notes.isEmpty() ? null : NotepadData.notes.get(0);
        cursor = selected != null ? selected.content.length() : 0;
        textOffset = 0;
        renamingTitle = false;
        refreshWidgetState();
    }

    private void openNote(Note note) {
        if (selected != null) NotepadData.save();
        selected = note;
        cursor = note.content.length();
        textOffset = 0;
        renamingTitle = false;
        pickingColor = false;
        refreshWidgetState();
    }

    // ---- Вставка блоков ----

    private void insertTodo() {
        if (selected == null) return;
        String text = selected.content;
        // Переносим на новую строку если находимся не в начале строки
        boolean needsNewline = cursor > 0 && text.charAt(cursor - 1) != '\n';
        insert((needsNewline ? "\n" : "") + "[ ] ");
    }

    private void insertSeparator() {
        if (selected == null) return;
        String text = selected.content;
        boolean needsNewline = cursor > 0 && text.charAt(cursor - 1) != '\n';
        // Используем дефисы вместо спецсимволов
        insert((needsNewline ? "\n" : "") + "--------------------\n");
    }

    private void insert(String s) {
        if (selected == null) return;
        if (selected.content.length() + s.length() > NotepadData.MAX_CONTENT_LENGTH) return;
        selected.content = selected.content.substring(0, cursor) + s + selected.content.substring(cursor);
        cursor += s.length();
    }

    // ---- Закрепление стикера ----

    private void toggleColorPicker() {
        pickingColor = !pickingColor;
        refreshWidgetState();
    }

    private void pinWithColor(int color) {
        if (selected == null) return;
        float px = width - 210f;
        float py = 40f + (NotepadData.stickers.size() * 25) % (height - 140);
        NotepadData.pinNote(selected.id, px, py, color);
        pickingColor = false;
        refreshWidgetState();
    }

    // ---- Закрытие ----

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float dt) {
        // Без блюра, игра видна через экран
    }

    @Override
    public void onClose() {
        if (renamingTitle && selected != null) commitRename();
        NotepadData.save();
        super.onClose();
    }

    // ---- Ввод с клавиатуры ----

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) { onClose(); return true; } // Escape

        if (renamingTitle) return handleRenameKey(key);
        if (selected != null) return handleEditorKey(key, scan, mods);
        return super.keyPressed(key, scan, mods);
    }

    private boolean handleRenameKey(int key) {
        switch (key) {
            case 257, 335 -> { commitRename(); return true; }  // Enter
            case 256       -> { cancelRename(); return true; } // Escape
            case 259 -> {                                       // Backspace
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

    private void commitRename() {
        if (selected != null) {
            selected.title = titleBuffer.isBlank() ? "Заметка" : titleBuffer.trim();
        }
        renamingTitle = false;
        NotepadData.save();
    }

    private void cancelRename() {
        renamingTitle = false;
    }

    private boolean handleEditorKey(int key, int scan, int mods) {
        String t = selected.content;
        switch (key) {
            case 259 -> { // Backspace
                if (cursor > 0) {
                    selected.content = t.substring(0, cursor - 1) + t.substring(cursor);
                    cursor--;
                }
                return true;
            }
            case 261 -> { // Delete
                if (cursor < t.length()) {
                    selected.content = t.substring(0, cursor) + t.substring(cursor + 1);
                }
                return true;
            }
            case 263 -> { cursor = Math.max(0, cursor - 1); return true; }
            case 262 -> { cursor = Math.min(t.length(), cursor + 1); return true; }
            case 265 -> { moveCursorVertically(-1); return true; }
            case 264 -> { moveCursorVertically(1); return true; }
            case 268 -> { cursor = lineStart(t, cursor); return true; } // Home
            case 269 -> { cursor = lineEnd(t, cursor); return true; }   // End
            case 257, 335 -> { typeChar('\n'); return true; }            // Enter
            case 258 -> { typeChar('\t'); return true; }                 // Tab
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
        selected.content = selected.content.substring(0, cursor) + c + selected.content.substring(cursor);
        cursor++;
    }

    private int lineStart(String t, int pos) {
        while (pos > 0 && t.charAt(pos - 1) != '\n') pos--;
        return pos;
    }

    private int lineEnd(String t, int pos) {
        while (pos < t.length() && t.charAt(pos) != '\n') pos++;
        return pos;
    }

    private void moveCursorVertically(int direction) {
        String t = selected.content;
        int col = cursor - lineStart(t, cursor);
        String[] lines = t.split("\n", -1);
        int charCount = 0, lineIndex = 0;
        for (int i = 0; i < lines.length; i++) {
            if (cursor <= charCount + lines[i].length()) { lineIndex = i; break; }
            charCount += lines[i].length() + 1;
        }
        int target = Mth.clamp(lineIndex + direction, 0, lines.length - 1);
        int targetCol = Math.min(col, lines[target].length());
        int newPos = 0;
        for (int i = 0; i < target; i++) newPos += lines[i].length() + 1;
        cursor = newPos + targetCol;
    }

    // ---- Клики мышью ----

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int ix = (int) mx, iy = (int) my;

        // Клик по заголовку заметки для переименования
        if (selected != null && !renamingTitle) {
            int titleX = ox + LIST_W + 4;
            int titleY = oy + TORN + 18;
            if (ix >= titleX && ix < ox + W - 60 && iy >= titleY && iy < titleY + 11) {
                renamingTitle = true;
                titleBuffer = selected.title;
                titleCursor = titleBuffer.length();
                return true;
            }
        }

        // Клик по чекбоксам todo
        if (selected != null && !renamingTitle && tryToggleTodo(ix, iy)) return true;

        // Клик по заметке в списке
        int visibleRows = visibleListRows();
        for (int i = 0; i < visibleRows; i++) {
            int noteIndex = listOffset + i;
            if (noteIndex >= NotepadData.notes.size()) break;
            int rowY = oy + TORN + 16 + i * ROW_H;
            if (ix >= ox + 2 && ix < ox + LIST_W - 2 && iy >= rowY && iy < rowY + ROW_H) {
                openNote(NotepadData.notes.get(noteIndex));
                return true;
            }
        }

        return super.mouseClicked(mx, my, btn);
    }

    private boolean tryToggleTodo(int mx, int my) {
        int ex = ox + LIST_W + 22;
        int ey = oy + TORN + 32;
        int maxW = W - LIST_W - 30;
        int clipBottom = oy + H - TORN - 20;

        String text = selected.content;
        String[] rawLines = text.split("\n", -1);
        int dy = ey - textOffset * LINE_H;
        int ci = 0;

        for (String line : rawLines) {
            List<String> wrapped = wrapToWidth(line, maxW);
            if (wrapped.isEmpty()) wrapped.add("");

            String first = wrapped.get(0);
            if (dy >= ey - 2 && dy <= clipBottom && (first.startsWith("[ ] ") || first.startsWith("[x] "))) {
                if (mx >= ex - 2 && mx < ex + 9 && my >= dy && my < dy + 10) {
                    if (first.startsWith("[ ] ")) {
                        selected.content = text.substring(0, ci) + "[x] " + text.substring(ci + 4);
                    } else {
                        selected.content = text.substring(0, ci) + "[ ] " + text.substring(ci + 4);
                    }
                    NotepadData.save();
                    return true;
                }
            }

            for (int wi = 0; wi < wrapped.size(); wi++) {
                ci += wrapped.get(wi).length();
                if (wi < wrapped.size() - 1) dy += LINE_H;
            }
            ci++;
            dy += LINE_H;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int ix = (int) mx;
        if (ix < ox + LIST_W) {
            int maxScroll = Math.max(0, NotepadData.notes.size() - visibleListRows());
            listOffset = Mth.clamp((int) (listOffset - dy), 0, maxScroll);
        } else {
            int maxScroll = Math.max(0, countTextLines() - visibleEditorLines());
            textOffset = Mth.clamp((int) (textOffset - dy), 0, maxScroll);
        }
        return true;
    }

    private int visibleListRows() {
        return (H - TORN * 2 - 22) / ROW_H;
    }

    private int visibleEditorLines() {
        return (H - TORN * 2 - 34) / LINE_H;
    }

    private int countTextLines() {
        if (selected == null) return 0;
        int maxW = W - LIST_W - 30;
        int total = 0;
        for (String line : selected.content.split("\n", -1)) {
            total += Math.max(1, wrapToWidth(line, maxW).size());
        }
        return total;
    }

    // ---- Тик ----

    @Override
    public void tick() {
        if (++cursorTimer >= 10) {
            cursorTimer = 0;
            cursorVisible = !cursorVisible;
        }
    }

    // ---- Рендер ----

    @Override
    public void render(GuiGraphics g, int mx, int my, float dt) {
        drawFrame(g);
        drawList(g, mx, my);
        drawEditor(g);
        super.render(g, mx, my, dt);
        drawColorPickerOverlay(g);
    }

    private void drawFrame(GuiGraphics g) {
        int x = ox, y = oy;

        g.fill(x + 4, y + 4, x + W + 4, y + H + 4, COL_SHADOW);
        g.fill(x, y, x + W, y + H, COL_BG);
        g.fill(x, y, x + LIST_W, y + H, COL_LIST);
        g.fill(x + LIST_W, y, x + LIST_W + 1, y + H, COL_BORDER);

        // Рваный верх
        for (int col = 0; col < W; col += 4) {
            int t = tornTop[Math.min(col / 4, tornTop.length - 1)];
            g.fill(x + col, y, x + col + 4, y + t, 0xFF1E1E1E);
            g.fill(x + col, y + t, x + col + 4, y + t + 2, COL_TORN);
        }

        // Рваный низ
        for (int col = 0; col < W; col += 4) {
            int t = tornBot[Math.min(col / 4, tornBot.length - 1)];
            int base = y + H - TORN;
            g.fill(x + col, base + t, x + col + 4, y + H, 0xFF1E1E1E);
            g.fill(x + col, base + t - 1, x + col + 4, base + t + 1, COL_TORN);
        }

        // Тулбар редактора
        g.fill(x + LIST_W + 1, y + TORN, x + W, y + TORN + 17, COL_TOOLBAR);
        g.fill(x + LIST_W + 1, y + TORN + 16, x + W, y + TORN + 17, COL_BORDER);

        // Дырки переплёта
        for (int i = 0; i < 3; i++) {
            int hy = y + 30 + i * 75;
            g.fill(x + LIST_W + 3, hy, x + LIST_W + 12, hy + 8, 0xFF111111);
            g.fill(x + LIST_W + 4, hy + 1, x + LIST_W + 11, hy + 7, 0xFF000000);
            g.fill(x + LIST_W + 4, hy + 1, x + LIST_W + 7, hy + 3, 0x22FFFFFF);
        }

        // Красная линия поля
        int lineX = ox + LIST_W + 21;
        g.fill(lineX, oy + TORN + 17, lineX + 1, oy + H - TORN - 20, COL_MARGIN_RED);

        // Горизонтальные линейки
        int ruledY = oy + TORN + 32 + LINE_H;
        while (ruledY < oy + H - TORN - 20) {
            g.fill(lineX - 1, ruledY, ox + W - 8, ruledY + 1, COL_RULED_LINE);
            ruledY += LINE_H;
        }
    }

    private void drawList(GuiGraphics g, int mx, int my) {
        g.drawString(font, "Заметки", ox + 4, oy + TORN + 4, COL_HINT, false);

        int clipX1 = ox + 1, clipX2 = ox + LIST_W - 1;
        int clipY1 = oy + TORN + 14, clipY2 = oy + H - TORN - 20;
        g.enableScissor(clipX1, clipY1, clipX2, clipY2);

        int visibleRows = visibleListRows();
        for (int i = 0; i < visibleRows + 1; i++) {
            int noteIndex = listOffset + i;
            if (noteIndex >= NotepadData.notes.size()) break;

            Note note = NotepadData.notes.get(noteIndex);
            int rowY = oy + TORN + 16 + i * ROW_H;

            boolean hovered = mx >= ox + 2 && mx < ox + LIST_W - 2 && my >= rowY && my < rowY + ROW_H;
            boolean isSelected = note == selected;

            if (isSelected) g.fill(ox + 2, rowY, ox + LIST_W - 2, rowY + ROW_H - 2, COL_SEL);
            else if (hovered) g.fill(ox + 2, rowY, ox + LIST_W - 2, rowY + ROW_H - 2, COL_HOVER);

            boolean pinned = NotepadData.stickers.stream().anyMatch(s -> s.noteId.equals(note.id));
            int maxTitleWidth = LIST_W - (pinned ? 22 : 8);
            String title = font.plainSubstrByWidth(note.title, maxTitleWidth);
            g.drawString(font, title, ox + 5, rowY + 6, isSelected ? COL_TEXT : COL_HINT, false);

            if (pinned) g.drawString(font, "*", ox + LIST_W - 10, rowY + 6, 0xFFFF8800, false);
        }

        g.disableScissor();

        // Скроллбар списка
        int total = NotepadData.notes.size();
        int visible = visibleListRows();
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
        int maxW = W - LIST_W - 30;
        int clipTop = contentY - 2;
        int clipBot = oy + H - TORN - 20;

        if (selected == null) {
            g.drawString(font, "Выберите заметку слева", ex, contentY + 20, COL_HINT, false);
            return;
        }

        // Заголовок / поле переименования
        if (renamingTitle) {
            g.fill(ex - 2, titleY - 1, ox + W - 22, titleY + 11, 0x33FFFFFF);
            g.drawString(font, titleBuffer, ex, titleY + 1, COL_TEXT, false);
            if (cursorVisible) {
                int cx = ex + font.width(titleBuffer.substring(0, titleCursor));
                g.fill(cx, titleY - 1, cx + 1, titleY + 10, COL_TEXT);
            }
            g.drawString(font, "Enter - сохранить", ox + LIST_W + 200, titleY + 1, COL_HINT, false);
        } else {
            String title = font.plainSubstrByWidth(selected.title, W - LIST_W - 70);
            g.drawString(font, title, ex, titleY + 1, COL_TEXT, false);
            g.drawString(font, "  [клик чтобы переименовать]", ex + font.width(title), titleY + 1, COL_HINT, false);
        }

        // Контент
        g.enableScissor(ox + LIST_W + 2, clipTop, ox + W - 2, clipBot);

        String text = selected.content;
        if (text.isEmpty()) {
            g.drawString(font, "Начните писать...", ex, contentY, COL_HINT, false);
        }

        String[] rawLines = text.split("\n", -1);
        int dy = contentY - textOffset * LINE_H;
        int ci = 0;

        outer:
        for (int li = 0; li < rawLines.length; li++) {
            List<String> wrapped = wrapToWidth(rawLines[li], maxW);
            if (wrapped.isEmpty()) wrapped.add("");

            for (int wi = 0; wi < wrapped.size(); wi++) {
                String seg = wrapped.get(wi);
                int segEnd = ci + seg.length();

                if (dy >= clipTop - LINE_H && dy <= clipBot) {
                    drawTextSegment(g, seg, ex, dy, maxW, wi == 0);
                }

                if (!renamingTitle && cursorVisible && cursor >= ci && cursor <= segEnd) {
                    String beforeCursor = seg.substring(0, cursor - ci);
                    int cx = ex + font.width(beforeCursor);
                    if (dy >= clipTop && dy <= clipBot) {
                        g.fill(cx, dy - 1, cx + 1, dy + font.lineHeight, COL_TEXT);
                    }
                }

                ci = segEnd;
                if (wi < wrapped.size() - 1) {
                    dy += LINE_H;
                    if (dy > clipBot + LINE_H) break outer;
                }
            }
            if (li < rawLines.length - 1) ci++;
            dy += LINE_H;
            if (dy > clipBot + LINE_H) break;
        }

        // Скроллбар контента
        int totalLines = countTextLines();
        int visLines = visibleEditorLines();
        if (totalLines > visLines) {
            float scroll = (float) textOffset / (totalLines - visLines);
            int barH = clipBot - clipTop;
            int thumbH = Math.max(10, barH * visLines / totalLines);
            int thumbY = clipTop + (int) ((barH - thumbH) * scroll);
            g.fill(ox + W - 7, clipTop, ox + W - 5, clipBot, 0x33000000);
            g.fill(ox + W - 7, thumbY, ox + W - 5, thumbY + thumbH, 0x88000000);
        }

        g.disableScissor();
    }

    private void drawTextSegment(GuiGraphics g, String seg, int ex, int dy, int maxW, boolean lineStart) {
        // Разделитель
        if (lineStart && seg.startsWith("----")) {
            g.fill(ex - 2, dy + 4, ex + maxW, dy + 5, 0x88886644);
            return;
        }

        // Todo: не отмечено
        if (lineStart && seg.startsWith("[ ] ")) {
            drawCheckbox(g, ex, dy, false);
            g.drawString(font, seg.substring(4), ex + 11, dy, COL_TEXT, false);
            return;
        }

        // Todo: отмечено
        if (lineStart && seg.startsWith("[x] ")) {
            drawCheckbox(g, ex, dy, true);
            String rest = seg.substring(4);
            g.drawString(font, rest, ex + 11, dy, 0xFF888877, false);
            // Зачёркивание
            g.fill(ex + 11, dy + 4, ex + 11 + font.width(rest), dy + 5, 0xFF888877);
            return;
        }

        g.drawString(font, seg, ex, dy, COL_TEXT, false);
    }

    private void drawCheckbox(GuiGraphics g, int ex, int dy, boolean checked) {
        g.fill(ex - 1, dy, ex + 8, dy + 8, 0x33000000);
        if (checked) {
            g.fill(ex, dy + 1, ex + 7, dy + 7, COL_CHECKED);
            g.drawString(font, "v", ex, dy, 0xFFFFFFFF, false);
        } else {
            g.fill(ex, dy + 1, ex + 7, dy + 7, 0xFFEEE0C4);
        }
    }

    private void drawColorPickerOverlay(GuiGraphics g) {
        if (!pickingColor) return;
        int y = oy + 17;
        g.drawString(font, "Цвет:", ox + W - 18 - STICKER_COLORS.length * 17 - 36, y + 1, COL_HINT, false);
        for (int i = 0; i < STICKER_COLORS.length; i++) {
            int bx = ox + W - 18 - (STICKER_COLORS.length - i) * 17 + 1;
            g.fill(bx, y, bx + 13, y + 10, STICKER_COLORS[i]);
        }
    }

    // ---- Утилиты ----

    private List<String> wrapToWidth(String line, int maxW) {
        List<String> result = new ArrayList<>();
        if (line.isEmpty()) { result.add(""); return result; }
        String remaining = line;
        while (!remaining.isEmpty()) {
            String fit = font.plainSubstrByWidth(remaining, maxW);
            if (fit.isEmpty()) fit = remaining.substring(0, 1);
            result.add(fit);
            remaining = remaining.substring(fit.length());
        }
        return result;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
