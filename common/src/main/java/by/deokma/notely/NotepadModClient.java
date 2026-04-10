package by.deokma.notely;

import by.deokma.notely.gui.NotepadScreen;
import by.deokma.notely.gui.PinnedNotesOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

public class NotepadModClient {

    public static KeyMapping openKey;

    public static void init() {
        NotepadData.load();
    }

    /** Called after the game window is created — safe to access GLFW. */
    public static void initWindow() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        GLFWScrollCallbackI existing = GLFW.glfwSetScrollCallback(window, null);
        GLFW.glfwSetScrollCallback(window, (win, dx, dy) -> {
            if (existing != null) existing.invoke(win, dx, dy);
            if (!(mc.screen instanceof NotepadScreen)) {
                onMouseScroll(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), dy);
            }
        });
    }

    /** Called when joining a singleplayer world. */
    public static void onJoinWorld(String worldFolderName) {
        NotepadData.loadForContext(worldFolderName);
    }

    /** Called when connecting to a multiplayer server. */
    public static void onJoinServer(String serverAddress) {
        NotepadData.loadForContext("server_" + serverAddress);
    }

    /** Called on disconnect from any world or server. */
    public static void onLeave() {
        NotepadData.clearContext();
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

    public static boolean onMouseScroll(double rawX, double rawY, double delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NotepadScreen) return false;
        return PinnedNotesOverlay.handleScroll(rawX, rawY, delta, mc);
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
