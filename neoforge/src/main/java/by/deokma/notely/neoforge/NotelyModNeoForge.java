package by.deokma.notely.neoforge;

import by.deokma.notely.NotelyMod;
import by.deokma.notely.NotelyModClient;
import by.deokma.notely.gui.NotelyScreen;
import by.deokma.notely.gui.PinnedNotesOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.lwjgl.glfw.GLFW;

@Mod(value = NotelyMod.MOD_ID, dist = Dist.CLIENT)
public class NotelyModNeoForge {

    private boolean mouseWasDown = false;
    private boolean windowInitialized = false;

    public NotelyModNeoForge(IEventBus modBus) {
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeys);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        NotelyModClient.init();
        NotelyModClient.setScreenFactory(NotelyScreenNeoForge::new);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onRenderHud);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggingIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggingOut);
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(NotelyModClient.createKeyMapping());
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (!windowInitialized && mc.getWindow() != null) {
            NotelyModClient.initWindow();
            windowInitialized = true;
        }

        NotelyModClient.onKeyTick();

        if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return;
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

    private void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!NotelyModClient.isStickersAllowedOnScreen(mc)) return;
        if (mc.screen instanceof NotelyScreen) return;
        PinnedNotesOverlay.render(
            event.getGuiGraphics(),
            mc.getWindow().getGuiScaledWidth(),
            mc.getWindow().getGuiScaledHeight()
        );
    }

    private void onPlayerLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        ServerData server = mc.getCurrentServer();
        if (server != null) {
            NotelyModClient.onJoinServer(server.ip);
        } else if (mc.getSingleplayerServer() != null) {
            String folderName = resolveSingleplayerFolder(mc);
            NotelyModClient.onJoinWorld(folderName);
        }
    }

    private String resolveSingleplayerFolder(Minecraft mc) {
        try {
            var field = mc.getSingleplayerServer().getClass().getSuperclass().getDeclaredField("storageSource");
            field.setAccessible(true);
            var storage = field.get(mc.getSingleplayerServer());
            return (String) storage.getClass().getMethod("getLevelId").invoke(storage);
        } catch (Exception e) {
            return mc.getSingleplayerServer().getWorldData().getLevelName();
        }
    }

    private void onPlayerLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        NotelyModClient.onLeave();
    }
}
