package com.example.notepad;

import com.example.notepad.gui.NotepadScreen;
import com.example.notepad.gui.PinnedNotesOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class NotepadModClient {

    public static KeyMapping openKey;

    public static void init() {
        NotepadData.load();
    }

    public static KeyMapping createKeyMapping() {
        openKey = new KeyMapping("key.notepad.open", GLFW.GLFW_KEY_N, "key.categories.notepad");
        return openKey;
    }

    public static void onKeyTick() {
        if (openKey != null && openKey.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(new NotepadScreen());
            }
        }
    }

    public static boolean onMousePress(double rawX, double rawY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NotepadScreen) return false;
        return PinnedNotesOverlay.handlePress(rawX, rawY, mc);
    }

    public static void onMouseHeld(double rawX, double rawY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NotepadScreen) return;
        PinnedNotesOverlay.handleDrag(rawX, rawY, mc);
    }

    public static void onMouseRelease() {
        PinnedNotesOverlay.handleRelease();
    }
}
