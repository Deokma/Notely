package by.deokma.notely;

import by.deokma.notely.gui.NotelyScreen;
import by.deokma.notely.gui.PinnedNotesOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

public class NotelyModClient {

    public static KeyMapping openKey;

    public static void init() {
        // Data is loaded per-world via onJoinWorld/onJoinServer
    }

    /** Called after the game window is created — safe to access GLFW. */
    public static void initWindow() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        GLFWScrollCallbackI existing = GLFW.glfwSetScrollCallback(window, null);
        GLFW.glfwSetScrollCallback(window, (win, dx, dy) -> {
            if (existing != null) existing.invoke(win, dx, dy);
            if (!(mc.screen instanceof NotelyScreen)) {
                onMouseScroll(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), dy);
            }
        });
    }

    /** Called when joining a singleplayer world. */
    public static void onJoinWorld(String worldFolderName) {
        NotelyData.loadForContext(worldFolderName);
    }

    /** Called when connecting to a multiplayer server. */
    public static void onJoinServer(String serverAddress) {
        NotelyData.loadForContext("server_" + serverAddress);
    }

    /** Called on disconnect from any world or server. */
    public static void onLeave() {
        NotelyData.clearContext();
    }

    /**
     * Returns true if stickers should be visible and interactive on the current screen.
     * Stickers show in-game (no screen) and over chat, but not over inventory/pause menu/etc.
     */
    public static boolean isStickersAllowedOnScreen(Minecraft mc) {
        return mc.screen == null || mc.screen instanceof ChatScreen;
    }

    public static KeyMapping createKeyMapping() {
        openKey = new KeyMapping("key.notely.open", GLFW.GLFW_KEY_N, "key.categories.notely");
        return openKey;
    }

    public static void onKeyTick() {
        if (openKey != null && openKey.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null && NotelyData.isInWorld()) {
                mc.setScreen(new NotelyScreen());
            }
        }
    }

    public static boolean onMousePress(double rawX, double rawY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NotelyScreen) return false;
        return PinnedNotesOverlay.handlePress(rawX, rawY, mc);
    }

    public static boolean onMouseScroll(double rawX, double rawY, double delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NotelyScreen) return false;
        return PinnedNotesOverlay.handleScroll(rawX, rawY, delta, mc);
    }

    public static void onMouseHeld(double rawX, double rawY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NotelyScreen) return;
        PinnedNotesOverlay.handleDrag(rawX, rawY, mc);
    }

    public static void onMouseRelease() {
        PinnedNotesOverlay.handleRelease();
    }
}
