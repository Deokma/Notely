package by.deokma.notely.gui;

import by.deokma.notely.NotepadData;
import by.deokma.notely.NotepadData.Note;
import by.deokma.notely.NotepadData.Sticker;
import by.deokma.notely.util.MarkdownRenderer;
import by.deokma.notely.util.MarkdownRenderer.LineType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class PinnedNotesOverlay {

    private static final int HEADER      = 14;
    private static final int PAD         = 5;
    private static final int MIN_W       = 80;
    private static final int MIN_H       = 50;
    private static final int RESIZE_ZONE = 10;
    private static final int LINE_H      = 10;

    private static Sticker dragged  = null;
    private static boolean resizing = false;
    private static float dragOX, dragOY;

    // =========================================================
    // Render
    // =========================================================

    public static void render(Object gfxObj, int sw, int sh) {
        if (!(gfxObj instanceof GuiGraphics gfx)) return;
        Minecraft mc = Minecraft.getInstance();
        int mx = scaled(mc.mouseHandler.xpos(), mc.getWindow().getScreenWidth(), sw);
        int my = scaled(mc.mouseHandler.ypos(), mc.getWindow().getScreenHeight(), sh);
        List<Sticker> list = NotepadData.stickers;
        for (int i = list.size() - 1; i >= 0; i--) {
            drawSticker(gfx, mc, list.get(i), mx, my);
        }
    }

    private static void drawSticker(GuiGraphics gfx, Minecraft mc, Sticker s, int mx, int my) {
        Note note = NotepadData.findNote(s.noteId);
        int x = (int) s.x, y = (int) s.y, w = (int) s.width, h = (int) s.height;

        int alpha = s.transparent ? 0x88 : 0xFF;
        int bodyColor   = (alpha << 24) | (s.color & 0x00FFFFFF);
        int headerColor = (alpha << 24) | (darken(s.color, 0.82f) & 0x00FFFFFF);

        // Shadow + body
        gfx.fill(x + 3, y + 3, x + w + 3, y + h + 3, s.transparent ? 0x22000000 : 0x44000000);
        gfx.fill(x, y, x + w, y + h, bodyColor);
        gfx.fill(x, y, x + w, y + HEADER, headerColor);

        // Close button
        int cx = x + w - HEADER + 1;
        boolean closeHov = mx >= cx && mx < x + w && my >= y && my < y + HEADER;
        if (closeHov) gfx.fill(cx, y + 1, x + w - 1, y + HEADER - 1, 0xAAFF4444);
        gfx.drawString(mc.font, "x", cx + 2, y + 3, closeHov ? 0xFFFFFFFF : 0xFF555555, false);

        // Transparency toggle button
        int tx2 = cx - HEADER;
        boolean transHov = mx >= tx2 && mx < cx && my >= y && my < y + HEADER;
        int transColor = s.transparent ? 0xFF4488CC : 0xFF888888;
        if (transHov) gfx.fill(tx2, y + 1, cx - 1, y + HEADER - 1, 0x44FFFFFF);
        gfx.drawString(mc.font, s.transparent ? "o" : "O", tx2 + 2, y + 3, transColor, false);

        // Title
        String title = note != null ? mc.font.plainSubstrByWidth(note.title, w - HEADER * 2 - 6) : "?";
        gfx.drawString(mc.font, title, x + 4, y + 3, 0xFF333333, false);

        // Ruled lines
        for (int row = 0; row < (h - HEADER) / LINE_H; row++) {
            int ly = y + HEADER + PAD + row * LINE_H + LINE_H - 2;
            if (ly < y + h - 4) gfx.fill(x + PAD, ly, x + w - PAD, ly + 1, 0x22000000);
        }

        // Content
        if (note != null && !note.content.isEmpty()) {
            int ty = y + HEADER + PAD;
            for (String line : note.content.split("\n")) {
                if (ty + LINE_H > y + h - 4) {
                    gfx.drawString(mc.font, "...", x + PAD, ty, 0x88000000, false);
                    break;
                }
                drawStickerLine(gfx, mc, s, line, x, ty, w, mx, my, alpha);
                ty += LINE_H;
            }
        }

        // Resize handle
        int rx = x + w - 8, ry = y + h - 8;
        boolean resHov = mx >= rx && mx < x + w && my >= ry && my < y + h;
        gfx.fill(rx, ry, x + w, y + h, resHov ? 0x88000000 : 0x33000000);
        for (int k = 0; k < 3; k++) {
            gfx.fill(rx + k * 2 + 1, y + h - 2, rx + k * 2 + 2, y + h - 1, 0x88555555);
            gfx.fill(rx + k * 2 + 1, y + h - 4, rx + k * 2 + 2, y + h - 3, 0x88555555);
        }
    }

    private static void drawStickerLine(GuiGraphics gfx, Minecraft mc, Sticker s,
            String line, int x, int ty, int w, int mx, int my, int alpha) {
        int ink = (alpha << 24) | 0x001A0A00;
        LineType type = MarkdownRenderer.detectLineType(line);
        int maxW = w - PAD * 2;

        switch (type) {
            case H1 -> {
                String t = mc.font.plainSubstrByWidth(line.substring(2), maxW);
                gfx.drawString(mc.font, t, x + PAD, ty, (alpha << 24) | 0x003A2000, false);
                gfx.fill(x + PAD, ty + LINE_H - 1, x + PAD + mc.font.width(t), ty + LINE_H, (alpha << 24) | 0x003A2000);
            }
            case H2 -> gfx.drawString(mc.font, mc.font.plainSubstrByWidth(line.substring(3), maxW), x + PAD, ty, (alpha << 24) | 0x005A3800, false);
            case H3 -> gfx.drawString(mc.font, mc.font.plainSubstrByWidth(line.substring(4), maxW), x + PAD, ty, (alpha << 24) | 0x009B8A6A, false);
            case HR -> {
                int mid = ty + LINE_H / 2;
                gfx.fill(x + PAD, mid, x + w - PAD, mid + 1, (alpha << 24) | 0x008B7355);
            }
            case QUOTE -> {
                gfx.fill(x + PAD, ty, x + PAD + 2, ty + LINE_H - 1, (alpha << 24) | 0x008B7355);
                gfx.drawString(mc.font, mc.font.plainSubstrByWidth(line.substring(2), maxW - 4), x + PAD + 4, ty, (alpha << 24) | 0x007A6A50, false);
            }
            case CODE -> {
                String code = line.substring(1, line.length() - 1);
                String fit = mc.font.plainSubstrByWidth(code, maxW - 4);
                gfx.fill(x + PAD - 1, ty - 1, x + PAD + mc.font.width(fit) + 3, ty + LINE_H, 0x22000000);
                gfx.drawString(mc.font, fit, x + PAD + 1, ty, (alpha << 24) | 0x004A7A30, false);
            }
            case TODO_OPEN, TODO_DONE -> drawStickerTodo(gfx, mc, s, line, x, ty, w, mx, my, alpha, ink, type == LineType.TODO_DONE);
            default -> gfx.drawString(mc.font, mc.font.plainSubstrByWidth(line, maxW), x + PAD, ty, ink, false);
        }
    }

    private static void drawStickerTodo(GuiGraphics gfx, Minecraft mc, Sticker s,
            String line, int x, int ty, int w, int mx, int my, int alpha, int ink, boolean done) {
        gfx.fill(x + PAD, ty, x + PAD + 7, ty + 7, 0x44000000);
        if (done) {
            gfx.fill(x + PAD + 1, ty + 1, x + PAD + 6, ty + 6, (alpha << 24) | 0x004A8A3A);
            gfx.drawString(mc.font, "x", x + PAD, ty, (alpha << 24) | 0x00FFFFFF, false);
        } else {
            gfx.fill(x + PAD + 1, ty + 1, x + PAD + 6, ty + 6, darken(s.color, 1.05f));
        }
        boolean hov = mx >= x + PAD && mx < x + PAD + 9 && my >= ty && my < ty + 7;
        if (hov) gfx.fill(x + PAD - 1, ty - 1, x + PAD + 8, ty + 8, 0x44FFFFFF);
        String todoText = mc.font.plainSubstrByWidth(line.substring(4), w - PAD * 2 - 10);
        int todoColor = done ? (alpha << 24) | 0x00888877 : ink;
        gfx.drawString(mc.font, todoText, x + PAD + 10, ty, todoColor, false);
        if (done) gfx.fill(x + PAD + 10, ty + 4, x + PAD + 10 + mc.font.width(todoText), ty + 5, todoColor);
    }

    // =========================================================
    // Mouse handling
    // =========================================================

    public static boolean handlePress(double rawX, double rawY, Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int mx = scaled(rawX, mc.getWindow().getScreenWidth(), sw);
        int my = scaled(rawY, mc.getWindow().getScreenHeight(), sh);

        List<Sticker> list = NotepadData.stickers;
        for (int i = list.size() - 1; i >= 0; i--) {
            Sticker s = list.get(i);
            int x = (int) s.x, y = (int) s.y, w = (int) s.width, h = (int) s.height;
            if (mx < x || mx > x + w || my < y || my > y + h) continue;

            // Close button
            if (mx >= x + w - HEADER + 1 && my < y + HEADER) {
                NotepadData.removeSticker(s);
                return true;
            }

            // Transparency toggle
            int transBtn = x + w - HEADER * 2 + 1;
            if (mx >= transBtn && mx < x + w - HEADER + 1 && my < y + HEADER) {
                s.transparent = !s.transparent;
                NotepadData.save();
                return true;
            }

            if (tryToggleTodoInSticker(s, mx, my)) return true;

            // Resize handle
            if (mx >= x + w - RESIZE_ZONE && my >= y + h - RESIZE_ZONE) {
                dragged = s; resizing = true;
                dragOX = mx - (s.x + s.width);
                dragOY = my - (s.y + s.height);
                return true;
            }

            // Drag by header
            if (my < y + HEADER && mx < x + w - HEADER + 1) {
                dragged = s; resizing = false;
                dragOX = mx - s.x;
                dragOY = my - s.y;
                return true;
            }

            // Bring to front
            list.remove(i);
            list.add(s);
            return true;
        }
        return false;
    }

    private static boolean tryToggleTodoInSticker(Sticker s, int mx, int my) {
        Note note = NotepadData.findNote(s.noteId);
        if (note == null) return false;

        int x = (int) s.x, y = (int) s.y, h = (int) s.height;
        int ty = y + HEADER + PAD;
        int ci = 0;

        for (String line : note.content.split("\n")) {
            if (ty + LINE_H > y + h - 4) break;
            if (line.startsWith("[ ] ") || line.startsWith("[x] ")) {
                if (mx >= x + PAD && mx < x + PAD + 9 && my >= ty && my < ty + 8) {
                    if (ci + 4 <= note.content.length()) {
                        String before = note.content.substring(0, ci);
                        String after  = note.content.substring(ci + 4);
                        String newPrefix = note.content.startsWith("[ ] ", ci) ? "[x] " : "[ ] ";
                        note.content = before + newPrefix + after;
                    }
                    NotepadData.save();
                    return true;
                }
            }
            ci += line.length() + 1;
            ty += LINE_H;
        }
        return false;
    }

    public static void handleDrag(double rawX, double rawY, Minecraft mc) {
        if (dragged == null) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float mx = (float) (rawX * sw / mc.getWindow().getScreenWidth());
        float my = (float) (rawY * sh / mc.getWindow().getScreenHeight());
        if (resizing) {
            dragged.width  = Math.max(MIN_W, mx - dragOX - dragged.x);
            dragged.height = Math.max(MIN_H, my - dragOY - dragged.y);
        } else {
            dragged.x = mx - dragOX;
            dragged.y = my - dragOY;
        }
    }

    public static void handleRelease() {
        if (dragged != null) { NotepadData.save(); dragged = null; resizing = false; }
    }

    public static boolean isDragging() { return dragged != null; }

    // =========================================================
    // Utilities
    // =========================================================

    private static int scaled(double raw, int screenSize, int guiSize) {
        return (int) (raw * guiSize / screenSize);
    }

    private static int darken(int color, float f) {
        int a = (color >> 24) & 0xFF;
        int r = (int) Math.min(255, ((color >> 16) & 0xFF) * f);
        int g = (int) Math.min(255, ((color >>  8) & 0xFF) * f);
        int b = (int) Math.min(255, ( color        & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
