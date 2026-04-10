package by.deokma.notely.neoforge;

import by.deokma.notely.NotepadMod;
import by.deokma.notely.NotepadModClient;
import by.deokma.notely.gui.NotepadScreen;
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

@Mod(value = NotepadMod.MOD_ID, dist = Dist.CLIENT)
public class NotepadModNeoForge {

    private boolean mouseWasDown = false;
    private boolean windowInitialized = false;

    public NotepadModNeoForge(IEventBus modBus) {
        modBus.addListener(this::onClientSetup);
        modBus.addListener(this::onRegisterKeys);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        NotepadModClient.init();
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
        NeoForge.EVENT_BUS.addListener(this::onRenderHud);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggingIn);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLoggingOut);
    }

    private void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(NotepadModClient.createKeyMapping());
    }

    private void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();

        if (!windowInitialized && mc.getWindow() != null) {
            NotepadModClient.initWindow();
            windowInitialized = true;
        }

        NotepadModClient.onKeyTick();

        if (mc.screen instanceof NotepadScreen) return;
        if (mc.getWindow() == null) return;

        long win = mc.getWindow().getWindow();
        boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (down && !mouseWasDown) {
            NotepadModClient.onMousePress(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        } else if (down && PinnedNotesOverlay.isDragging()) {
            NotepadModClient.onMouseHeld(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        } else if (!down && mouseWasDown) {
            NotepadModClient.onMouseRelease();
        }

        mouseWasDown = down;
    }

    private void onRenderHud(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof NotepadScreen) return;
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
            NotepadModClient.onJoinServer(server.ip);
        } else if (mc.getSingleplayerServer() != null) {
            String folderName = resolveSingleplayerFolder(mc);
            NotepadModClient.onJoinWorld(folderName);
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
        NotepadModClient.onLeave();
    }
}
