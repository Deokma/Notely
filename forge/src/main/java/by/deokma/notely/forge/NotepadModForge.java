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
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.lwjgl.glfw.GLFW;

@Mod(NotelyMod.MOD_ID)
public class NotepadModForge {

    private boolean mouseWasDown = false;
    private boolean windowInitialized = false;

    public NotepadModForge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeys);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        NotelyModClient.init();
        MinecraftForge.EVENT_BUS.addListener(this::onClientTick);
        // Render stickers in-game (no screen) and over chat
        MinecraftForge.EVENT_BUS.addListener(this::onRenderHud);
        MinecraftForge.EVENT_BUS.addListener(this::onRenderScreen);
        // Mouse input over chat screen
        MinecraftForge.EVENT_BUS.addListener(this::onScreenMousePress);
        MinecraftForge.EVENT_BUS.addListener(this::onScreenMouseRelease);
        MinecraftForge.EVENT_BUS.addListener(this::onScreenMouseDrag);
        MinecraftForge.EVENT_BUS.addListener(this::onScreenMouseScroll);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggingIn);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLoggingOut);
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(NotelyModClient.createKeyMapping());
    }

    // ---- Tick: key + mouse input when no screen is open ----

    private void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        if (!windowInitialized && mc.getWindow() != null) {
            NotelyModClient.initWindow();
            windowInitialized = true;
        }

        NotelyModClient.onKeyTick();

        // Mouse handling only when no screen is open (chat handled via ScreenEvent)
        if (mc.screen != null) return;
        if (mc.getWindow() == null) return;

        long win = mc.getWindow().getWindow();
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
        if (mc.screen != null) return; // chat screen handled by onRenderScreen
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

    // ---- Mouse input over chat screen ----

    private void onScreenMousePress(ScreenEvent.MouseButtonPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return;
        if (event.getScreen() instanceof NotelyScreen) return;
        if (event.getButton() != 0) return;
        boolean handled = PinnedNotesOverlay.handlePressScaled(event.getMouseX(), event.getMouseY());
        if (handled) {
            event.setCanceled(true);
            mouseWasDown = true;
        }
    }

    private void onScreenMouseRelease(ScreenEvent.MouseButtonReleased.Post event) {
        if (event.getButton() != 0) return;
        if (mouseWasDown) {
            NotelyModClient.onMouseRelease();
            mouseWasDown = false;
        }
    }

    private void onScreenMouseDrag(ScreenEvent.MouseDragged.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return;
        if (!PinnedNotesOverlay.isDragging()) return;
        PinnedNotesOverlay.handleDragScaled(event.getMouseX(), event.getMouseY());
        event.setCanceled(true);
    }

    private void onScreenMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return;
        boolean handled = PinnedNotesOverlay.handleScrollScaled(
                event.getMouseX(), event.getMouseY(), event.getDeltaY(), mc);
        if (handled) event.setCanceled(true);
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
