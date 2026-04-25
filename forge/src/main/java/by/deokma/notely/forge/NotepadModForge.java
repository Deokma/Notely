package by.deokma.notely.forge;

import by.deokma.notely.NotelyMod;
import by.deokma.notely.NotelyModClient;
import by.deokma.notely.gui.NotelyScreen;
import by.deokma.notely.gui.PinnedNotesOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

@Mod(NotelyMod.MOD_ID)
public class NotepadModForge {

    private boolean mouseWasDown = false;
    private boolean windowInitialized = false;

    public NotepadModForge(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();
        FMLClientSetupEvent.getBus(modBusGroup).addListener(this::onClientSetup);
        RegisterKeyMappingsEvent.getBus(modBusGroup).addListener(this::onRegisterKeys);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        NotelyModClient.init();

        TickEvent.ClientTickEvent.Post.BUS.addListener(this::onClientTick);
        CustomizeGuiOverlayEvent.Chat.BUS.addListener(this::onRenderHud);
        ScreenEvent.Render.Post.BUS.addListener(this::onRenderScreen);

        // Cancellable events use Predicate (return true = cancel)
        ScreenEvent.MouseButtonPressed.Pre.BUS.addListener(this::onScreenMousePress);
        ScreenEvent.MouseButtonReleased.Post.BUS.addListener(this::onScreenMouseRelease);
        ScreenEvent.MouseDragged.Pre.BUS.addListener(this::onScreenMouseDrag);
        ScreenEvent.MouseScrolled.Pre.BUS.addListener(this::onScreenMouseScroll);

        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(this::onPlayerLoggingIn);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(this::onPlayerLoggingOut);
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(NotelyModClient.createKeyMapping());
    }

    // ---- Tick: key + mouse input when no screen is open ----

    private void onClientTick(TickEvent.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (!windowInitialized && mc.getWindow() != null) {
            NotelyModClient.initWindow();
            windowInitialized = true;
        }

        NotelyModClient.onKeyTick();

        if (mc.screen != null) return;
        if (mc.getWindow() == null) return;

        long win = NotelyModClient.getGlfwWindow(mc);
        boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (down && !mouseWasDown) {
            NotelyModClient.onMousePress(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        } else if (down && PinnedNotesOverlay.isDragging()) {
            NotelyModClient.onMouseHeld(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        } else if (!down && mouseWasDown) {
            NotelyModClient.onMouseRelease();
        }

        mouseWasDown = down;
    }

    // ---- Render: in-game HUD (no screen open) ----

    private void onRenderHud(CustomizeGuiOverlayEvent.Chat event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        PinnedNotesOverlay.render(
                event.getGuiGraphics(),
                event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight()
        );
    }

    // ---- Render: over chat screen only ----

    private void onRenderScreen(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return;
        if (event.getScreen() instanceof NotelyScreen) return;
        PinnedNotesOverlay.render(
                event.getGuiGraphics(),
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight()
        );
    }

    // ---- Mouse input over chat screen (Predicate = return true to cancel) ----

    private boolean onScreenMousePress(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return false;
        if (event.getScreen() instanceof NotelyScreen) return false;
        if (event.getInfo().button() != 0) return false;
        boolean handled = PinnedNotesOverlay.handlePressScaled(event.getMouseX(), event.getMouseY());
        if (handled) mouseWasDown = true;
        return handled;
    }

    private void onScreenMouseRelease(ScreenEvent.MouseButtonReleased.Post event) {
        if (mouseWasDown) {
            NotelyModClient.onMouseRelease();
            mouseWasDown = false;
        }
    }

    private boolean onScreenMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return false;
        if (!PinnedNotesOverlay.isDragging()) return false;
        PinnedNotesOverlay.handleDragScaled(event.getMouseX(), event.getMouseY());
        return true;
    }

    private boolean onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return false;
        return PinnedNotesOverlay.handleScrollScaled(
                event.getMouseX(), event.getMouseY(), event.getDeltaY(), mc);
    }

    // ---- World join/leave ----

    private void onPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            NotelyModClient.onJoinServer(server.ip);
        } else if (mc.getSingleplayerServer() != null) {
            try {
                var field = mc.getSingleplayerServer().getClass().getSuperclass().getDeclaredField("storageSource");
                field.setAccessible(true);
                var storage = field.get(mc.getSingleplayerServer());
                NotelyModClient.onJoinWorld((String) storage.getClass().getMethod("getLevelId").invoke(storage));
            } catch (Exception e) {
                NotelyModClient.onJoinWorld(mc.getSingleplayerServer().getWorldData().getLevelName());
            }
        }
    }

    private void onPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NotelyModClient.onLeave();
    }
}
