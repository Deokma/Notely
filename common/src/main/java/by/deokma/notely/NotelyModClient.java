package by.deokma.notely;

import by.deokma.notely.gui.NotelyScreen;
import by.deokma.notely.gui.PinnedNotesOverlay;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWScrollCallbackI;

public class NotelyModClient {

    public static KeyMapping openKey;

    public static void init() {
        NotelyData.load();
    }

    /** Called after the game window is created — safe to access GLFW. */
    public static void initWindow() {
        Minecraft mc = Minecraft.getInstance();
        long window = getGlfwWindow(mc);
        if (window == 0) return;
        GLFWScrollCallbackI existing = GLFW.glfwSetScrollCallback(window, null);
        GLFW.glfwSetScrollCallback(window, (win, dx, dy) -> {
            if (existing != null) existing.invoke(win, dx, dy);
            if (!(mc.screen instanceof NotelyScreen)) {
                onMouseScroll(mc.mouseHandler.xpos(), mc.mouseHandler.ypos(), dy);
            }
        });
    }

    /** Get the native GLFW window handle — compatible with 1.21.1 and 1.21.11. */
    public static long getGlfwWindow(Minecraft mc) {
        // Try getWindow() first (1.21.1)
        try {
            var m = mc.getWindow().getClass().getMethod("getWindow");
            return (long) m.invoke(mc.getWindow());
        } catch (Exception ignored) {}
        // Try getHandle() (1.21.11+)
        try {
            var m = mc.getWindow().getClass().getMethod("getHandle");
            return (long) m.invoke(mc.getWindow());
        } catch (Exception ignored) {}
        // Fallback: field access
        for (var f : mc.getWindow().getClass().getDeclaredFields()) {
            if (f.getType() == long.class) {
                try { f.setAccessible(true); return (long) f.get(mc.getWindow()); }
                catch (Exception ignored) {}
            }
        }
        return 0;
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

    public static KeyMapping createKeyMapping() {
        // 1.21.11: KeyMapping(String, int, KeyMapping$Category)
        //          KeyMapping$Category.register(Identifier)
        try {
            ClassLoader cl = KeyMapping.class.getClassLoader();
            Class<?> catClass = Class.forName("net.minecraft.client.KeyMapping$Category", true, cl);
            // Create Identifier for the category id using KeyMapping's classloader
            Object identifier = null;
            for (String cls : new String[]{"net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation"}) {
                try {
                    Class<?> idClass = Class.forName(cls, true, cl);
                    for (String method : new String[]{"withNamespaceAndPath", "fromNamespaceAndPath"}) {
                        try {
                            identifier = idClass.getMethod(method, String.class, String.class)
                                .invoke(null, "notely", "categories/notely");
                            break;
                        } catch (NoSuchMethodException ignored) {}
                    }
                    if (identifier != null) break;
                } catch (ClassNotFoundException ignored) {}
            }
            if (identifier == null) throw new RuntimeException("Cannot create Identifier/ResourceLocation");
            Object category = catClass.getMethod("register", identifier.getClass()).invoke(null, identifier);
            openKey = KeyMapping.class.getConstructor(String.class, int.class, catClass)
                .newInstance("key.notely.open", GLFW.GLFW_KEY_N, category);
        } catch (Exception e) {
            throw new RuntimeException("Cannot create KeyMapping", e);
        }
        return openKey;
    }

    private static java.util.function.Supplier<NotelyScreen> screenFactory = NotelyScreen::new;

    public static void setScreenFactory(java.util.function.Supplier<NotelyScreen> factory) {
        screenFactory = factory;
    }

    public static void onKeyTick() {
        if (openKey != null && openKey.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen == null) {
                mc.setScreen(screenFactory.get());
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
