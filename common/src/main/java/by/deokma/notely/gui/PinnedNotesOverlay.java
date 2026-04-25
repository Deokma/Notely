package by.deokma.notely.gui;

import by.deokma.notely.NotelyData;
import by.deokma.notely.NotelyData.Note;
import by.deokma.notely.NotelyData.Sticker;
import by.deokma.notely.NotelyModClient;
import by.deokma.notely.util.MarkdownRenderer;
import by.deokma.notely.util.MarkdownRenderer.LineType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

public class PinnedNotesOverlay {

    private static final int HEADER = 14;
    private static final int PAD = 5;
    private static final int MIN_W = 80;
    private static final int MIN_H = 50;
    private static final int RESIZE_ZONE = 10;
    private static final int LINE_H = 10;

    private static Sticker dragged = null;
    private static boolean resizing = false;
    private static float dragOX, dragOY;

    // =========================================================
    // Render
    // =========================================================

    public static void render(Object gfxObj, int sw, int sh) {
        if (!(gfxObj instanceof GuiGraphics gfx)) return;
        if (!NotelyData.isInWorld()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int mx = scaled(mc.mouseHandler.xpos(), mc.getWindow().getScreenWidth(), sw);
        int my = scaled(mc.mouseHandler.ypos(), mc.getWindow().getScreenHeight(), sh);
        List<Sticker> list = NotelyData.stickers;
        for (int i = list.size() - 1; i >= 0; i--) {
            drawSticker(gfx, mc, list.get(i), mx, my);
        }
    }

    private static void drawSticker(GuiGraphics gfx, Minecraft mc, Sticker s, int mx, int my) {
        Note note = NotelyData.findNote(s.noteId);
        int x = (int) s.x, y = (int) s.y, w = (int) s.width, h = (int) s.height;

        int alpha = s.transparent ? 0x88 : 0xFF;
        int bodyColor = (alpha << 24) | (s.color & 0x00FFFFFF);
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
            String[] lines = note.content.split("\n");
            int lineH = Math.max(1, (int) (LINE_H * s.fontSize));
            int visibleLines = (h - HEADER - PAD * 2) / lineH;
            int maxScroll = Math.max(0, lines.length - visibleLines);
            s.scrollOffset = Math.max(0, Math.min(s.scrollOffset, maxScroll));

            gfx.enableScissor(x, y + HEADER, x + w, y + h - 4);

            if (s.fontSize != 1.0f) {
                // Scale text using Matrix3x2fStack (1.21.6+) or PoseStack (1.21.1)
                Object pose = gfx.pose();
                boolean pushed = tryPushMatrix(pose, x + PAD, y + HEADER + PAD, s.fontSize);
                // In scaled space: width and clip height must be divided by fontSize
                int scaledW = pushed ? (int)(w / s.fontSize) : w;
                int ty = pushed ? 0 : y + HEADER + PAD;
                int drawX = pushed ? -PAD : x; // drawStickerLine adds PAD internally
                int clipH = pushed ? (int)((h - HEADER - PAD * 2) / s.fontSize) : h;
                for (int li = s.scrollOffset; li < lines.length; li++) {
                    if (ty + LINE_H > (pushed ? clipH : y + h - 4)) break;
                    drawStickerLine(gfx, mc, s, lines[li], drawX, ty, scaledW, mx, my, alpha);
                    ty += LINE_H;
                }
                if (pushed) tryPopMatrix(pose);
            } else {
                int ty = y + HEADER + PAD;
                for (int li = s.scrollOffset; li < lines.length; li++) {
                    if (ty + lineH > y + h - 4) break;
                    drawStickerLine(gfx, mc, s, lines[li], x, ty, w, mx, my, alpha);
                    ty += lineH;
                }
            }

            gfx.disableScissor();
            // Scrollbar
            if (lines.length > visibleLines) {
                float scroll = maxScroll > 0 ? (float) s.scrollOffset / maxScroll : 0;
                int barH = h - HEADER - 8;
                int thumbH = Math.max(6, barH * visibleLines / lines.length);
                int thumbY = y + HEADER + 4 + (int) ((barH - thumbH) * scroll);
                gfx.fill(x + w - 4, y + HEADER + 4, x + w - 2, y + h - 4, 0x33000000);
                gfx.fill(x + w - 4, thumbY, x + w - 2, thumbY + thumbH, 0x88000000);
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
            case H2 ->
                    gfx.drawString(mc.font, mc.font.plainSubstrByWidth(line.substring(3), maxW), x + PAD, ty, (alpha << 24) | 0x005A3800, false);
            case H3 ->
                    gfx.drawString(mc.font, mc.font.plainSubstrByWidth(line.substring(4), maxW), x + PAD, ty, (alpha << 24) | 0x009B8A6A, false);
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
            case TODO_OPEN, TODO_DONE ->
                    drawStickerTodo(gfx, mc, s, line, x, ty, w, mx, my, alpha, ink, type == LineType.TODO_DONE);
            default -> gfx.drawString(mc.font, mc.font.plainSubstrByWidth(line, maxW), x + PAD, ty, ink, false);
        }
    }

    private static void drawStickerTodo(GuiGraphics gfx, Minecraft mc, Sticker s,
                                        String line, int x, int ty, int w, int mx, int my, int alpha, int ink, boolean done) {
        MarkdownRenderer.drawCheckbox(gfx, x + PAD, ty, done);
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
        if (!NotelyData.isInWorld()) return false;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int mx = scaled(rawX, mc.getWindow().getScreenWidth(), sw);
        int my = scaled(rawY, mc.getWindow().getScreenHeight(), sh);
        return handlePressAt(mx, my);
    }

    /** Use when coordinates are already GUI-scaled (e.g. from ScreenEvent). */
    public static boolean handlePressScaled(double scaledX, double scaledY) {
        if (!NotelyData.isInWorld()) return false;
        return handlePressAt((int) scaledX, (int) scaledY);
    }

    private static boolean handlePressAt(int mx, int my) {
        List<Sticker> list = NotelyData.stickers;
        for (int i = list.size() - 1; i >= 0; i--) {
            Sticker s = list.get(i);
            int x = (int) s.x, y = (int) s.y, w = (int) s.width, h = (int) s.height;
            if (mx < x || mx > x + w || my < y || my > y + h) continue;

            // Close button
            if (mx >= x + w - HEADER + 1 && my < y + HEADER) {
                NotelyData.removeSticker(s);
                return true;
            }

            // Transparency toggle
            int transBtn = x + w - HEADER * 2 + 1;
            if (mx >= transBtn && mx < x + w - HEADER + 1 && my < y + HEADER) {
                s.transparent = !s.transparent;
                NotelyData.save();
                return true;
            }

            if (tryToggleTodoInSticker(s, mx, my)) return true;

            // Resize handle
            if (mx >= x + w - RESIZE_ZONE && my >= y + h - RESIZE_ZONE) {
                dragged = s;
                resizing = true;
                dragOX = mx - (s.x + s.width);
                dragOY = my - (s.y + s.height);
                return true;
            }

            // Drag by header
            if (my < y + HEADER && mx < x + w - HEADER + 1) {
                dragged = s;
                resizing = false;
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
        Note note = NotelyData.findNote(s.noteId);
        if (note == null) return false;

        int x = (int) s.x, y = (int) s.y, h = (int) s.height;
        String[] lines = note.content.split("\n");
        int lineH = Math.max(1, (int) (LINE_H * s.fontSize));
        int ty = y + HEADER + PAD;
        int ci = 0;

        // Skip lines above scroll offset
        for (int li = 0; li < s.scrollOffset && li < lines.length; li++) {
            ci += lines[li].length() + 1;
        }

        for (int li = s.scrollOffset; li < lines.length; li++) {
            if (ty + lineH > y + h - 4) break;
            String line = lines[li];
            if (line.startsWith("[ ] ") || line.startsWith("[x] ")) {
                if (mx >= x + PAD && mx < x + PAD + 9 && my >= ty && my < ty + 8) {
                    if (ci + 4 <= note.content.length()) {
                        String before = note.content.substring(0, ci);
                        String after = note.content.substring(ci + 4);
                        String newPrefix = note.content.startsWith("[ ] ", ci) ? "[x] " : "[ ] ";
                        note.content = before + newPrefix + after;
                    }
                    NotelyData.save();
                    return true;
                }
            }
            ci += line.length() + 1;
            ty += lineH;
        }
        return false;
    }

    public static void handleDrag(double rawX, double rawY, Minecraft mc) {
        if (dragged == null) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float mx = (float) (rawX * sw / mc.getWindow().getScreenWidth());
        float my = (float) (rawY * sh / mc.getWindow().getScreenHeight());
        applyDrag(mx, my);
    }

    /** Use when coordinates are already GUI-scaled (e.g. from ScreenEvent). */
    public static void handleDragScaled(double scaledX, double scaledY) {
        if (dragged == null) return;
        applyDrag((float) scaledX, (float) scaledY);
    }

    private static void applyDrag(float mx, float my) {
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
            NotelyData.save();
            dragged = null;
            resizing = false;
        }
    }

    public static boolean handleScroll(double rawX, double rawY, double delta, Minecraft mc) {
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int mx = scaled(rawX, mc.getWindow().getScreenWidth(), sw);
        int my = scaled(rawY, mc.getWindow().getScreenHeight(), sh);
        return handleScrollAt(mx, my, delta, mc);
    }

    /** Use when coordinates are already GUI-scaled (e.g. from ScreenEvent). */
    public static boolean handleScrollScaled(double scaledX, double scaledY, double delta, Minecraft mc) {
        return handleScrollAt((int) scaledX, (int) scaledY, delta, mc);
    }

    private static boolean handleScrollAt(int mx, int my, double delta, Minecraft mc) {
        long win = NotelyModClient.getGlfwWindow(mc);
        boolean ctrl = win != 0 && (
            org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)  == org.lwjgl.glfw.GLFW.GLFW_PRESS
         || org.lwjgl.glfw.GLFW.glfwGetKey(win, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL) == org.lwjgl.glfw.GLFW.GLFW_PRESS);

        List<Sticker> list = NotelyData.stickers;
        for (int i = list.size() - 1; i >= 0; i--) {
            Sticker s = list.get(i);
            int x = (int) s.x, y = (int) s.y, w = (int) s.width, h = (int) s.height;
            if (mx < x || mx > x + w || my < y || my > y + h) continue;

            if (ctrl) {
                // Ctrl+scroll — change font size
                s.fontSize = Math.max(0.5f, Math.min(2.0f, s.fontSize + (float) delta * 0.1f));
                NotelyData.save();
            } else {
                // Normal scroll — scroll content
                if (my < y + HEADER) continue;
                Note note = NotelyData.findNote(s.noteId);
                if (note == null) continue;
                int lineH = (int) (LINE_H * s.fontSize);
                int lines = note.content.split("\n", -1).length;
                int visibleLines = (h - HEADER - PAD * 2) / Math.max(1, lineH);
                int maxScroll = Math.max(0, lines - visibleLines);
                s.scrollOffset = Math.max(0, Math.min(s.scrollOffset - (int) delta, maxScroll));
            }
            return true;
        }
        return false;
    }

    public static boolean isDragging() {
        return dragged != null;
    }

    // =========================================================
    // Matrix transform helpers (compatible with 1.21.1 PoseStack and 1.21.6+ Matrix3x2fStack)
    // =========================================================

    /** Push matrix, translate to (tx, ty), scale by s. Returns true if successful. */
    private static boolean tryPushMatrix(Object pose, float tx, float ty, float s) {
        // 1.21.6+ Matrix3x2fStack
        try {
            pose.getClass().getMethod("pushMatrix").invoke(pose);
            // translate(float, float)
            try {
                pose.getClass().getMethod("translate", float.class, float.class).invoke(pose, tx, ty);
            } catch (NoSuchMethodException e) {
                // translate(Vector2f) — create via reflection
                Class<?> vec2 = Class.forName("org.joml.Vector2f", true, pose.getClass().getClassLoader());
                Object v = vec2.getConstructor(float.class, float.class).newInstance(tx, ty);
                pose.getClass().getMethod("translate", vec2).invoke(pose, v);
            }
            // scale(float) or scale(float, float)
            try {
                pose.getClass().getMethod("scale", float.class, float.class).invoke(pose, s, s);
            } catch (NoSuchMethodException e) {
                pose.getClass().getMethod("scale", float.class).invoke(pose, s);
            }
            return true;
        } catch (Exception ignored) {}
        // 1.21.1 PoseStack
        try {
            pose.getClass().getMethod("pushPose").invoke(pose);
            pose.getClass().getMethod("translate", double.class, double.class, double.class)
                .invoke(pose, (double) tx, (double) ty, 0.0);
            Object last = pose.getClass().getMethod("last").invoke(pose);
            Object mat = last.getClass().getMethod("pose").invoke(last);
            mat.getClass().getMethod("scale", float.class, float.class, float.class).invoke(mat, s, s, 1f);
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    private static void tryPopMatrix(Object pose) {
        try {
            pose.getClass().getMethod("popMatrix").invoke(pose);
            return;
        } catch (Exception ignored) {}
        try {
            pose.getClass().getMethod("popPose").invoke(pose);
        } catch (Exception ignored) {}
    }

    // =========================================================
    // Utilities
    // =========================================================

    private static int scaled(double raw, int screenSize, int guiSize) {
        return (int) (raw * guiSize / screenSize);
    }

    private static int darken(int color, float f) {
        int a = (color >> 24) & 0xFF;
        int r = (int) Math.min(255, ((color >> 16) & 0xFF) * f);
        int g = (int) Math.min(255, ((color >> 8) & 0xFF) * f);
        int b = (int) Math.min(255, (color & 0xFF) * f);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
