package com.example.notepad.gui;

import com.example.notepad.NotepadData;
import com.example.notepad.NotepadData.Note;
import com.example.notepad.NotepadData.Sticker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class PinnedNotesOverlay {

    private static final int HEADER = 14;
    private static final int PAD = 5;
    private static final int MIN_W = 80;
    private static final int MIN_H = 50;
    private static final int RESIZE_ZONE = 10;

    private static Sticker dragged = null;
    private static boolean resizing = false;
    private static float dragOX, dragOY;

    public static void render(Object gfxObj, int screenW, int screenH) {
        if (!(gfxObj instanceof GuiGraphics gfx)) return;
        Minecraft mc = Minecraft.getInstance();

        int mx = toScaled(mc.mouseHandler.xpos(), mc.getWindow().getScreenWidth(), screenW);
        int my = toScaled(mc.mouseHandler.ypos(), mc.getWindow().getScreenHeight(), screenH);

        List<Sticker> list = NotepadData.stickers;
        for (int i = list.size() - 1; i >= 0; i--) {
            drawSticker(gfx, mc, list.get(i), mx, my);
        }
    }

    private static void drawSticker(GuiGraphics gfx, Minecraft mc, Sticker s, int mx, int my) {
        Note note = NotepadData.findNote(s.noteId);
        int x = (int) s.x, y = (int) s.y;
        int w = (int) s.width, h = (int) s.height;

        gfx.fill(x + 3, y + 3, x + w + 3, y + h + 3, 0x44000000);
        gfx.fill(x, y, x + w, y + h, s.color);
        gfx.fill(x, y, x + w, y + HEADER, darken(s.color, 0.82f));

        // Кнопка закрытия в шапке
        int closeX = x + w - HEADER + 1;
        boolean closeHovered = mx >= closeX && mx < x + w && my >= y && my < y + HEADER;
        if (closeHovered) gfx.fill(closeX, y + 1, x + w - 1, y + HEADER - 1, 0xAAFF4444);
        gfx.drawString(mc.font, "x", closeX + 2, y + 3, closeHovered ? 0xFFFFFFFF : 0xFF555555, false);

        // Название заметки в шапке
        String title = note != null ? note.title : "?";
        title = mc.font.plainSubstrByWidth(title, w - HEADER - 6);
        gfx.drawString(mc.font, title, x + 4, y + 3, 0xFF333333, false);

        // Линейки на теле стикера
        for (int row = 0; row < (h - HEADER) / 10; row++) {
            int lineY = y + HEADER + PAD + row * 10 + 8;
            if (lineY < y + h - 4) gfx.fill(x + PAD, lineY, x + w - PAD, lineY + 1, 0x22000000);
        }

        // Текст заметки
        if (note != null && !note.content.isEmpty()) {
            int textY = y + HEADER + PAD;
            for (String line : note.content.split("\n")) {
                if (textY + 10 > y + h - 4) {
                    gfx.drawString(mc.font, "...", x + PAD, textY, 0x88000000, false);
                    break;
                }
                String fit = mc.font.plainSubstrByWidth(line, w - PAD * 2);
                gfx.drawString(mc.font, fit, x + PAD, textY, 0xFF1A0A00, false);
                textY += 10;
            }
        }

        // Ручка ресайза
        int rx = x + w - 8, ry = y + h - 8;
        boolean resizeHovered = mx >= rx && mx < x + w && my >= ry && my < y + h;
        gfx.fill(rx, ry, x + w, y + h, resizeHovered ? 0x88000000 : 0x33000000);
        for (int k = 0; k < 3; k++) {
            gfx.fill(rx + k * 2 + 1, y + h - 2, rx + k * 2 + 2, y + h - 1, 0x88555555);
            gfx.fill(rx + k * 2 + 1, y + h - 4, rx + k * 2 + 2, y + h - 3, 0x88555555);
        }
    }

    public static boolean handlePress(double rawX, double rawY, Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int mx = toScaled(rawX, mc.getWindow().getScreenWidth(), sw);
        int my = toScaled(rawY, mc.getWindow().getScreenHeight(), sh);

        List<Sticker> list = NotepadData.stickers;
        for (int i = list.size() - 1; i >= 0; i--) {
            Sticker s = list.get(i);
            int x = (int) s.x, y = (int) s.y, w = (int) s.width, h = (int) s.height;
            if (mx < x || mx > x + w || my < y || my > y + h) continue;

            // Клик по крестику
            int closeX = x + w - HEADER + 1;
            if (mx >= closeX && my < y + HEADER) {
                NotepadData.removeSticker(s);
                return true;
            }

            // Начало ресайза
            if (mx >= x + w - RESIZE_ZONE && my >= y + h - RESIZE_ZONE) {
                dragged = s;
                resizing = true;
                dragOX = mx - (s.x + s.width);
                dragOY = my - (s.y + s.height);
                return true;
            }

            // Начало перетаскивания за шапку
            if (my < y + HEADER && mx < closeX) {
                dragged = s;
                resizing = false;
                dragOX = mx - s.x;
                dragOY = my - s.y;
                return true;
            }

            // Просто клик внутри - поднять наверх
            list.remove(i);
            list.add(s);
            return true;
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
            dragged.width = Math.max(MIN_W, mx - dragOX - dragged.x);
            dragged.height = Math.max(MIN_H, my - dragOY - dragged.y);
        } else {
            dragged.x = mx - dragOX;
            dragged.y = my - dragOY;
        }
    }

    public static void handleRelease() {
        if (dragged != null) {
            NotepadData.save();
            dragged = null;
            resizing = false;
        }
    }

    public static boolean isDragging() {
        return dragged != null;
    }

    private static int toScaled(double raw, int screenSize, int guiSize) {
        return (int) (raw * guiSize / screenSize);
    }

    private static int darken(int color, float factor) {
        int a = (color >> 24) & 0xFF;
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
